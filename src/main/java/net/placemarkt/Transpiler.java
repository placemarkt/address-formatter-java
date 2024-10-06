package net.placemarkt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

public class Transpiler {

  private interface Constants {
    ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
    YAMLFactory yamlFactory = new YAMLFactory();
    ObjectMapper jsonWriter = new ObjectMapper();
  }

  public static void main(String[] args) {
    Transpiler.transpileWorldwide();
    Transpiler.transpileCountryNames();
    Transpiler.transpileAliases();
    Transpiler.transpileAbbreviations();
    Transpiler.transpileCountry2Lang();
    Transpiler.transpileCountyCodes();
    Transpiler.transpileStateCodes();
  }

  static void transpileWorldwide() {
    DataMapper.readToFile(
      "address-formatting/conf/countries/worldwide.yaml",
      "src/main/resources/worldwide.json"
    );
  }

  static void transpileCountryNames() {
    DataMapper.readToFile(
      "address-formatting/conf/country_codes.yaml",
      "src/main/resources/countryNames.json"
    );
  }

  static void transpileAliases() {
    try {
      final ArrayNode node = Constants.jsonWriter.createArrayNode();
      Path path = Paths.get("address-formatting/conf/components.yaml");
      YAMLParser yamlParser = Constants.yamlFactory.createParser(path.toFile());
      List<ObjectNode> nodes = Constants.jsonWriter
          .readValues(yamlParser, new TypeReference<ObjectNode>() {})
          .readAll();

      nodes.forEach(component-> {
        ArrayNode aliases = component.withArray("aliases");
        aliases.forEach(alias -> {
          ObjectNode componentNode = node.addObject();
          componentNode.put("alias", alias.textValue());
          componentNode.put("name", component.get("name").textValue());
        });
        ObjectNode componentNode =  node.addObject();
        componentNode.put("alias", component.get("name").textValue());
        componentNode.put("name", component.get("name").textValue());
      });
      try (PrintWriter out = new PrintWriter("src/main/resources/aliases.json")) {
        out.println(node.toString());
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  static void transpileAbbreviations() {
    ObjectNode abbreviations = Constants.jsonWriter.createObjectNode();
    try {
      try (Stream<Path> paths = Files.list(Paths.get("address-formatting/conf/abbreviations"))) {
        paths.forEach(path -> {
          try {
            String fileNameWithExtension = path.getFileName().toString();
            int pos = fileNameWithExtension.lastIndexOf(".");
            String fileName = fileNameWithExtension.substring(0, pos).toUpperCase();
            String yaml = Transpiler.readFile(path.toString());
            Object obj = Constants.yamlReader.readValue(yaml, Object.class);
            JsonNode country = Constants.jsonWriter.valueToTree(obj);
            Iterator<String> fieldName = country.fieldNames();
            ArrayNode countryComponentArray = Constants.jsonWriter.createArrayNode();
            while (fieldName.hasNext()) {
              String type = fieldName.next();
              JsonNode replacements = country.get(type);
              Iterator<String> srcs = replacements.fieldNames();
              ArrayNode pairs = Constants.jsonWriter.createArrayNode();
              while (srcs.hasNext()) {
                String src = srcs.next();
                String dest = replacements.get(src).textValue();
                ObjectNode pair = pairs.addObject();
                pair.put("src", src);
                pair.put("dest", dest);
              }
              ObjectNode countryComponent = Constants.jsonWriter.createObjectNode();
              countryComponent.put("component", type);
              countryComponent.set("replacements", pairs);
              countryComponentArray.add(countryComponent);
            }
            abbreviations.set(fileName, countryComponentArray);
          } catch (IOException e) {
            e.printStackTrace();
          }
        });
      }

      try (PrintWriter out = new PrintWriter("src/main/resources/abbreviations.json")) {
        out.println(abbreviations.toString());
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  static void transpileCountry2Lang() {
    ObjectNode node = null;
    try {
      Path path = Paths.get("address-formatting/conf/country2lang.yaml");
      String yaml = Transpiler.readFile(path.toString());
      Object obj = Constants.yamlReader.readValue(yaml, Object.class);
      node = Constants.jsonWriter.valueToTree(obj);
      Iterator<String> countries = node.fieldNames();
      while (countries.hasNext()) {
        String country = countries.next();
        String languages = node.get(country).asText();
        ArrayNode languagesArray = node.putArray(country);
        String[] languagesSplit = languages.split(",");
        for (String s : languagesSplit) {
          languagesArray.add(s.toUpperCase());
        }
      }
      try (PrintWriter out = new PrintWriter("src/main/resources/country2Lang.json")) {
        out.println(node.toString());
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /*
  TODO: Look into formatting this data in such a way that makes it easier to query
   */
  static void transpileCountyCodes() {
    DataMapper.readToFile(
      "address-formatting/conf/county_codes.yaml",
      "src/main/resources/countyCodes.json"
    );
  }

  /*
  TODO: Look into formatting this data in such a way that makes it easier to query
   */
  static void transpileStateCodes() {
    DataMapper.readToFile(
      "address-formatting/conf/state_codes.yaml",
      "src/main/resources/stateCodes.json"
    );
  }

  static String readFile(String path)
      throws IOException
  {
    byte[] encoded = Files.readAllBytes(Paths.get(path));
    return new String(encoded, StandardCharsets.UTF_8);
  }
}
