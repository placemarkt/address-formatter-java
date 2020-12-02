package net.placemarkt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.List;
import java.util.Iterator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.JsonNode;

public class TemplateProcessor {

  private static final ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
  private static final YAMLFactory yamlFactory = new YAMLFactory();
  private static final ObjectMapper jsonWriter = new ObjectMapper();

  static JsonNode transpileWorldwide() {
    ObjectNode node = null;
    try {
      Path path = Paths.get("address-formatting/conf/countries/worldwide.yaml");
      String yaml = Files.readString(path);
      Object obj = yamlReader.readValue(yaml, Object.class);
      node = jsonWriter.valueToTree(obj);
    } catch (IOException e) {
      e.printStackTrace();
    }

    return node;
  }

  static JsonNode transpileCountryNames() {
    ObjectNode node = null;
    try {
      Path path = Paths.get("address-formatting/conf/country_codes.yaml");
      String yaml = Files.readString(path);
      String formattedYaml = yaml.replaceAll(" \\# ", " ");
      Object obj = yamlReader.readValue(formattedYaml, Object.class);
      node = jsonWriter.valueToTree(obj);
    } catch (IOException e) {
      e.printStackTrace();
    }

    return node;
  }

  static JsonNode transpileAliases() {
    try {
      final ArrayNode node = jsonWriter.createArrayNode();
      Path path = Paths.get("address-formatting/conf/components.yaml");
      YAMLParser yamlParser = yamlFactory.createParser(path.toFile());
      List<ObjectNode> nodes = jsonWriter
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
      return node;
    } catch (IOException e) {
      e.printStackTrace();
    }

    return null;
  }

  static JsonNode transpileAbbreviations() {
    ObjectNode abbreviations = jsonWriter.createObjectNode();
    try {
      try (Stream<Path> paths = Files.list(Paths.get("address-formatting/conf/abbreviations"))) {
        paths.forEach(path -> {
          try {
            String fileNameWithExtension = path.getFileName().toString();
            int pos = fileNameWithExtension.lastIndexOf(".");
            String fileName = fileNameWithExtension.substring(0, pos).toUpperCase();
            String yaml = Files.readString(path);
            Object obj = yamlReader.readValue(yaml, Object.class);
            JsonNode country = jsonWriter.valueToTree(obj);
            Iterator<String> fieldName = country.fieldNames();
            ArrayNode countryComponentArray = jsonWriter.createArrayNode();
            while (fieldName.hasNext()) {
              String type = fieldName.next();
              JsonNode replacements = country.get(type);
              Iterator<String> srcs = replacements.fieldNames();
              ArrayNode pairs = jsonWriter.createArrayNode();
              while (srcs.hasNext()) {
                String src = srcs.next();
                String dest = replacements.get(src).textValue();
                ObjectNode pair = pairs.addObject();
                pair.put("src", src);
                pair.put("dest", dest);
              }
              ObjectNode countryComponent = jsonWriter.createObjectNode();
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

    } catch (IOException e) {
      e.printStackTrace();
    }

    return abbreviations;
  }

  static ObjectNode transpileCountry2Lang() {
    ObjectNode node = null;
    try {
      Path path = Paths.get("address-formatting/conf/country2lang.yaml");
      String yaml = Files.readString(path);
      Object obj = yamlReader.readValue(yaml, Object.class);
      node = jsonWriter.valueToTree(obj);
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
    } catch (IOException e) {
      e.printStackTrace();
    }

    return node;
  }

  /*
  TODO: Look into formatting this data in such a way that makes it easier to query
   */
  static ObjectNode transpileCountyCodes() {
    ObjectNode node = null;
    try {
      Path path = Paths.get("address-formatting/conf/county_codes.yaml");
      String yaml = Files.readString(path);
      Object obj = yamlReader.readValue(yaml, Object.class);
      node = jsonWriter.valueToTree(obj);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return node;
  }

  /*
  TODO: Look into formatting this data in such a way that makes it easier to query
   */
  static ObjectNode transpileStateCodes() {
    ObjectNode node = null;
    try {
      Path path = Paths.get("address-formatting/conf/state_codes.yaml");
      String yaml = Files.readString(path);
      Object obj = yamlReader.readValue(yaml, Object.class);
      node = jsonWriter.valueToTree(obj);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return node;
  }
}
