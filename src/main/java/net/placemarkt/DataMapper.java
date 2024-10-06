package net.placemarkt;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DataMapper {
    private static final TypeFactory factory = TypeFactory.defaultInstance();
    private static final ObjectMapper jsonReader = new ObjectMapper();
    private static final ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
    public static final MapType stringMapType = factory.constructMapType(HashMap.class, String.class, String.class);

    public static Map<String, String> readToStringMap(String json) throws IOException {
        try {
            return jsonReader.readValue(json, stringMapType);
        } catch (JsonProcessingException e) {
            throw new IOException("Json processing exception", e);
        }
    }

    public static void readToFile(String yamlFilePath, String jsonFilePath) {
        try {
            Path path = Paths.get(yamlFilePath);
            String yaml = Transpiler.readFile(path.toString());
            Object obj = yamlReader.readValue(yaml, Object.class);
            ObjectNode node = jsonReader.valueToTree(obj);
            try (PrintWriter out = new PrintWriter(jsonFilePath)) {
                out.println(node.toString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
