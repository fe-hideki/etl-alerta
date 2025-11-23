package sptech.school;

import io.github.cdimascio.dotenv.Dotenv;

import java.io.*;
import java.util.*;

public class Main {

    public static void main(String[] args) {

        Dotenv dotenv = Dotenv.load();
        String modoExecucao = dotenv.get("MODO_EXECUCAO", "LOCAL");

        if (modoExecucao.equalsIgnoreCase("AWS")) {
            System.out.println("Iniciando ETL: TRUSTED -> CLIENT (Geracao de Alertas)");

            try {
                // 1. EXTRAÇÃO & TRATAMENTO: Lê do bucket TRUSTED e gera o CSV de alertas
                String csvAlerta = GeradorAlertas.gerarCsvAlertas();

                // 2. CARGA: Envia o CSV de alertas para o bucket CLIENT
                ConexaoAws.enviarCsvClient("alerta.csv", csvAlerta);

                System.out.println("ETL TRUSTED -> CLIENT finalizada com sucesso.");

            } catch (Exception e) {
                System.err.println("Falha na execução do ETL: " + e.getMessage());
                e.printStackTrace();
            }

        } else {
            System.err.println("Modo LOCAL de execução não suportado para este fluxo.");
            System.err.println("Por favor, configure MODO_EXECUCAO=AWS no seu arquivo .env");
        }
    }
}