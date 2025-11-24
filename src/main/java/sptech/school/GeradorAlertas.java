package sptech.school;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GeradorAlertas {

    private static final Double LIMITE_MAXIMO_ALERTA = 100.0;

    private static final int INDICE_MAC_ADRESS = 0;
    private static final int INDICE_DT_HORA = 1;
    private static final int INDICE_ID_MAINFRAME = 2;
    private static final int INDICE_CPU = 3;
    private static final int INDICE_RAM = 4;
    private static final int INDICE_DISCO = 5;

    // Processa os dados de um mainframe especifico
    public static void processarDadosParaAlertas(Connection conn, List<String[]> dadosMainframe, List<Alerta> listaAlertas) {
        if (dadosMainframe == null || dadosMainframe.isEmpty()) {
            return;
        }

        // Agrupa linhas por mainframe
        Map<String, List<String[]>> dadosPorMainframe = new HashMap<>();
        for (String[] linha : dadosMainframe) {
            if (linha.length > INDICE_MAC_ADRESS) {
                String macAdress = linha[INDICE_MAC_ADRESS];
                dadosPorMainframe.computeIfAbsent(macAdress, k -> new ArrayList<>()).add(linha);
            }
        }

        for (Map.Entry<String, List<String[]>> entry : dadosPorMainframe.entrySet()) {
            String macAdress = entry.getKey();

            try {
                // Busca os limites no BD
                Map<String, Double[]> limitesMainframe = ConexaoBd.buscarLimitesMetricas(conn, macAdress);

                if (limitesMainframe.isEmpty()) {
                    System.out.println("⚠️ Limites não encontrados no BD para o MAC: " + macAdress);
                    continue;
                }

                // Processa linha por linha do mainframe
                for (String[] linha : entry.getValue()) {
                    processarLinhaMainframe(conn, listaAlertas, linha, limitesMainframe);
                }
            } catch (SQLException e) {
                System.err.println("❌ Erro de SQL ao buscar limites para " + macAdress + ": " + e.getMessage());
            }
        }
    }

    // Lógica que verifica a linha, define a gravidade, insere no BD e adiciona à lista.
    private static void processarLinhaMainframe(Connection conn, List<Alerta> listaAlertas, String[] linha,
                                                Map<String, Double[]> limitesMainframe) {

        if (linha.length < INDICE_DISCO + 1) return;

        String dtHora = linha[INDICE_DT_HORA];
        String macAdress = linha[INDICE_MAC_ADRESS];
        String identificacaoMainframe = linha[INDICE_ID_MAINFRAME];

        Map<String, Integer> mapaIndices = new HashMap<>();
        mapaIndices.put("Processador", INDICE_CPU);
        mapaIndices.put("Memória RAM", INDICE_RAM);
        mapaIndices.put("Disco Rígido", INDICE_DISCO);

        for (Map.Entry<String, Integer> entry : mapaIndices.entrySet()) {
            String nomeComponenteBd = entry.getKey();
            int indiceCsv = entry.getValue();

            if (limitesMainframe.containsKey(nomeComponenteBd)) {

                Double limiteMin = limitesMainframe.get(nomeComponenteBd)[0];
                Double limiteMax = limitesMainframe.get(nomeComponenteBd)[1];

                try {
                    Double valorColetado = Double.parseDouble(linha[indiceCsv].replace(",", "."));
                    String gravidade = definirGravidade(valorColetado, limiteMin, limiteMax);

                    if (gravidade != null && !gravidade.equals("Normal")) {
                        ConexaoBd.inserirAlerta(conn, dtHora, nomeComponenteBd, valorColetado, macAdress, identificacaoMainframe, gravidade);

                        listaAlertas.add(new Alerta(dtHora, valorColetado, nomeComponenteBd, gravidade, macAdress, identificacaoMainframe));
                    }

                } catch (NumberFormatException e) {
                    System.err.println("Erro ao converter valor numérico na linha: " + String.join(";", linha));
                }
            }
        }
    }

    // Lógica de gravidade (mantida inalterada)
    private static String definirGravidade(Double valor, Double min, Double max) {
        Double limiteMuitoUrgenteMax = max + ((LIMITE_MAXIMO_ALERTA - max) / 2);
        Double limiteMuitoUrgenteMin = min / 2;

        if (valor >= LIMITE_MAXIMO_ALERTA || valor <= 0.00) return "Emergencia";
        else if (valor >= limiteMuitoUrgenteMax || valor <= limiteMuitoUrgenteMin) return "Muito Urgente";
        else if (valor > max || valor < min) return "Urgente";
        return "Normal";
    }

    // Monta o CSV de alertas (tornada pública para ser chamada pelo Main)
    public static String montarCsvAlertas(List<Alerta> listaAlertas) {
        StringBuilder sb = new StringBuilder();
        sb.append("dt_hora;valor_coletado_%;componente;gravidade;macAdress;identificacao_mainframe\n");
        for (Alerta alerta : listaAlertas) {
            sb.append(alerta.toString()).append("\n");
        }
        return sb.toString();
    }

    // Lê o arquivo local (mantida pública para o modo SIMULADO)
    public static List<String[]> lerArquivoCsvLocal(String nomeArquivo) throws IOException {
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