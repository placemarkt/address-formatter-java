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
    public static interface Helpers {
        final ObjectMapper jsonReader = new ObjectMapper();
        final ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
    }

    public static interface Types {
        final TypeFactory factory = TypeFactory.defaultInstance();
        final MapType stringMapType = factory.constructMapType(HashMap.class, String.class, String.class);
    }

    public static Map<String, String> readToStringMap(String json) throws IOException {
        try {
            return Helpers.jsonReader.readValue(json, Types.stringMapType);
        } catch (JsonProcessingException e) {
            throw new IOException("Json processing exception", e);
        }
    }

    public static void readToFile(String yamlFilePath, String jsonFilePath) {
        try {
            Path path = Paths.get(yamlFilePath);
            String yaml = Transpiler.readFile(path.toString());
            Object obj = Helpers.yamlReader.readValue(yaml, Object.class);
            ObjectNode node = Helpers.jsonReader.valueToTree(obj);
            try (PrintWriter out = new PrintWriter(jsonFilePath)) {
                out.println(node.toString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
