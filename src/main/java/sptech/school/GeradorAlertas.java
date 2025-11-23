package sptech.school;

import io.github.cdimascio.dotenv.Dotenv;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class GeradorAlertas {

    private static final Double LIMITE_MAXIMO_ALERTA = 100.0;

    // Constantes de índices do CSV
    private static final int INDICE_MAC_ADRESS = 0;
    private static final int INDICE_DT_HORA = 1;
    private static final int INDICE_ID_MAINFRAME = 2;
    private static final int INDICE_CPU = 3;
    private static final int INDICE_RAM = 4;
    private static final int INDICE_DISCO = 5;

    // =========================================================================
    // 1. FUNÇÃO PRINCIPAL
    // =========================================================================

    public static String gerarCsvAlertas() {
        Dotenv dotenv = Dotenv.load();
        String modoExecucao = dotenv.get("MODO_EXECUCAO", "LOCAL");
        List<String[]> dadosMainframe = null;
        List<Alerta> listaAlertas = new ArrayList<>();

        // 1. EXTRAÇÃO
        if (modoExecucao.equalsIgnoreCase("AWS")) {
            System.out.println("Lendo arquivo de dados do bucket TRUSTED...");
            dadosMainframe = ConexaoAws.lerArquivoCsvDoTrusted("trusted.csv");
        } else if (modoExecucao.equalsIgnoreCase("SIMULADO")) {
            try {
                System.out.println("Lendo arquivo de dados localmente (trusted.csv)...");
                dadosMainframe = lerArquivoCsvLocal("trusted.csv");
            } catch (IOException e) {
                System.err.println("❌ Erro ao ler arquivo local trusted.csv: " + e.getMessage());
                return "";
            }
        } else {
            System.err.println("❌ MODO_EXECUCAO inválido. Use 'AWS' ou 'SIMULADO'.");
            return "";
        }

        if (dadosMainframe == null || dadosMainframe.isEmpty()) {
            System.out.println("Nenhum dado encontrado ou lido.");
            return "";
        }

        // 2. CONEXÃO DB E PROCESSAMENTO
        try (Connection conn = ConexaoBd.getConnection()) {

            // Agrupa linhas por mainframe para otimizar buscas no banco
            Map<String, List<String[]>> dadosPorMainframe = new HashMap<>();
            for (String[] linha : dadosMainframe) {
                // Validação básica de tamanho da linha
                if (linha.length > INDICE_MAC_ADRESS) {
                    String macAdress = linha[INDICE_MAC_ADRESS];
                    dadosPorMainframe.computeIfAbsent(macAdress, k -> new ArrayList<>()).add(linha);
                }
            }

            for (Map.Entry<String, List<String[]>> entry : dadosPorMainframe.entrySet()) {
                String macAdress = entry.getKey();
                List<String[]> linhas = entry.getValue();

                // Busca limites UMA VEZ por mainframe (retorna chaves como: "Processador", "Memória RAM")
                Map<String, Double[]> limitesMainframe = ConexaoBd.buscarLimitesMetricas(conn, macAdress);

                if (limitesMainframe.isEmpty()) {
                    System.out.println("⚠️ Limites não encontrados no DB para o MAC: " + macAdress);
                    continue; // Pula se não achou mainframe no banco
                }

                for (String[] linha : linhas) {
                    processarLinhaMainframe(conn, listaAlertas, linha, limitesMainframe);
                }
            }

        } catch (SQLException e) {
            System.err.println("❌ Erro de SQL: " + e.getMessage());
            e.printStackTrace();
        }

        // 3. CARGA
        return montarCsvAlertas(listaAlertas);
    }

    // =========================================================================
    // 2. MÉTODOS AUXILIARES DE TRATAMENTO
    // =========================================================================

    private static void processarLinhaMainframe(Connection conn, List<Alerta> listaAlertas, String[] linha,
                                                Map<String, Double[]> limitesMainframe) {

        if (linha.length < INDICE_DISCO + 1) return;

        String dtHora = linha[INDICE_DT_HORA];
        String macAdress = linha[INDICE_MAC_ADRESS];
        String identificacaoMainframe = linha[INDICE_ID_MAINFRAME];

        // Mapeamento: Chave do CSV -> Objeto contendo {Nome no Banco, Índice no CSV}
        // Isso resolve o problema de "CPU" (CSV) vs "Processador" (Banco)
        Map<String, Integer> mapaIndices = new HashMap<>();
        mapaIndices.put("Processador", INDICE_CPU);
        mapaIndices.put("Memória RAM", INDICE_RAM);
        mapaIndices.put("Disco Rígido", INDICE_DISCO);

        // Itera sobre os componentes esperados (Processador, RAM, Disco)
        for (Map.Entry<String, Integer> entry : mapaIndices.entrySet()) {
            String nomeComponenteBd = entry.getKey(); // Ex: "Processador"
            int indiceCsv = entry.getValue();         // Ex: 3

            // Verifica se o banco retornou limites para esse componente ("Processador")
            if (limitesMainframe.containsKey(nomeComponenteBd)) {

                Double limiteMin = limitesMainframe.get(nomeComponenteBd)[0];
                Double limiteMax = limitesMainframe.get(nomeComponenteBd)[1];

                try {
                    Double valorColetado = Double.parseDouble(linha[indiceCsv].replace(",", "."));
                    String gravidade = definirGravidade(valorColetado, limiteMin, limiteMax);

                    // Se gravidade for crítica, insere no banco e adiciona na lista
                    if (gravidade != null && !gravidade.equals("Normal")) {
                        // DB e Jira (Envia o nome correto: "Processador")
                        ConexaoBd.inserirAlerta(conn, dtHora, nomeComponenteBd, valorColetado, macAdress, identificacaoMainframe, gravidade);

                        // CSV Client
                        listaAlertas.add(new Alerta(dtHora, valorColetado, nomeComponenteBd, gravidade, macAdress, identificacaoMainframe));
                    }

                } catch (NumberFormatException e) {
                    System.err.println("Erro ao converter valor numérico na linha: " + String.join(";", linha));
                }
            }
        }
    }

    // Lógica separada para ficar igual ao seu Trigger SQL
    private static String definirGravidade(Double valor, Double min, Double max) {
        // Cálculo dos pontos médios (Range Crítico)
        // Ex: Se Max é 90 e Limite é 100. Ponto médio é 95.
        Double limiteMuitoUrgenteMax = max + ((LIMITE_MAXIMO_ALERTA - max) / 2);

        // Ex: Se Min é 10 e Limite é 0. Ponto médio é 5.
        Double limiteMuitoUrgenteMin = min / 2;

        // 1. EMERGÊNCIA (100% ou 0%)
        if (valor >= LIMITE_MAXIMO_ALERTA || valor <= 0.00) {
            return "Emergencia"; // Sem acento para facilitar Enum/Map no Java
        }

        // 2. MUITO URGENTE (Entre o ponto médio e o extremo)
        // Ex: Entre 95 e 100 OU entre 0 e 5
        else if (valor >= limiteMuitoUrgenteMax || valor <= limiteMuitoUrgenteMin) {
            return "Muito Urgente";
        }

        // 3. URGENTE (Passou do limite configurado, mas não chegou no ponto médio)
        // Ex: Entre 90 e 95 OU entre 5 e 10
        else if (valor > max || valor < min) {
            return "Urgente";
        }

        // 4. NORMAL (Dentro da faixa segura)
        return "Normal";
    }

    // =========================================================================
    // 3. MÉTODOS AUXILIARES DE CARGA (CSV)
    // =========================================================================

    private static String montarCsvAlertas(List<Alerta> listaAlertas) {
        StringBuilder sb = new StringBuilder();
        sb.append("dt_hora;valor_coletado_%;componente;gravidade;macAdress;identificacao_mainframe\n");
        for (Alerta alerta : listaAlertas) {
            sb.append(alerta.toString()).append("\n");
        }
        return sb.toString();
    }

    private static List<String[]> lerArquivoCsvLocal(String nomeArquivo) throws IOException {
        List<String[]> linhas = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(nomeArquivo))) {
            String linha;
            boolean primeiraLinha = true;
            while ((linha = reader.readLine()) != null) {
                if (primeiraLinha) {
                    primeiraLinha = false;
                    continue;
                }
                linhas.add(linha.split(";"));
            }
        }
        return linhas;
    }
}