package net.placemarkt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.type.MapType;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import com.google.common.base.CaseFormat;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

class AddressFormatter {

  private static final JsonNode worldwide = TemplateProcessor.transpileWorldwide();
  private static final JsonNode countryNames = TemplateProcessor.transpileCountryNames();
  private static final JsonNode aliases = TemplateProcessor.transpileAliases();
  private static final JsonNode abbreviations = TemplateProcessor.transpileAbbreviations();
  private static final JsonNode country2Lang = TemplateProcessor.transpileCountry2Lang();
  private static final JsonNode countryCodes = TemplateProcessor.transpileCountyCodes();
  private static final JsonNode stateCodes = TemplateProcessor.transpileStateCodes();
  private static final List knownComponents = getKnownComponents();

  private static List<String> getKnownComponents() {
    List<String> knownComponents = new ArrayList<>();
    Iterator<JsonNode> fields = AddressFormatter.aliases.elements();
    while (fields.hasNext()) {
      JsonNode field = fields.next();
      knownComponents.add(field.get("alias").textValue());
    }

    return knownComponents;
  }

  private final ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
  private final OutputType outputType;
  private final boolean abbreviate;
  private final boolean appendCountry;

  public static void main(String[] args) {
    AddressFormatter formatter = new AddressFormatter(OutputType.STRING, false, false);
    try {
      formatter.format("{country: Andorra,"
          + "countryCode: sh,"
          + "county: Andorra la Vella,"
          + "houseNumber: 88,"
          + "neighbourhood: Centre històric,"
          + "postcode: AD500,"
          + "residential: Avinguda Meritxell,"
          + "town: Andorra la Vella}");
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  AddressFormatter(OutputType outputType, Boolean abbreviate, Boolean appendCountry) {
    this.outputType = outputType;
    this.abbreviate = abbreviate;
    this.appendCountry = appendCountry;
  }

  Map<String, Object> normalizeFields(Map<String, Object> components) {
    Map<String, Object> normalizedComponents = new HashMap<>();
    for (Map.Entry<String, Object> entry : components.entrySet()) {
      String field = entry.getKey();
      Object value = entry.getValue();
      String newField = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, field);
      if (!normalizedComponents.containsKey(newField) && knownComponents.contains(newField)) {
        normalizedComponents.put(newField, value);
      }
    }
    return normalizedComponents;
  }

  Map<String, Object> determineCountryCode(Map<String, Object> components,
      String fallbackCountryCode) {
    String countryCode;

    if (components.get("country_code") != null) {
      countryCode = (String) components.get("country_code");
    } else if (fallbackCountryCode != null) {
      countryCode = fallbackCountryCode;
    } else {
      throw new Error("No country code provided. Use fallbackCountryCode?");
    }

    countryCode = countryCode.toUpperCase();

    if (!worldwide.has(countryCode) || countryCode.length() != 2) {
      throw new Error("Invalid country code");
    }

    if (countryCode.equals("UK")) {
      countryCode = "GB";
    }

    JsonNode country = worldwide.get(countryCode);
    if (country != null && country.has("use_country")) {
      String oldCountryCode = countryCode;
      countryCode = country.get("use_country").asText().toUpperCase();

      if (country.has("change_country")) {
        String newCountry = country.get("change_country").asText();
        Pattern p = Pattern.compile("\\$(\\w*)");
        Matcher m = p.matcher(newCountry);
        String match;
        if (m.find()) {
          match = m.group(1); // $state
          Pattern p2 = Pattern.compile(String.format("\\$%s", match));
          Matcher m2;
          if (components.get(match) != null && components.containsKey(match)) {
            m2 = p2.matcher(newCountry);
            String toReplace = components.get(match).toString();
            newCountry = m2.replaceAll(toReplace);
          } else {
            m2 = p2.matcher(newCountry);
            newCountry = m2.replaceAll("");
          }
          components.put("country", newCountry);
        }

        JsonNode oldCountry = worldwide.get(oldCountryCode);
        JsonNode oldCountryAddComponent = oldCountry.get("add_component");
        if (oldCountryAddComponent != null && oldCountryAddComponent.toString().contains("=")) {
          String[] pairs = oldCountryAddComponent.toString().split("=");
          if (pairs[0].equals("state")) {
            components.put("state", pairs[1]);
          }
        }
      }
    }

    String state = (components.get("state") != null) ? components.get("state").toString() : null;

    if (countryCode.equals("NL") && state != null) {
      Pattern p1 = Pattern.compile("sint maarten", Pattern.CASE_INSENSITIVE);
      Matcher m1 = p1.matcher(state);
      Pattern p2 = Pattern.compile("aruba", Pattern.CASE_INSENSITIVE);
      Matcher m2 = p2.matcher(state);
      if (state.equals("Curaçao")) {
        countryCode = "CW";
        components.put("country", "Curaçao");
      } else if (m1.find()) {
        countryCode = "SX";
        components.put("country", "Sint Maarten");
      } else if (m2.find()) {
        countryCode = "AW";
        components.put("country", "Aruba");
      }
    }

    components.put("country_code", countryCode);
    return components;
  }

  Map<String, Object> applyAliases(Map<String, Object> components) {
    System.out.print(aliases.toPrettyString());
    Map<String, Object> aliasedComponents = new HashMap<>();
    components.forEach((key, value) -> {
      String newKey = key;
      Object newValue = value;
      Iterator<JsonNode> iterator = aliases.elements();
      while (iterator.hasNext()) {
        JsonNode pair = iterator.next();
        if (pair.get("alias").asText().equals(key)
            && components.get(pair.get("name").asText()) == null) {
          newKey = pair.get("name").asText();
          break;
        }
      }

      aliasedComponents.put(newKey, newValue);
    });

    return aliasedComponents;
  }

  JsonNode findTemplate(Map<String, Object> components) {
    JsonNode template;
    if (worldwide.has(components.get("country_code").toString())) {
      template = worldwide.get(components.get("country_code").toString());
    } else {
      template = worldwide.get("default");
    }

    return template;
  }

  public String format(String json) throws IOException {
    return format(json, null);
  }

  public String format(String json, String fallbackCountryCode) throws IOException {
    TypeFactory factory = TypeFactory.defaultInstance();
    MapType type = factory.constructMapType(HashMap.class, String.class, String.class);
    Map<String, Object> components = yamlReader.readValue(json, type);
    components = normalizeFields(components);

    if (fallbackCountryCode != null) {
      components.put("countryCode", fallbackCountryCode);
    }

    components = determineCountryCode(components, fallbackCountryCode);
    String countryCode = components.get("country_code").toString();

    if (appendCountry && countryNames.has(countryCode) && components.get("country") == null) {
      components.put("country", countryNames.get(countryCode).asText());
    }

    components = applyAliases(components);
    JsonNode template = findTemplate(components);

    return "";
  }

  public enum OutputType {STRING, ARRAY}
}
