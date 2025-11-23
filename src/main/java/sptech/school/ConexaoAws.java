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

    // =========================================================================
    // MÉTODOS DE LEITURA (GET)
    // =========================================================================

    // Lê um CSV do bucket RAW e devolve como lista de linhas
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
                boolean primeiraLinha = true;
                while ((linha = reader.readLine()) != null) {
                    if (primeiraLinha) {
                        primeiraLinha = false; // Pula o cabeçalho
                        continue;
                    }
                    // Utiliza o delimitador ponto-e-vírgula (;)
                    linhas.add(linha.split(";"));
                }
            }

        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                System.out.printf("Arquivo %s não encontrado no bucket RAW %s.%n", nomeArquivo, bucketRaw);
                return null;
            }
            System.err.println("Erro ao ler arquivo do S3 RAW: " + e.awsErrorDetails().errorMessage());
        } catch (IOException e) {
            System.err.println("Erro de I/O ao processar CSV do S3 RAW: " + e.getMessage());
        }

        return linhas;
    }

    // Lê um CSV do bucket TRUSTED e devolve como lista de linhas
    // NOVO MÉTODO: Lê um CSV do bucket TRUSTED e devolve como lista de linhas
    public static List<String[]> lerArquivoCsvDoTrusted(String nomeArquivo) {
        List<String[]> linhas = new ArrayList<>();
        String bucketTrusted = pegarBucket("trusted");

        try {
            GetObjectRequest getReq = GetObjectRequest.builder()
                    .bucket(bucketTrusted)
                    .key(nomeArquivo)
                    .build();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(s3.getObject(getReq)))) {

                String linha;
                // Pular o cabeçalho (a primeira linha)
                reader.readLine();

                while ((linha = reader.readLine()) != null) {
                    // Utiliza o delimitador ponto-e-vírgula (;)
                    linhas.add(linha.split(";"));
                }
            }
        } catch (S3Exception e) {
            System.err.println("Erro S3 ao ler arquivo: " + nomeArquivo + " do bucket " + bucketTrusted);
            System.err.println(e.awsErrorDetails().errorMessage());
            return null; // Retorna null em caso de erro S3
        } catch (IOException e) {
            System.err.println("Erro de IO ao ler arquivo CSV: " + e.getMessage());
            return null;
        }

        System.out.println("✅ Arquivo " + nomeArquivo + " lido do bucket TRUSTED.");
        return linhas;
    }

    // =========================================================================
    // MÉTODOS DE ESCRITA (PUT)
    // =========================================================================

    // Envia um CSV para o bucket TRUSTED
    public static void enviarCsvTrusted(String nomeArquivo, String conteudo) {
        String bucketTrusted = pegarBucket("trusted");

        try {
            PutObjectRequest putReq = PutObjectRequest.builder()
                    .bucket(bucketTrusted)
                    .key(nomeArquivo)
                    .contentType("text/csv")
                    .build();

            s3.putObject(putReq, RequestBody.fromString(conteudo));
            System.out.printf("✅ Arquivo '%s' enviado com sucesso para o bucket TRUSTED: %s%n", nomeArquivo, bucketTrusted);

        } catch (S3Exception e) {
            System.err.println("❌ Erro ao enviar arquivo para o S3 TRUSTED: " + e.awsErrorDetails().errorMessage());
        }
    }

    // Envia um CSV para o bucket CLIENT
    public static void enviarCsvClient(String nomeArquivo, String conteudo) {
        String bucketClient = pegarBucket("client");

        try {
            PutObjectRequest putReq = PutObjectRequest.builder()
                    .bucket(bucketClient)
                    .key(nomeArquivo)
                    .contentType("text/csv")
                    .build();

            s3.putObject(putReq, RequestBody.fromString(conteudo));
            System.out.printf("✅ Arquivo '%s' enviado com sucesso para o bucket CLIENT: %s%n", nomeArquivo, bucketClient);

        } catch (S3Exception e) {
            System.err.println("❌ Erro ao enviar arquivo para o S3 CLIENT: " + e.awsErrorDetails().errorMessage());
        }
    }

    // =========================================================================
    // MÉTODOS DE SUPORTE
    // =========================================================================

    // Retorna o nome do bucket pelo tipo
    public static String pegarBucket(String tipo) {
        if (tipo.equalsIgnoreCase("raw") && !BUCKETS_RAW.isEmpty()) {
            return BUCKETS_RAW.get(0);
        } else if (tipo.equalsIgnoreCase("trusted") && !BUCKETS_TRUSTED.isEmpty()) {
            return BUCKETS_TRUSTED.get(0);
        } else if (tipo.equalsIgnoreCase("client") && !BUCKETS_CLIENT.isEmpty()) {
            return BUCKETS_CLIENT.get(0);
        }
        throw new RuntimeException("Bucket do tipo '" + tipo + "' não encontrado!");
    }

    // Lista todos os buckets da conta
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

    // Testa conexão e categoriza buckets
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

            if (BUCKETS_RAW.isEmpty() || BUCKETS_TRUSTED.isEmpty() || BUCKETS_CLIENT.isEmpty()) {
                System.err.println("\nERRO: Certifique-se de ter pelo menos um bucket para cada tipo (raw, trusted, client).");
            } else {
                System.out.println("\nConexão e categorização de buckets concluídas com sucesso.");
            }

        } catch (Exception e) {
            System.err.println("Erro geral no main de ConexaoAws: " + e.getMessage());
            e.printStackTrace();
        }
    }
}