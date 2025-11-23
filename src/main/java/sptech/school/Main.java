package sptech.school;

import io.github.cdimascio.dotenv.Dotenv;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class Main {

    public static void main(String[] args) {

        Dotenv dotenv = Dotenv.load();
        String modoExecucao = dotenv.get("MODO_EXECUCAO", "LOCAL");

        if (modoExecucao.equalsIgnoreCase("AWS")) {
            System.out.println("Iniciando ETL: TRUSTED -> CLIENT (Geracao de Alertas) - MODO AWS");

            try {
                // 1. EXTRAÇÃO & TRATAMENTO: Lê do bucket TRUSTED e gera o CSV de alertas
                String csvAlerta = GeradorAlertas.gerarCsvAlertas();

                // 2. CARGA: Envia o CSV de alertas para o bucket CLIENT
                if (csvAlerta != null && !csvAlerta.isEmpty()) {
                    ConexaoAws.enviarCsvClient("alerta.csv", csvAlerta);
                } else {
                    System.out.println("Nenhum alerta gerado. Não foi necessário enviar arquivo para o S3 CLIENT.");
                }

                System.out.println("ETL TRUSTED -> CLIENT finalizada com sucesso.");

            } catch (Exception e) {
                System.err.println("❌ Falha na execução do ETL: " + e.getMessage());
                e.printStackTrace();
            }

        } else if (modoExecucao.equalsIgnoreCase("SIMULADO")) {
            System.out.println("Iniciando ETL: TRUSTED -> CLIENT (Geracao de Alertas) - MODO SIMULADO");

            try {
                // 1. EXTRAÇÃO & TRATAMENTO: Lê do arquivo local (trusted.csv) e gera o CSV de alertas
                String csvAlerta = GeradorAlertas.gerarCsvAlertas();

                if (csvAlerta != null && !csvAlerta.isEmpty()) {
                    System.out.println("\n--- CSV de Alertas Gerado Localmente (Conteúdo) ---");
                    System.out.println(csvAlerta);

                    // 2. CARGA: Salvar o arquivo localmente
                    String nomeArquivoSaida = "alerta.csv";
                    try (FileWriter writer = new FileWriter(nomeArquivoSaida)) {
                        writer.write(csvAlerta);
                        System.out.println("\n✅ O arquivo de alertas foi salvo localmente como: " + nomeArquivoSaida);
                    } catch (IOException e) {
                        System.err.println("❌ Erro ao salvar o arquivo de alertas localmente: " + e.getMessage());
                    }

                } else {
                    System.out.println("Nenhum alerta gerado.");
                }

                System.out.println("\nSimulação de ETL TRUSTED -> CLIENT finalizada com sucesso.");

            } catch (Exception e) {
                System.err.println("❌ Falha na simulação do ETL: " + e.getMessage());
                e.printStackTrace();
            }

        } else {
            System.err.println("MODO_EXECUCAO inválido. Use 'AWS' ou 'SIMULADO'.");
            System.err.println("Por favor, configure MODO_EXECUCAO no seu arquivo .env");
        }
    }
}