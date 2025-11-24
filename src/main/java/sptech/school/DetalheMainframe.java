package sptech.school;

import io.github.cdimascio.dotenv.Dotenv;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import java.io.FileWriter;
import java.io.IOException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class DetalheMainframe {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) {
        List<String> macs = new ArrayList<>();
        Integer empresa = 1;

        try (Connection conn = DriverManager.getConnection(
                Dotenv.load().get("DB_URL"),Dotenv.load().get("DB_USER"),Dotenv.load().get("DB_PASSWORD"))) {

        } catch (SQLException e) {
            System.err.println("Erro ao conectar no banco: " + e.getMessage());
        }

        List<String> dirs = ConexaoAws.listarDiretorios();
        List<Map<String, Object>> mainframesJson = new ArrayList<>();

        for (String dir : dirs) {

            String mac = dir.replace("1/", "").replace("/", "");

            List<String[]> linhas = ConexaoAws.lerArquivoCsvDoTrusted(mac, empresa, "trusted.csv");
            if (linhas.isEmpty()) continue;

            Integer ultimo = linhas.size() - 1;

            ArrayList<Double> cpu = new ArrayList<>();
            ArrayList<Double> ram = new ArrayList<>();
            ArrayList<Double> disk = new ArrayList<>();

            String macAdress = linhas.get(ultimo)[0].replace(",", ".");
            Double throughput = Double.valueOf(linhas.get(ultimo)[6].replace(",", "."));
            Double iops = Double.valueOf(linhas.get(ultimo)[7].replace(",", "."));
            Double latencia = Double.valueOf(linhas.get(ultimo)[10].replace(",", "."));
            Integer ultimoDado = linhas.size() - 1;

            for (int i = 0; i < 5; i++) {
                cpu.add(Double.valueOf(linhas.get(ultimoDado)[3].replace(",", ".")));
                ram.add(Double.valueOf(linhas.get(ultimoDado)[4].replace(",", ".")));
                disk.add(Double.valueOf(linhas.get(ultimoDado)[5].replace(",", ".")));
                ultimoDado--;
            }

            String incidentes = "";
            String minUltimoAlerta = "";
            String hhmm = "";
            String fabricante = "";
            String modelo = "";

            try (Connection conn = DriverManager.getConnection(
                    Dotenv.load().get("DB_URL"),Dotenv.load().get("DB_USER"),Dotenv.load().get("DB_PASSWORD"))) {

                List dadosDb = ConexaoBd.buscarMetricas(conn, macAdress);

                if (!dadosDb.isEmpty()) {
                    minUltimoAlerta = dadosDb.get(0).toString();
                    incidentes = dadosDb.get(1).toString();
                    fabricante = dadosDb.get(2).toString();
                    modelo = dadosDb.get(3).toString();

                    Integer hora = Integer.valueOf(minUltimoAlerta) / 60;
                    Integer minSobra = Integer.valueOf(minUltimoAlerta) % 60;
                    hhmm = hora + "h " + minSobra + "m";
                }

            } catch (SQLException e) {
                System.err.println("Erro ao conectar no banco: " + e.getMessage());
            }

            // Monta mapa com dados reais
            Map<String, Object> mainframe = new HashMap<>();
            mainframe.put("nome", modelo);
            mainframe.put("cpu", cpu);
            mainframe.put("ram", ram);
            mainframe.put("disk", disk);
            mainframe.put("Fabricante", fabricante);
            mainframe.put("mac", macAdress);
            mainframe.put("throughput", throughput);
            mainframe.put("eventos", Integer.valueOf(incidentes));
            mainframe.put("iops", iops);
            mainframe.put("latencia", latencia);
            mainframesJson.add(mainframe);
        }

        String jsonFinal = gson.toJson(mainframesJson);

        // Exibe no console
        System.out.println(jsonFinal);

        // Salva json
        try (FileWriter writer = new FileWriter("mainframes.json")) {
            writer.write(jsonFinal);
            ConexaoAws.salvarJsonNoS3("dashIgor.json", jsonFinal);
        } catch (IOException e) {
            System.err.println("Erro ao salvar JSON: " + e.getMessage());
        }
    }
}
