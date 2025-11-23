package sptech.school;

import io.github.cdimascio.dotenv.Dotenv;
import java.io.IOException;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

// Importação da integração Jira
import static sptech.school.IntegracaoJira.abrirChamado;

public class ConexaoBd {


    // ==========================================
    // MÉTODO MAIN APENAS PARA TESTE DE CONEXÃO
    // ==========================================
    public static void main(String[] args) {
        System.out.println("--- Testando Conexão com Banco de Dados ---");

        try (Connection conn = getConnection()) {
            System.out.println("✅ Conexão estabelecida com sucesso!");

            // Teste de busca de limites (Troque pelo MAC de um mainframe que existe no seu banco)
            String macTeste = "166250251803552"; // Exemplo do seu script (Z15)
            System.out.println("Buscando métricas para o MAC: " + macTeste);

            Map<String, Double[]> limites = buscarLimitesMetricas(conn, macTeste);

            if (limites.isEmpty()) {
                System.out.println("⚠️ Nenhuma métrica encontrada. Verifique se o MAC está correto e se há métricas 'Uso' cadastradas.");
            } else {
                for (Map.Entry<String, Double[]> entry : limites.entrySet()) {
                    System.out.printf("   Componente: %s | Min: %.2f | Max: %.2f%n",
                            entry.getKey(), entry.getValue()[0], entry.getValue()[1]);
                }
            }

        } catch (SQLException e) {
            System.err.println("❌ Falha na conexão: " + e.getMessage());
        }
    }

    // Mapeamento de Gravidade conforme seus INSERTS (Tabela gravidade)
    private static final Map<String, Integer> MAP_GRAVIDADE_FK = new HashMap<>();
    static {
        // 1=Emergência, 2=Muito Urgente, 3=Urgente, 4=Normal
        MAP_GRAVIDADE_FK.put("Emergencia", 1);
        MAP_GRAVIDADE_FK.put("Emergência", 1); // Caso venha com acento
        MAP_GRAVIDADE_FK.put("Muito Urgente", 2);
        MAP_GRAVIDADE_FK.put("Urgente", 3);
        MAP_GRAVIDADE_FK.put("Normal", 4);
    }

    public static Connection getConnection() throws SQLException {
        Dotenv dotenv = Dotenv.load();
        String url = dotenv.get("DB_URL");
        String user = dotenv.get("DB_USER");
        String password = dotenv.get("DB_PASSWORD");
        return DriverManager.getConnection(url, user, password);
    }

    /**
     * Busca os limites MIN e MAX configurados na tabela metrica.
     * Retorna um Map onde a Chave é o nome do componente (ex: 'Processador')
     * e o Valor é um array [min, max].
     */
    public static Map<String, Double[]> buscarLimitesMetricas(Connection conn, String macAdress) throws SQLException {
        // SQL ajustado para suas tabelas: metrica -> componente -> mainframe
        String sql = """
            SELECT c.nome, m.min, m.max
            FROM metrica m
            JOIN componente c ON m.fkComponente = c.id
            JOIN mainframe mf ON m.fkMainframe = mf.id
            WHERE mf.macAdress = ? AND m.fkTipo = 1
        """;
        // Obs: fkTipo = 1 refere-se a 'Uso' conforme seu script

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
     * Insere o alerta na tabela 'alerta' descobrindo o fkMetrica dinamicamente.
     */
    public static void inserirAlerta(Connection conn,
                                     String dtHora, String nomeComponente, Double valorColetado,
                                     String macAdress, String identificacaoMainframe, String gravidade) {

        // 1. Descobrir o ID da gravidade
        // Remove acentos e ajusta casing se necessário para garantir o match
        String gravidadeChave = gravidade;
        if(gravidade.equalsIgnoreCase("Emergência")) gravidadeChave = "Emergencia";

        Integer fkGravidade = MAP_GRAVIDADE_FK.getOrDefault(gravidadeChave, 4); // Default 4 (Normal)

        // Se for Normal (4), a lógica de negócio geralmente não insere alerta ou insere apenas log
        if (fkGravidade == 4) return;

        // 2. SQL de Inserção
        // Precisamos sub-selecionar o ID da métrica baseado no MacAdress e Nome do Componente
        String sql = """
            INSERT INTO alerta (dt_hora, valor_coletado, fkGravidade, fkStatus, fkMetrica)
            VALUES (?, ?, ?, 1, (
                SELECT m.id 
                FROM metrica m
                JOIN mainframe mf ON m.fkMainframe = mf.id
                JOIN componente c ON m.fkComponente = c.id
                WHERE mf.macAdress = ? AND c.nome = ? AND m.fkTipo = 1
                LIMIT 1
            ));
        """;
        // Obs: fkStatus 1 = 'Aberto' conforme seu insert de exemplo

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            // Formatar Data: O Java String vem como ISO, o MySQL DATETIME aceita "YYYY-MM-DD HH:MM:SS"
            // Se o dtHora vier com "T" (ex: 2023-10-25T10:00:00), substituímos por espaço.
            stmt.setString(1, dtHora.replace("T", " "));
            stmt.setDouble(2, valorColetado);
            stmt.setInt(3, fkGravidade);
            stmt.setString(4, macAdress);

            // ATENÇÃO: O nome do componente deve ser igual ao do banco ('Processador', 'Memória RAM', 'Disco Rígido')
            // Se o seu CSV traz "CPU", precisa converter para "Processador" antes de mandar pra cá ou garantir que o CSV venha certo.
            stmt.setString(5, nomeComponente);

            int linhasAfetadas = stmt.executeUpdate();

            if (linhasAfetadas > 0) {
                System.out.printf("✅ Alerta %s inserido no BD para %s | Componente: %s | Valor: %.2f%%%n",
                        gravidade, identificacaoMainframe, nomeComponente, valorColetado);

                // 3. Integração com Jira (Somente se for crítico)
                if (fkGravidade <= 3) {
                    String summary = String.format("ALERTA %s: %s em %s", gravidade.toUpperCase(), nomeComponente, identificacaoMainframe);
                    String description = String.format(
                            "O Mainframe %s (MAC: %s) apresentou comportamento anômalo.\n" +
                                    "Componente: %s\n" +
                                    "Valor Coletado: %.2f%%\n" +
                                    "Gravidade: %s\n" +
                                    "Data/Hora: %s",
                            identificacaoMainframe, macAdress, nomeComponente, valorColetado, gravidade, dtHora
                    );
                    abrirChamado(summary, description);
                }
            } else {
                System.err.println("⚠️ Alerta não inserido. Verifique se o MAC Address e o Componente existem na tabela 'metrica'.");
            }

        } catch (SQLException e) {
            System.err.println("❌ Erro SQL ao inserir alerta: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("❌ Erro ao abrir chamado no Jira: " + e.getMessage());
        }
    }
}