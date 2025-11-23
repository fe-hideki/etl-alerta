package sptech.school;

import io.github.cdimascio.dotenv.Dotenv;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GeradorAlertas {

    // Constante para o valor máximo (100% de uso)
    private static final Double LIMITE_MAXIMO_ALERTA = 100.0;

    // =========================================================================
    // 1. FUNÇÃO PRINCIPAL
    // =========================================================================

    public static String gerarCsvAlertas() {
        Dotenv dotenv = Dotenv.load();
        String modoExecucao = dotenv.get("MODO_EXECUCAO", "LOCAL");
        List<String[]> dadosMainframe = null;
        List<Alerta> listaAlertas = new ArrayList<>();

        if (!modoExecucao.equalsIgnoreCase("AWS")) {
            System.err.println("Modo LOCAL de execução não suportado para este fluxo. Configure MODO_EXECUCAO=AWS.");
            return "";
        }

        System.out.println("Lendo arquivo de dados do bucket TRUSTED...");
        // Extração: Lê o CSV do S3 TRUSTED
        dadosMainframe = ConexaoAws.lerArquivoCsvDoTrusted("trusted.csv");

        if (dadosMainframe == null || dadosMainframe.isEmpty()) {
            System.out.println("Nenhum dado encontrado no bucket TRUSTED.");
            return "";
        }

        // Conexão com o Banco de Dados para buscar limites e inserir alertas
        try (Connection conn = ConexaoBd.getConnection()) {

            for (String[] linha : dadosMainframe) {
                // Assumindo a ordem das colunas no trusted.csv:
                String macAdress = linha[0];
                String dtHora = linha[1];
                String identificacaoMainframe = linha[2];

                // Busca os limites min/max do DB para o mainframe
                Map<String, Double[]> limites = ConexaoBd.buscarLimitesMetricas(conn, macAdress);

                if (limites.isEmpty()) {
                    System.out.println("Aviso: Limites de métricas não encontrados no DB para MAC: " + macAdress);
                    continue;
                }

                // Indices no CSV TRUSTED: [3]=CPU, [4]=RAM, [5]=DISCO

                // Processa Processador
                processarComponente(conn, listaAlertas, dtHora, macAdress, identificacaoMainframe,
                        "Processador", 3, linha, limites);

                // Processa Memória RAM
                processarComponente(conn, listaAlertas, dtHora, macAdress, identificacaoMainframe,
                        "Memória RAM", 4, linha, limites);

                // Processa Disco Rígido
                processarComponente(conn, listaAlertas, dtHora, macAdress, identificacaoMainframe,
                        "Disco Rígido", 5, linha, limites);
            }

        } catch (SQLException e) {
            System.err.println("Erro ao conectar ou buscar/inserir métricas no banco de dados: " + e.getMessage());
            e.printStackTrace();
            return "";
        } catch (Exception e) {
            System.err.println("Erro durante o processamento de alertas: " + e.getMessage());
            e.printStackTrace();
            return "";
        }

        // Carga: Monta o CSV dos alertas gerados para o bucket CLIENT
        return montarCsvAlertas(listaAlertas);
    }

    // =========================================================================
    // 2. MÉTODOS AUXILIARES DE TRANSFORMAÇÃO
    // =========================================================================

    // Orquestra a busca do limite, conversão e chamada da verificação de alerta
    private static void processarComponente(Connection conn, List<Alerta> listaAlertas, String dtHora, String macAdress,
                                            String identificacaoMainframe, String nomeComponente,
                                            int indiceLinha, String[] linha, Map<String, Double[]> limites) {

        Double[] limite = limites.get(nomeComponente);

        if (limite != null) {
            // Conversão de String (CSV) para Double, tratando possível vírgula
            Double valorColetado = Double.parseDouble(linha[indiceLinha].replace(",", "."));

            // Verifica, insere no DB/Jira E adiciona à lista para o CSV
            verificarAlerta(conn, listaAlertas, dtHora, macAdress, identificacaoMainframe,
                    nomeComponente, valorColetado, limite[0], limite[1]);
        }
    }

    // Aplica a lógica de gravidade (replica o TRIGGER SQL) e dispara a inserção/Jira
    private static void verificarAlerta(Connection conn, List<Alerta> listaAlertas, String dtHora, String macAdress, String identificacaoMainframe,
                                        String componente, Double valorColetado, Double limiteMin, Double limiteMax) {

        String gravidade = null;

        // Limiares de Alerta (Baseado na lógica de 0% e 100%)
        Double limiteMuitoUrgenteMax = limiteMax + ((LIMITE_MAXIMO_ALERTA - limiteMax) / 2); // Ponto médio entre Max e 100
        Double limiteMuitoUrgenteMin = limiteMin / 2; // Ponto médio entre Min e 0

        // 1. EMERGÊNCIA (fkGravidade = 1) - Extremos 100% ou 0%
        if (valorColetado >= LIMITE_MAXIMO_ALERTA || valorColetado <= 0.00) {
            gravidade = "Emergência";

            // 2. MUITO URGENTE (fkGravidade = 2) - Entre o limiteMuitoUrgente e o extremo
        } else if ((valorColetado > limiteMuitoUrgenteMax && valorColetado < LIMITE_MAXIMO_ALERTA)
                || (valorColetado > 0.00 && valorColetado <= limiteMuitoUrgenteMin)) {
            gravidade = "Muito Urgente";

            // 3. URGENTE (fkGravidade = 3) - Entre o VMAX/VMIN e o limiteMuitoUrgente
        } else if ((valorColetado > limiteMax && valorColetado <= limiteMuitoUrgenteMax) // Acima do Max
                || (valorColetado >= limiteMuitoUrgenteMin && valorColetado < limiteMin)) { // Abaixo do Min
            gravidade = "Urgente";

            // 4. NORMAL (fkGravidade = 4) - Dentro do range VMIN e VMAX
        } else if (valorColetado > limiteMin && valorColetado < limiteMax) {
            gravidade = "Normal";
        }

        // Se a gravidade for crítica (diferente de "Normal" ou nula)
        if (gravidade != null && !gravidade.equals("Normal")) {

            // 1. INSERE NO DB E ABRE CHAMADO NO JIRA
            ConexaoBd.inserirAlerta(conn, dtHora, componente, valorColetado, macAdress, identificacaoMainframe, gravidade);

            // 2. ADICIONA À LISTA PARA O CSV (Bucket CLIENT)
            listaAlertas.add(new Alerta(dtHora, valorColetado, componente, gravidade, macAdress, identificacaoMainframe));
        }
    }

    // =========================================================================
    // 3. MÉTODO AUXILIAR DE CARGA (CSV)
    // =========================================================================

    // Monta o conteúdo do CSV a ser enviado
    private static String montarCsvAlertas(List<Alerta> listaAlertas) {
        StringBuilder sb = new StringBuilder();
        // Cabeçalho do CSV
        sb.append("dt_hora;valor_coletado_%;componente;gravidade;macAdress;identificacao_mainframe\n");

        for (Alerta alerta : listaAlertas) {
            // Utiliza o toString() da classe Alerta para formatar a linha do CSV
            sb.append(alerta.toString()).append("\n");
        }

        return sb.toString();
    }
}