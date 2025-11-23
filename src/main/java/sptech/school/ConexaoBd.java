package sptech.school;

import io.github.cdimascio.dotenv.Dotenv;
import org.jetbrains.annotations.NotNull;
import java.io.IOException;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

// Importação da integração Jira, que será usada no método de inserção
import static sptech.school.IntegracaoJira.abrirChamado;

public class ConexaoBd {

    // Método auxiliar para estabelecer a conexão
    public static Connection getConnection() throws SQLException {
        Dotenv dotenv = Dotenv.load();
        String url = dotenv.get("DB_URL");
        String user = dotenv.get("DB_USER");
        String password = dotenv.get("DB_PASSWORD");
        // O GeradorAlertas fará o try-with-resources para fechar a conexão
        return DriverManager.getConnection(url, user, password);
    }

    /**
     * Busca os limites MIN e MAX da tabela metrica.
     * @param conn Conexão JDBC aberta.
     * @param macAdress Mac Adress do mainframe.
     * @return Map onde a chave é o nome do componente e o valor é um array Double[] {min, max}.
     */
    public static Map<String, Double[]> buscarLimitesMetricas(Connection conn, String macAdress) throws SQLException {

        // Assume fkTipo=1 para "Uso" (CPU, RAM, DISCO)
        String sql = """
        SELECT c.nome, m.min, m.max
        FROM metrica m
        JOIN componente c ON m.fkComponente = c.id
        JOIN mainframe mf ON m.fkMainframe = mf.id
        WHERE mf.macAdress = ? AND m.fkTipo = 1; 
        """;

        Map<String, Double[]> limites = new HashMap<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, macAdress);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String nomeComponente = rs.getString("nome");
                    Double min = rs.getDouble("min");
                    Double max = rs.getDouble("max");
                    limites.put(nomeComponente, new Double[]{min, max});
                }
            }
        }
        return limites;
    }

    /**
     * Insere o alerta no banco de dados (tabela 'alerta') e abre chamado no Jira.
     */
    public static void inserirAlerta(@NotNull Connection conn,
                                     String dtHora, String nomeComponente, Double valorColetado,
                                     String macAdress, String identificacaoMainframe, String gravidade) {

        // 1. Mapeamento de Gravidade (String para ID do BD)
        int fkGravidade;
        switch (gravidade.toLowerCase()) {
            case "emergência": fkGravidade = 1; break;
            case "muito urgente": fkGravidade = 2; break;
            case "urgente": fkGravidade = 3; break;
            default: fkGravidade = 4; // Normal, mas não deve ser inserido aqui
        }

        // Se a gravidade for 'Normal', não insere no DB nem abre chamado
        if (fkGravidade == 4) return;

        // SQL para inserir o alerta no DB:
        String sql = """
            INSERT INTO alerta (dt_hora, valor_coletado, fkGravidade, fkMetrica)
            VALUES (?, ?, ?, (
                SELECT m.id FROM metrica m
                JOIN mainframe mf ON m.fkMainframe = mf.id
                JOIN componente c ON m.fkComponente = c.id
                WHERE mf.macAdress = ? AND c.nome = ?
            )); 
        """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            // Seta os valores
            stmt.setString(1, dtHora.replace("T", " ")); // Formato TIMESTAMP/DATETIME
            stmt.setDouble(2, valorColetado);
            stmt.setInt(3, fkGravidade);
            stmt.setString(4, macAdress); // Para o subquery
            stmt.setString(5, nomeComponente); // Para o subquery

            // Executa
            stmt.executeUpdate();
            System.out.printf("Alerta %s inserido para %s (%s) | Valor: %.2f\n",
                    gravidade, nomeComponente, identificacaoMainframe, valorColetado);

            // 2. Abertura de Chamado no Jira (Apenas para alertas críticos)
            if (fkGravidade <= 3) { // Emergência, Muito Urgente, Urgente
                String summary = String.format("ALERTA %s: Uso de %s no %s", gravidade.toUpperCase(), nomeComponente, identificacaoMainframe);
                String description = String.format(
                        "O Mainframe %s (MAC: %s) excedeu o limite de uso de %s.\n" +
                                "Valor Coletado: %.2f%%\n" +
                                "Data/Hora: %s\n" +
                                "Gravidade: %s",
                        identificacaoMainframe, macAdress, nomeComponente, valorColetado, dtHora, gravidade
                );
                abrirChamado(summary, description);
            }

        } catch (SQLException e) {
            System.err.println("Erro ao inserir alerta no DB: " + e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("Erro ao abrir chamado no Jira: " + e.getMessage());
        }
    }
}