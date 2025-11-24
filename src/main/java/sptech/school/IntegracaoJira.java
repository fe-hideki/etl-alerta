package sptech.school;

import io.github.cdimascio.dotenv.Dotenv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class IntegracaoJira {

    // Abrir chamado
    public static void abrirChamado(String summary, String description) throws IOException {
        Dotenv dotenv = Dotenv.load();
        String jiraBaseUrl = dotenv.get("JIRA_URL"); // Ex: https://sua-empresa.atlassian.net
        String email = dotenv.get("JIRA_EMAIL");
        String apiToken = dotenv.get("API_JIRA");

        if (jiraBaseUrl == null || email == null || apiToken == null) {
            System.err.println("❌ Erro: Variáveis de ambiente do Jira (JIRA_URL, JIRA_EMAIL, API_JIRA) não configuradas.");
            return;
        }

        // Remove barra final se houver para evitar duplicidade
        if (jiraBaseUrl.endsWith("/")) {
            jiraBaseUrl = jiraBaseUrl.substring(0, jiraBaseUrl.length() - 1);
        }

        // Endpoint específico para criar chamados no Jira Service Management
        String apiUrl = jiraBaseUrl + "/rest/servicedeskapi/request";

        String auth = Base64.getEncoder().encodeToString((email + ":" + apiToken).getBytes(StandardCharsets.UTF_8));

        // Tratar string para json
        String safeSummary = summary.replace("\"", "\\\"");
        String safeDescription = description.replace("\"", "\\\"").replace("\n", "\\n");

        String json = "{"
                + "\"serviceDeskId\": \"2\","
                + "\"requestTypeId\": \"2\","
                + "\"requestFieldValues\": {"
                + "\"summary\": \"" + safeSummary + "\","
                + "\"description\": \"" + safeDescription + "\""
                + "}"
                + "}";

        URL url = new URL(apiUrl);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Authorization", "Basic " + auth);
        con.setRequestProperty("Accept", "application/json");
        con.setRequestProperty("Content-Type", "application/json");
        con.setDoOutput(true);

        try (OutputStream os = con.getOutputStream()) {
            byte[] input = json.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = con.getResponseCode();

        if (responseCode == 201) {
            System.out.println("✅ Chamado no Jira aberto com sucesso! Título: " + summary);
        } else {
            System.err.println("❌ Erro ao criar chamado no Jira. Código: " + responseCode);
            System.err.println("URL Tentada: " + apiUrl);

            // Ler o erro detalhado do Jira
            try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getErrorStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line.trim());
                }
                System.err.println("Detalhes do erro Jira: " + response.toString());
            } catch (Exception e) {
                System.err.println("Não foi possível ler os detalhes do erro.");
            }
        }
    }

    // Teste
    public static void main(String[] args) throws IOException {
        System.out.println("--- Teste Manual Jira ---");
        abrirChamado("Teste de Alerta Java", "Este é um teste.\nNova linha aqui.\nFim.");
    }
}