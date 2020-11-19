package net.placemarkt;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class TemplateProcessor {

  private static ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
  private static ObjectMapper jsonWriter = new ObjectMapper();

  private static void transpileWorldwide() throws IOException {
    Path path = Paths.get("address-formatting/conf/countries/worldwide.yaml");
    String yaml = Files.readString(path);
    Object obj = yamlReader.readValue(yaml, Object.class);
    String json = jsonWriter.writeValueAsString(obj);
    try (PrintWriter out = new PrintWriter("src/main/resources/templates/templates.json")) {
      out.println(json);
    }
    System.out.println(json);
  }

  private static void transpileCountry2Lang() throws IOException {
    Path path = Paths.get("address-formatting/conf/country2lang.yaml");
    String yaml = Files.readString(path);
    Object obj = yamlReader.readValue(yaml, Object.class);
    String json = jsonWriter.writeValueAsString(obj);
    try (PrintWriter out = new PrintWriter("src/main/resources/templates/country2Lang.json")) {
      out.println(json);
    }
    System.out.println(json);
  }

  public static void main(String[] args) {
    try {
      TemplateProcessor.transpileWorldwide();
      TemplateProcessor.transpileCountry2Lang();
    } catch (Exception e){
      e.printStackTrace();
    }
  }
}
