package net.placemarkt;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
  }

  private static void transpileAliases() throws IOException {
    Path path = Paths.get("address-formatting/conf/components.yaml");
    String yaml = Files.readString(path);
    Object obj = yamlReader.readValue(yaml, Object.class);
    String json = jsonWriter.writeValueAsString(obj);
    try (PrintWriter out = new PrintWriter("src/main/resources/templates/aliases.json")) {
      out.println(json);
    }
  }

  private static void transpileCountry2Lang() throws IOException {
    Path path = Paths.get("address-formatting/conf/country2lang.yaml");
    String yaml = Files.readString(path);
    Object obj = yamlReader.readValue(yaml, Object.class);
    String json = jsonWriter.writeValueAsString(obj);
    try (PrintWriter out = new PrintWriter("src/main/resources/templates/country2Lang.json")) {
      out.println(json);
    }
  }

  private static void transpileCountryCodes() throws IOException {
    Path path = Paths.get("address-formatting/conf/country_codes.yaml");
    String yaml = Files.readString(path);
    Object obj = yamlReader.readValue(yaml, Object.class);
    String json = jsonWriter.writeValueAsString(obj);
    try (PrintWriter out = new PrintWriter("src/main/resources/templates/countryCodes.json")) {
      out.println(json);
    }
  }

  private static void transpileStateCodes() throws IOException {
    Path path = Paths.get("address-formatting/conf/state_codes.yaml");
    String yaml = Files.readString(path);
    Object obj = yamlReader.readValue(yaml, Object.class);
    String json = jsonWriter.writeValueAsString(obj);
    try (PrintWriter out = new PrintWriter("src/main/resources/templates/stateCodes.json")) {
      out.println(json);
    }
  }

  private static void transpileAbbreviations() throws IOException {
    Object abbreviations = new Object();
    ObjectReader objectReader = yamlReader.readerForUpdating(abbreviations);
    try (Stream<Path> paths = Files.list(Paths.get("address-formatting/conf/abbreviations"))) {
      ObjectNode json = jsonWriter.createObjectNode();
      paths.forEach(path -> {
        System.out.println(path);
        try {
          String yaml = Files.readString(path);
          Object obj = yamlReader.readValue(yaml, Object.class);
          ObjectNode node = jsonWriter.valueToTree(obj) ;
          json.setAll(node);
        } catch (IOException e) {
          e.printStackTrace();
        }

      });
      String jsonString = jsonWriter.writeValueAsString(json);
      try (PrintWriter out = new PrintWriter("src/main/resources/templates/abbreviations.json")) {
        out.println(jsonString);
      }
      //paths.filter(Files::isRegularFile).collect(Collectors.toSet());
    }

    //Path path = Paths.get("address-formatting/conf/abbreviations");
    //String yaml = Files.readString(path);
    //Object obj = yamlReader.readValue(yaml, Object.class);
    //String json = jsonWriter.writeValueAsString(obj);
    //try (PrintWriter out = new PrintWriter("src/main/resources/templates/abbreviations.json")) {
    //  out.println(json);
    //}
  }

  private static void transpileCountryNames() throws IOException {
    Path path = Paths.get("address-formatting/conf/country_codes.yaml");
    String yaml = Files.readString(path);
    String formattedYaml = yaml.replaceAll(" \\# ", " ");
    System.out.println(formattedYaml);
    Object obj = yamlReader.readValue(formattedYaml, Object.class);
    String json = jsonWriter.writeValueAsString(obj);
    try (PrintWriter out = new PrintWriter("src/main/resources/templates/countryNames.json")) {
      out.println(json);
    }
  }

  public static void main(String[] args) {
    try {
      //DONE
      //TemplateProcessor.transpileWorldwide();
      //TemplateProcessor.transpileCountryNames();
      //TODO
      TemplateProcessor.transpileAbbreviations();
      //TemplateProcessor.transpileAliases();
      //TemplateProcessor.transpileCountry2Lang();
      //TemplateProcessor.transpileCountryCodes();
      //TemplateProcessor.transpileStateCodes();
    } catch (Exception e){
      e.printStackTrace();
    }
  }
}
