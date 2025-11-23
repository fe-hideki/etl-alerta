package sptech.school;

import io.github.cdimascio.dotenv.Dotenv;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

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
    }

    // Insere alerta e abre chamado no Jira
    public static void inserirAlerta(@NotNull Connection conn,
                                     String dtHora, Integer fkComponente, Object valorColetado,
                                     String macAdress, String nomeComponente,String metrica) {
        String sql = """
            INSERT INTO alerta (dt_hora, fkComponente, valor_coletado, fkMainframe, fkGravidade, fkStatus, fkMetrica)
            VALUES (?, ?, ?, 
                    (SELECT id FROM mainframe WHERE macAdress = ?),
                    1, 1, ?)
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, dtHora);
            stmt.setInt(2, fkComponente);
            stmt.setObject(3, valorColetado);
            stmt.setString(4, macAdress);
            stmt.setInt(5, fkComponente);

            stmt.executeUpdate();

            String descricao = "Valor "+ metrica +" fora do limite: " + valorColetado+" || macAdress: "+macAdress+" || hora: "+dtHora;

            System.out.println("Alerta inserido para " + nomeComponente);
            abrirChamado("Alerta no " + nomeComponente, descricao);

        } catch (SQLException e) {
            System.err.println("Erro ao inserir alerta: " + e.getMessage());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Busca métricas configuradas para um mainframe
    public static List<List<Object>> buscarMetricas(Connection conn, String macAdress) throws SQLException {
        String sql = """
        SELECT cm.fkComponente, m.min, m.max, c.nome
        FROM componente_mainframe cm
        JOIN metrica m ON m.id = cm.fkMetrica AND m.fkComponente = cm.fkComponente
        JOIN componente c ON c.id = cm.fkComponente
        JOIN mainframe mf ON mf.id = cm.fkMainframe
        WHERE mf.macAdress = ?
        """;

        List<List<Object>> lista = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, macAdress);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                lista.add(List.of(
                        rs.getInt("fkComponente"),
                        rs.getDouble("min"),
                        rs.getDouble("max"),
                        rs.getString("nome")
                ));
            }
        }
        return lista;
    }
}
