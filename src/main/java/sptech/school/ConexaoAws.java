package sptech.school;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class ConexaoAws {

    private static final Region REGION = Region.US_EAST_1;

    private static final S3Client s3 = S3Client.builder()
            .region(REGION)
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();

    // LER CSV DO TRUSTED

    public static List<String[]> lerArquivoCsvDoTrusted(String mac, Integer empresa ,String nomeArquivo ) {

        LocalDate hoje = LocalDate.now();

        int dia  = hoje.getDayOfMonth();
        int mes  = hoje.getMonthValue();
        int ano  = hoje.getYear();


        String diretorio = empresa + "/" + mac + "/"+dia + mes + ano + "/" + nomeArquivo;


        List<String[]> linhas = new ArrayList<>();

        String bucket = pegarBucket("trusted");

        try {
            GetObjectRequest getReq = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(diretorio)
                    .build();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(s3.getObject(getReq)))) {

                String linha;
                while ((linha = reader.readLine()) != null) {
                    linhas.add(linha.split(";"));
                }


            }

            System.out.println("Arquivo lido do TRUSTED: " + diretorio);

        } catch (Exception e) {
            System.err.println("Erro ao ler arquivo do  empresa + \"/\" + dia + mes + ano + \"/\": " + e.getMessage());
        }

        return linhas;
    }

    public static List<String[]> lerCsvLocal(String nomeArquivo) {

            List<String[]> linhas = new ArrayList<>();

            try (BufferedReader reader = new BufferedReader(
                    new FileReader(nomeArquivo))) {

                String linha;

                while ((linha = reader.readLine()) != null) {
                    linhas.add(linha.split(";")); // separa do mesmo jeito
                }

                System.out.println("Arquivo lido LOCALMENTE: " + nomeArquivo);

            } catch (Exception e) {

                System.err.println("Erro ao ler arquivo local: " + e.getMessage());
            }

            return linhas;
   }

    public static List<String> listarDiretorios() {
        List<String> diretorios = new ArrayList<>();
        String bucket = pegarBucket("trusted"); // seu bucket "trusted"

        try {
            ListObjectsV2Request listReq = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix("1/")
                    .delimiter("/")
                    .build();

            ListObjectsV2Response res = s3.listObjectsV2(listReq);


            res.commonPrefixes().forEach(cp -> diretorios.add(cp.prefix()));

            System.out.println("Diretórios encontrados dentro de 1/: " + diretorios);

        } catch (Exception e) {
            System.err.println("Erro ao listar diretórios: " + e.getMessage());
        }

        return diretorios;
    }

    public static List<String> buscarMac(Connection conn, String empresa) throws SQLException {
        String sql = """
            SELECT m.macAdress 
            FROM empresa e
            JOIN setor s ON s.fkempresa = e.id
            JOIN mainframe m ON m.fksetor = s.id
            WHERE e.id = ?;
        """;

        List<String> lista = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, empresa);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                lista.add(rs.getString("macAdress"));
            }
        }

        return lista;
    }

    // ENVIAR JSON PARA TRUSTED

    public static void salvarJsonNoS3(String nomeArquivo, String json) {
        String bucketClient = pegarBucket("client"); // define bucket client

        try {
            PutObjectRequest putReq = PutObjectRequest.builder()
                    .bucket(bucketClient)
                    .key(nomeArquivo)
                    .contentType("application/json")
                    .build();

            s3.putObject(putReq, RequestBody.fromString(json));
            System.out.println("JSON enviado ao CLIENT S3: " + nomeArquivo);

        } catch (Exception e) {
            System.err.println("Erro ao enviar JSON para CLIENT: " + e.getMessage());
        }
    }

    // LISTAR BUCKETS
    public static List<String> pegarBucketsS3() {

        List<String> buckets = new ArrayList<>();

        try {
            ListBucketsResponse response = s3.listBuckets();
            for (Bucket b : response.buckets()) {
                buckets.add(b.name());
            }

        } catch (S3Exception e) {
            System.err.println("Erro ao listar buckets: " + e.awsErrorDetails().errorMessage());
        }

        return buckets;
    }
    // ACHAR BUCKET PELO NOME
    public static String pegarBucket(String tipo) {

        ListBucketsResponse response = s3.listBuckets();
        for (Bucket b : response.buckets()) {
            if (b.name().toLowerCase().contains(tipo.toLowerCase())) {
                return b.name();
            }
        }

        throw new RuntimeException("Bucket do tipo '" + tipo + "' não encontrado!");
    }
}
