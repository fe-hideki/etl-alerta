package sptech.school;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class TrataCsv {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static String converterListaMainframes(List<Map<String, Object>> listaMainframes) {
        if (listaMainframes == null || listaMainframes.isEmpty()) {
            throw new RuntimeException("Lista de mainframes vazia");
        }
        return gson.toJson(listaMainframes);
    }

    public static void salvarJsonLocal(List<Map<String, Object>> listaMainframes, String caminhoArquivo) {
        try (FileWriter writer = new FileWriter(caminhoArquivo)) {
            String json = converterListaMainframes(listaMainframes);
            writer.write(json);
            System.out.println("JSON salvo em: " + caminhoArquivo);
        } catch (IOException e) {
            System.err.println("Erro ao salvar JSON: " + e.getMessage());
        }
    }
}
