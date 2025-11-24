package sptech.school;

import io.github.cdimascio.dotenv.Dotenv;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AlertaMainProcessor {

    // Configurações do arquivo .env
    private static final Dotenv DOTENV = Dotenv.load();
    private static final Integer ID_EMPRESA = Integer.valueOf(DOTENV.get("ID_EMPRESA", "1"));
    private static final String NOME_ARQUIVO_DADOS = "trusted.csv";

    public static void main(String[] args) {

        String modoExecucao = DOTENV.get("MODO_EXECUCAO", "SIMULADO");
        List<Alerta> listaAlertas = new ArrayList<>();

        System.out.println("Iniciando Processador de Alertas em modo: " + modoExecucao.toUpperCase());

        try (Connection conn = ConexaoBd.getConnection()) {

            if (modoExecucao.equalsIgnoreCase("AWS")) {

                System.out.println("Modo AWS: Buscando Mainframes no BD para a Empresa " + ID_EMPRESA + "...");

                // Busca todos os MACs da empresa no BD
                List<String> macs = ConexaoBd.buscarMac(conn, ID_EMPRESA.toString());

                if (macs.isEmpty()) {
                    System.out.println("Nenhum mainframe encontrado no BD para a Empresa " + ID_EMPRESA);
                    return;
                }

                // Itera sobre cada MAC para ler o arquivo no S3
                for (String mac : macs) {
                    System.out.println("Lendo dados do S3 para o MAC: " + mac);
                    List<String[]> dadosAtuais = ConexaoAws.lerArquivoCsvDoTrusted(mac, ID_EMPRESA, NOME_ARQUIVO_DADOS);

                    // Processa os dados lidos
                    GeradorAlertas.processarDadosParaAlertas(conn, dadosAtuais, listaAlertas);
                }

            } else if (modoExecucao.equalsIgnoreCase("SIMULADO")) {

                // Leitura local do trusted.csv
                System.out.println("Modo SIMULADO: Lendo arquivo de dados localmente (" + NOME_ARQUIVO_DADOS + ")...");
                List<String[]> dadosMainframe = GeradorAlertas.lerArquivoCsvLocal(NOME_ARQUIVO_DADOS);

                // Processa todos os dados lidos
                GeradorAlertas.processarDadosParaAlertas(conn, dadosMainframe, listaAlertas);

            } else {
                System.err.println("❌ MODO_EXECUCAO inválido no .env. Use 'AWS' ou 'SIMULADO'.");
                return;
            }

            // Salvando csv de alertas
            if (!listaAlertas.isEmpty()) {
                String csvAlertas = GeradorAlertas.montarCsvAlertas(listaAlertas);
                String nomeArquivoAlertas = "alertas_empresa_" + ID_EMPRESA + ".csv";

                if (modoExecucao.equalsIgnoreCase("AWS")) {

                    // Salva como json no S3 client ConexaoAws.salvarJsonNoS3
                    ConexaoAws.salvarJsonNoS3(nomeArquivoAlertas, csvAlertas);

                } else if (modoExecucao.equalsIgnoreCase("SIMULADO")) {
                    // Salva na pasta raiz localmente
                    salvarCsvLocal(nomeArquivoAlertas, csvAlertas);
                }
            } else {
                System.out.println("Nenhum alerta gerado.");
            }

        } catch (SQLException e) {
            System.err.println("❌ Erro de conexão/SQL: " + e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("❌ Erro de I/O na leitura local: " + e.getMessage());
        }
    }

    // salvar o CSV localmente (SIMULADO)
    private static void salvarCsvLocal(String nomeArquivo, String csvContent) {
        try (FileWriter writer = new FileWriter(nomeArquivo)) {
            writer.write(csvContent);
            System.out.println("✅ CSV de Alertas salvo localmente: " + nomeArquivo);
        } catch (IOException e) {
            System.err.println("❌ Erro ao salvar CSV localmente: " + e.getMessage());
        }
    }
}