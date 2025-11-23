package sptech.school;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.*;
import java.util.*;

public class ConexaoAws {

    private static final Region REGION = Region.US_EAST_1;
    private static final S3Client s3 = S3Client.builder()
            .region(REGION)
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();

    private static final List<String> BUCKETS_RAW = new ArrayList<>();
    private static final List<String> BUCKETS_TRUSTED = new ArrayList<>();
    private static final List<String> BUCKETS_CLIENT = new ArrayList<>();

    //Lê um CSV do bucket RAW e devolve como lista de linhas
    public static List<String[]> lerArquivoCsvDoRaw(String nomeArquivo) {
        List<String[]> linhas = new ArrayList<>();
        String bucketRaw = pegarBucket("raw");

        try {
            GetObjectRequest getReq = GetObjectRequest.builder()
                    .bucket(bucketRaw)
                    .key(nomeArquivo)
                    .build();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(s3.getObject(getReq)))) {

                String linha;
                while ((linha = reader.readLine()) != null) {
                    linhas.add(linha.split(";"));
                }
            }

            System.out.println("Arquivo lido do RAW: " + nomeArquivo);

        } catch (Exception e) {
            System.err.println("Erro ao ler arquivo do RAW: " + e.getMessage());
        }

        return linhas;
    }

// Lê um CSV do bucket TRUSTED e devolve como lista de linhas
    public static List<String[]> lerArquivoCsvDoTrusted(String nomeArquivo) {
        List<String[]> linhas = new ArrayList<>();
        String bucketTrusted = pegarBucket("trusted"); // Usa a constante TRUSTED

        try {
            GetObjectRequest getReq = GetObjectRequest.builder()
                    .bucket(bucketTrusted)
                    .key(nomeArquivo)
                    .build();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(s3.getObject(getReq)))) {
                String linha;
                while ((linha = reader.readLine()) != null) {
                    // Divide por ";"
                    linhas.add(linha.split(";"));
                }
            }
        } catch (S3Exception e) {
            System.err.println("Erro ao ler arquivo do S3 (" + nomeArquivo + "): " + e.awsErrorDetails().errorMessage());
        } catch (IOException e) {
            System.err.println("Erro de I/O ao processar arquivo: " + e.getMessage());
        }
        return linhas;
    }

    //Envia CSV tratado para o bucket TRUSTED
    public static void enviarCsvTrusted(String nomeArquivo, String conteudoCsv) {
        String bucketTrusted = pegarBucket("trusted");

        try {
            PutObjectRequest putReq = PutObjectRequest.builder()
                    .bucket(bucketTrusted)
                    .key(nomeArquivo)
                    .build();

            s3.putObject(putReq, RequestBody.fromString(conteudoCsv));
            System.out.println("✅ CSV tratado enviado para TRUSTED: " + nomeArquivo);

        } catch (Exception e) {
            System.err.println("Erro ao enviar CSV para TRUSTED: " + e.getMessage());
        }
    }

    // Em ConexaoAws.java
// Envia o CSV tratado para o bucket CLIENT
    public static void enviarCsvClient(String nomeArquivo, String conteudoCsv) {
        String bucketClient = pegarBucket("client"); // Usa a constante CLIENT

        try {
            PutObjectRequest putReq = PutObjectRequest.builder()
                    .bucket(bucketClient)
                    .key(nomeArquivo)
                    .contentType("text/csv")
                    .build();

            s3.putObject(putReq, RequestBody.fromString(conteudoCsv));
            System.out.println("Arquivo CSV de alertas enviado com sucesso para o bucket CLIENT: " + bucketClient + "/" + nomeArquivo);
        } catch (S3Exception e) {
            System.err.println("Erro ao enviar arquivo para o S3: " + e.awsErrorDetails().errorMessage());
        }
    }

    //Acha o bucket certo pelo nome (raw/trusted/client)
    private static String pegarBucket(String tipo) {
        ListBucketsResponse response = s3.listBuckets();
        for (Bucket b : response.buckets()) {
            if (b.name().toLowerCase().contains(tipo)) {
                return b.name();
            }
        }
        throw new RuntimeException("Bucket do tipo '" + tipo + "' não encontrado!");
    }

    //Lista todos os buckets da conta
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

    //Testa conexão e categoriza buckets
    public static void main(String[] args) {
        try {
            System.out.println("Conectando à AWS S3...");
            List<String> buckets = pegarBucketsS3();

            for (String b : buckets) {
                String nome = b.toLowerCase();
                if (nome.contains("raw")) BUCKETS_RAW.add(b);
                else if (nome.contains("trusted")) BUCKETS_TRUSTED.add(b);
                else if (nome.contains("client")) BUCKETS_CLIENT.add(b);
            }

            System.out.println("\nBuckets RAW: " + BUCKETS_RAW);
            System.out.println("Buckets TRUSTED: " + BUCKETS_TRUSTED);
            System.out.println("Buckets CLIENT: " + BUCKETS_CLIENT);

        } catch (Exception e) {
            System.err.println("Erro ao conectar ou listar buckets: " + e.getMessage());
        }
    }
}
