package net.placemarkt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.type.MapType;
import com.google.common.primitives.Ints;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import com.google.common.base.CaseFormat;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.Optional;

class AddressFormatter {

  private static final JsonNode worldwide = TemplateProcessor.transpileWorldwide();
  private static final JsonNode countryNames = TemplateProcessor.transpileCountryNames();
  private static final JsonNode aliases = TemplateProcessor.transpileAliases();
  private static final JsonNode abbreviations = TemplateProcessor.transpileAbbreviations();
  private static final JsonNode country2Lang = TemplateProcessor.transpileCountry2Lang();
  private static final JsonNode countyCodes = TemplateProcessor.transpileCountyCodes();
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

  Map<String, Object> cleanupInput(Map<String, Object> components, JsonNode replacements) {
    Map<String, Object> cleanedComponents = new HashMap<>();
    Object country = components.get("country");
    Object state = components.get("state");

    if (country != null && state != null && Ints.tryParse((String) country) != null) {
      components.put("country", state);
      components.remove("state");
    }
    if (replacements != null && replacements.size() > 0) {
      Iterator<String> cIterator = components.keySet().iterator();
      Iterator<JsonNode> rIterator = replacements.iterator();
      while (cIterator.hasNext()) {
        String component = cIterator.next();
        Pattern p = Pattern.compile(String.format("^\\$%s=", component));
        while (rIterator.hasNext()) {
          ArrayNode replacement = (ArrayNode) rIterator.next();
          Matcher m = p.matcher(replacement.get(0).asText());
          if (m.find()) {
            m.reset();
            String value = m.replaceAll(replacement.get(0).asText());
            if (components.get(component).toString().equals(value)) {
              components.put(component, replacement.get(1));
            }
          } else {
            Pattern p2 = Pattern.compile(replacement.get(0).asText());
            Matcher m2 = p2.matcher(component);
            String value = m.replaceAll(replacement.get(1).asText());
            components.put(component, value);
          }
        }
      }
    }

    if (!components.containsKey("state_code")  && components.containsKey("state")) {
      String stateCode = getStateCode(components.get("state").toString(), components.get("country_code").toString());
      components.put("state_code", stateCode);
      Pattern p = Pattern.compile("^washington,? d\\.?c\\.?");
      Matcher m = p.matcher(components.get("state").toString());
      if (m.find()) {
        components.put("state_code", "DC");
        components.put("state", "District of Columbia");
        components.put("city", "Washington");
      }
    }

    if (!components.containsKey("county_code") && components.containsKey("county")) {
      String countyCode = getCountyCode(components.get("county").toString(), components.get("country_code").toString());
    }

    List<String> unknownComponents = StreamSupport.stream(components.keySet().spliterator(), false).filter(component-> {
      if (knownComponents.contains(component)) {
        return true;
      }
      return false;
    }).collect(Collectors.toList());

    if (unknownComponents.size() > 0) {
      components.put("attention", String.join(", ", unknownComponents));
    }


    if (components.containsKey("postcode")) {
      String postCode = components.get("postcode").toString();
      components.put("postcode", postCode);
      Pattern p1 = Pattern.compile("^(\\d{5}),\\d{5}");
      Pattern p2 = Pattern.compile("\\d+;\\d+");
      Matcher m1 = p1.matcher(postCode);
      Matcher m2 = p2.matcher(postCode);
      if (postCode.length() > 20) {
        components.remove("postcode");
      } else if (m2.matches()) {
        components.remove("postcode");
      } else if (m1.matches()) {
        components.put("postcode", m1.group(1));
      }
    }


  }

  String getStateCode(String state, String countryCode) {
    if (!stateCodes.has(countryCode)) {
      return null;
    }
    JsonNode country = stateCodes.get(countryCode);
    Optional<JsonNode> stateCode = StreamSupport.stream(country.spliterator(), true).filter(posState-> {
      if (posState.isObject()) {
        if (posState.has("default")) {
          if (posState.get("default").asText().toUpperCase().equals(state.toUpperCase())) {
            return true;
          }
        }
      } else {
        if (posState.asText().toUpperCase().equals(state.toUpperCase())) {
          return true;
        }
      }
      return false;
    }).findFirst();
    if (stateCode.isPresent()) {
      return stateCode.get().asText();
    } else {
      return null;
    }
  }

  String getCountyCode(String county, String countryCode) {
    if (!countyCodes.has(countryCode)) {
      return null;
    }
    JsonNode country = countyCodes.get(countryCode);
    Optional<JsonNode> countyCode = StreamSupport.stream(country.spliterator(), true).filter(posCounty -> {
      if (posCounty.isObject()) {
        if (posCounty.has("default")) {
          if (posCounty.get("default").asText().toUpperCase().equals(county.toUpperCase())) {
            return true;
          }
        }
      } else {
        if (posCounty.asText().toUpperCase().equals(county.toUpperCase())) {
          return true;
        }
      }
      return false;
    }).findFirst();

    if (countyCode.isPresent()) {
      return countyCode.get().asText();
    } else {
      return null;
    }
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
    components = cleanupInput(components, template.get("replace"));

    return "";
  }

  public enum OutputType {STRING, ARRAY}
}
