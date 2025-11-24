package sptech.school;

import io.github.cdimascio.dotenv.Dotenv;
import org.jetbrains.annotations.NotNull;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static sptech.school.IntegracaoJira.abrirChamado;


public class ConexaoBd {

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.load();

        String url = dotenv.get("DB_URL");
        String user = dotenv.get("DB_USER");
        String password = dotenv.get("DB_PASSWORD");

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("Conexão estabelecida com sucesso! \n");
        } catch (SQLException e) {
            System.err.println("Erro na conexão: " + e.getMessage());
        }


        try (Connection conn = DriverManager.getConnection(
                Dotenv.load().get("DB_URL"),
                Dotenv.load().get("DB_USER"),
                Dotenv.load().get("DB_PASSWORD"))) {

            List dadosDb = ConexaoBd.buscarMainFrame(conn,1);


        } catch (SQLException e) {
            System.err.println("Erro ao conectar no banco: " + e.getMessage());
        }
    }

    // Busca métricas configuradas para um mainframe
    public static List<Object> buscarMetricas(Connection conn, String macAdress) throws SQLException {
        String sql = """
                SELECT TIMESTAMPDIFF(MINUTE, al.dt_hora, NOW()) AS dif_ultimo_alerta,
                                    (SELECT COUNT(*)
                                        FROM alerta al2
                                        JOIN metrica mt2 ON mt2.id = al2.fkMetrica
                                        JOIN mainframe m2 ON m2.id = mt2.fkMainframe
                                        WHERE m2.macAdress = m.macAdress
                                          AND TIMESTAMPDIFF(HOUR, al2.dt_hora, NOW()) < 24
                                    ) AS incidentes_ultimas_24, m.fabricante, m.modelo
                                FROM mainframe AS m
                                JOIN metrica AS mt ON m.id = mt.fkMainframe
                                JOIN alerta AS al ON mt.id = al.fkMetrica
                                WHERE m.macAdress = ?
                                ORDER BY al.dt_hora DESC
                                LIMIT 1;
        """;

        List lista = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, macAdress);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                lista.add(rs.getString("dif_ultimo_alerta"));
                lista.add(rs.getString("incidentes_ultimas_24"));
                lista.add(rs.getString("fabricante"));
                lista.add(rs.getString("modelo"));
            }
        }
        return lista;
    }
   // busca todos mainframes

    public static List<Object> buscarMainFrame(Connection conn, Integer id) throws SQLException {
        String sql = """
            select m.macAdress from empresa e\s
                    join setor s  on e.id = s.fkempresa
                    join mainframe m on s.id = m.fksetor
                    where e.id = ? ;
                
        """;

        List lista = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            ResultSet rs = stmt.executeQuery();

            // next pega prox linha
            while (rs.next()) {
                lista.add(rs.getString("macAdress"));
            }

            System.out.println(lista);
        }
        return lista;
    }
    // busca todos mainframes
// Dentro da classe ConexaoAws

    public static List<String> buscarMac(Connection conn, String empresa) throws SQLException {
        String sql = """
            SELECT m.macAdress 
            FROM empresa e
            JOIN setor s ON s.fkempresa = e.id
            JOIN mainframe m ON m.fksetor = s.id
            WHERE e.id = ?;
        """;

        List<String> lista = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, empresa);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                lista.add(rs.getString("macAdress"));
            }
        }

        return lista;
    }

    // ------------------------------------------------------------------------------------------------------------------
    // ETL ALERTAS
    // ------------------------------------------------------------------------------------------------------------------

    // Mapeamento de gravidade
    private static final Map<String, Integer> MAP_GRAVIDADE_FK = new HashMap<>();
    static {
        MAP_GRAVIDADE_FK.put("Emergencia", 1);
        MAP_GRAVIDADE_FK.put("Emergência", 1); // caso venha com acento
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

    // Busca os limites MIN e MAX configurados na tabela metrica
    public static Map<String, Double[]> buscarLimitesMetricas(Connection conn, String macAdress) throws SQLException {
        String sql = """
            SELECT c.nome, m.min, m.max
            FROM metrica m
            JOIN componente c ON m.fkComponente = c.id
            JOIN mainframe mf ON m.fkMainframe = mf.id
            WHERE mf.macAdress = ? AND m.fkTipo = 1 
        """;//  fkTipo = 1 refere-se a 'Uso'

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

    private static String formatarDataSql(String dtHora) {
        try {
            // Divide em data e hora
            String[] partes = dtHora.split(" ");
            String data = partes[0]; // Ex: 21/11/2025
            String hora = partes[1]; // Ex: 12:45

            // Divide a data (DD, MM, YYYY)
            String[] dataPartes = data.split("/");
            String dia = dataPartes[0];
            String mes = dataPartes[1];
            String ano = dataPartes[2];

            // Reconstrói no formato YYYY-MM-DD HH:MM:SS
            return String.format("%s-%s-%s %s:00", ano, mes, dia, hora);
        } catch (Exception e) {
            System.err.println("Erro ao formatar data '" + dtHora + "'. Usando valor original.");
            return dtHora;
        }
    }

    // Insere o alerta na tabela alerta e descobre o fkMetrica
    public static void inserirAlerta(Connection conn,
                                     String dtHora, String nomeComponente, Double valorColetado,
                                     String macAdress, String identificacaoMainframe, String gravidade) {

        // Descobrir o ID da gravidade
        String gravidadeChave = gravidade;
        if(gravidade.equalsIgnoreCase("Emergência")) gravidadeChave = "Emergencia";

        Integer fkGravidade = MAP_GRAVIDADE_FK.getOrDefault(gravidadeChave, 4);
        if (fkGravidade == 4) return;

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
        """;// fkStatus 1 = 'Aberto'

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            // Formatar Data: AGORA USA A FUNÇÃO DE CONVERSÃO
            stmt.setString(1, formatarDataSql(dtHora));
            stmt.setDouble(2, valorColetado);
            stmt.setInt(3, fkGravidade);
            stmt.setString(4, macAdress);
            stmt.setString(5, nomeComponente);

            int linhasAfetadas = stmt.executeUpdate();

            if (linhasAfetadas > 0) {
                System.out.printf("✅ Alerta %s inserido no BD para %s | Componente: %s | Valor: %.2f%%%n",
                        gravidade, identificacaoMainframe, nomeComponente, valorColetado);

                // Integração com Jira
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
