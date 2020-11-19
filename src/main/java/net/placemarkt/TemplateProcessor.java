package net.placemarkt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class PrepareTemplates {
  public static void test() throws JsonProcessingException, IOException {
    Path path = Paths.get("address-formatting/conf/countries/worldwide.yaml");
    String yaml = Files.readString(path);
    ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
    Object obj = yamlReader.readValue(yaml, Object.class);
    ObjectMapper jsonWriter = new ObjectMapper();
    String json = jsonWriter.writeValueAsString(obj);
    System.out.println(json);
  }

  public static void main(String[] args) {
    try {
      PrepareTemplates.test();
    } catch (Exception e){
      e.printStackTrace();
    }
  }
}
