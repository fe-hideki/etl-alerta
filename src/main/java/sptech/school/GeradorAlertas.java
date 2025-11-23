package sptech.school;

import io.github.cdimascio.dotenv.Dotenv;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GeradorAlertas {

    // --- Limites de Alerta (Exemplo, idealmente viriam do DB) ---
    private static final Double LIMITE_CPU_URGENTE = 80.0;
    private static final Double LIMITE_RAM_URGENTE = 85.0;
    private static final Double LIMITE_DISCO_URGENTE = 75.0;
    private static final Double LIMITE_MAXIMO_ALERTA = 100.0;


    // Função principal para gerar o CSV de Alertas
    public static String gerarCsvAlertas() {
        Dotenv dotenv = Dotenv.load();
        String modoExecucao = dotenv.get("MODO_EXECUCAO", "LOCAL");
        List<String[]> dadosMainframe = null;

        if (modoExecucao.equalsIgnoreCase("AWS")) {
            System.out.println("Lendo arquivo de dados do bucket TRUSTED...");
            dadosMainframe = ConexaoAws.lerArquivoCsvDoTrusted("trusted.csv");
        } else {
            System.err.println("Modo LOCAL de leitura de TRUSTED não implementado.");
            return "";
        }

        if (dadosMainframe == null || dadosMainframe.isEmpty()) {
            System.out.println("Nenhum dado lido do bucket TRUSTED.");
            return "";
        }

        List<Alerta> listaAlertas = new ArrayList<>();
        // Remove o cabeçalho
        dadosMainframe.remove(0);

        // --- Lógica de Transformação e Alerta ---
        for (String[] linha : dadosMainframe) {
            try {
                // Indices do CSV trusted:
                // 0=macAdress; 1=timestamp; 2=identificacao-mainframe; 3=uso_cpu_total_%; 4=uso_ram_total_%; 5=uso_disco_total_%
                String macAdress = linha[0];
                String dtHora = linha[1];
                String identificacaoMainframe = linha[2]; // <--- NOVO: Pegando a identificação do mainframe
                Double usoCpu = Double.parseDouble(linha[3].replace(",", "."));
                Double usoRam = Double.parseDouble(linha[4].replace(",", "."));
                Double usoDisco = Double.parseDouble(linha[5].replace(",", "."));

                verificarAlerta(listaAlertas, dtHora, macAdress, identificacaoMainframe, "CPU", usoCpu, LIMITE_CPU_URGENTE); // <--- identificacaoMainframe adicionado
                verificarAlerta(listaAlertas, dtHora, macAdress, identificacaoMainframe, "RAM", usoRam, LIMITE_RAM_URGENTE); // <--- identificacaoMainframe adicionado
                verificarAlerta(listaAlertas, dtHora, macAdress, identificacaoMainframe, "Disco", usoDisco, LIMITE_DISCO_URGENTE); // <--- identificacaoMainframe adicionado

            } catch (NumberFormatException e) {
                System.err.println("Erro ao converter valor para numérico na linha: " + String.join(";", linha));
            }
        }

        // --- Geração do Novo CSV ---
        return montarCsvAlertas(listaAlertas);
    }

    // Aplica as regras de gravidade e adiciona o alerta à lista
    private static void verificarAlerta(List<Alerta> listaAlertas, String dtHora, String macAdress, String identificacaoMainframe, // <--- identificacaoMainframe adicionado
                                        String componente, Double valorColetado, Double limiteUrgente) {

        String gravidade = null;

        if (valorColetado >= LIMITE_MAXIMO_ALERTA) {
            gravidade = "Emergencia";
        } else if (valorColetado >= limiteUrgente && valorColetado < LIMITE_MAXIMO_ALERTA) {
            gravidade = "Muito Urgente";
        }

        if (gravidade != null) {
            listaAlertas.add(new Alerta(dtHora, valorColetado, componente, gravidade, macAdress, identificacaoMainframe)); // <--- identificacaoMainframe adicionado
        }
    }

    // Monta o conteúdo do CSV a ser enviado
    private static String montarCsvAlertas(List<Alerta> listaAlertas) {
        StringBuilder sb = new StringBuilder();
        // NOVO CABEÇALHO
        sb.append("dt_hora;valor_coletado_%;componente;gravidade;macAdress;identificacao_mainframe\n");

        for (Alerta alerta : listaAlertas) {
            sb.append(alerta.toCsvLine()).append("\n");
        }

        System.out.println("\n" + listaAlertas.size() + " alertas gerados.");
        return sb.toString();
    }
}
