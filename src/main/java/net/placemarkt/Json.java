package net.placemarkt;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;

public class Json {
    private static final TypeFactory factory = TypeFactory.defaultInstance();
    private static final ObjectMapper jsonReader = new ObjectMapper();
    public static final MapType stringMapType = factory.constructMapType(HashMap.class, String.class, String.class);

    public static Map<String, String> readToStringMap(String jsonString) throws IOException {
        try {
            return jsonReader.readValue(jsonString, stringMapType);
        } catch (JsonProcessingException e) {
            throw new IOException("Json processing exception", e);
        }
    }
}
