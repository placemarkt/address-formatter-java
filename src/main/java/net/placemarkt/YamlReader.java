package net.placemarkt;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;

public class YamlReader {
    private final TypeFactory factory = TypeFactory.defaultInstance();
    private final MapType type = factory.constructMapType(HashMap.class, String.class, String.class);
    private final ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
    private final String jsonString;

    public YamlReader(String jsonString) {
        this.jsonString = jsonString;
    }

    public Map<String, Object> read() throws IOException {
        try {
            return yamlReader.readValue(this.jsonString, type);
        } catch (JsonProcessingException e) {
            throw new IOException("Json processing exception", e);
        }
    }
}
