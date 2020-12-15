package net.placemarkt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.type.MapType;
import com.github.mustachejava.*;
import com.google.common.primitives.Ints;
import java.io.IOException;
import java.io.StringWriter;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import com.google.common.base.CaseFormat;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.Optional;
import static java.util.Map.entry;

class AddressFormatter {

  private static final JsonNode worldwide = TemplateProcessor.transpileWorldwide();
  private static final JsonNode countryNames = TemplateProcessor.transpileCountryNames();
  private static final JsonNode aliases = TemplateProcessor.transpileAliases();
  private static final JsonNode abbreviations = TemplateProcessor.transpileAbbreviations();
  private static final JsonNode country2Lang = TemplateProcessor.transpileCountry2Lang();
  private static final JsonNode countyCodes = TemplateProcessor.transpileCountyCodes();
  private static final JsonNode stateCodes = TemplateProcessor.transpileStateCodes();
  private static final List<String> knownComponents = getKnownComponents();
  private static final Map<String, String> replacements = Map.ofEntries(
      entry("[\\},\\s]+$", ""),
      entry("^[,\\s]+", ""),
      entry("^- ", ""),
      entry(",\\s*,", ", "),
      entry("[ \t]+,[ \t]+", ", "),
      entry("[ \t][ \t]+", " "),
      entry("[ \t]\n", "\n"),
      entry("\n,", "\n"),
      entry(",+", ","),
      entry(",\n", "\n"),
      entry("\n[ \t]+", "\n"),
      entry("\n+", "\n")
  );
  private static final RegexCache regexCache = new RegexCache();

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

  String format(String json) throws IOException {
    return format(json, null);
  }

  String format(String json, String fallbackCountryCode) throws IOException {
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
    return renderTemplate(template, components);
  }

  Map<String, Object> normalizeFields(Map<String, Object> components) {
    Map<String, Object> normalizedComponents = new HashMap<>();
    for (Map.Entry<String, Object> entry : components.entrySet()) {
      String field = entry.getKey();
      Object value = entry.getValue();
      String newField = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, field);
      if (!normalizedComponents.containsKey(newField)) {
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
        components.put("country", newCountry);
      }

      JsonNode oldCountry = worldwide.get(oldCountryCode);
      JsonNode oldCountryAddComponent = oldCountry.get("add_component");
      if (oldCountryAddComponent != null && oldCountryAddComponent.toString().contains("=")) {
        String[] pairs = oldCountryAddComponent.textValue().split("=");
        if (pairs[0].equals("state")) {
          components.put("state", pairs[1]);
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

  Map<String, Object> cleanupInput(Map<String, Object> components, JsonNode replacements) {
    Object country = components.get("country");
    Object state = components.get("state");

    if (country != null && state != null && Ints.tryParse((String) country) != null) {
      components.put("country", state);
      components.remove("state");
    }
    if (replacements != null && replacements.size() > 0) {
      for (String component : components.keySet()) {
        Iterator<JsonNode> rIterator = replacements.iterator();
        String regex = String.format("^%s=", component);
        Pattern p = Pattern.compile(regex);
        while (rIterator.hasNext()) {
          ArrayNode replacement = (ArrayNode) rIterator.next();
          Matcher m = p.matcher(replacement.get(0).asText());
          if (m.find()) {
            m.reset();
            String value = m.replaceAll("");
            if (components.get(component).toString().equals(value)) {
              components.put(component, replacement.get(1).asText());
            }
            m.reset();
          } else {
            Pattern p2 = Pattern.compile(replacement.get(0).asText());
            Matcher m2 = p2.matcher(components.get(component).toString());
            String value = m2.replaceAll(replacement.get(1).asText());
            m.reset();
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
      components.put("county_code", countyCode);
    }

    List<String> unknownComponents = components.entrySet().stream().filter(component -> {
      if (component.getKey() == null) {
        return false;
      }
      return !knownComponents.contains(component.getKey());
    }).map(component -> component.getValue().toString()).collect(Collectors.toList());

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

    if (abbreviate && components.containsKey("country_code") && country2Lang.has(components.get("country_code").toString())) {
      JsonNode languages = country2Lang.get(components.get("country_code").toString());
      StreamSupport.stream(languages.spliterator(), false)
          .filter(language -> abbreviations.has(language.textValue()))
          .map(language -> abbreviations.get(language.textValue())).forEach(
          languageAbbreviations -> StreamSupport.stream(languageAbbreviations.spliterator(), false)
              .filter(abbreviation -> abbreviation.has("component"))
              .forEach(abbreviation -> StreamSupport.stream(abbreviation.get("replacements").spliterator(), false)
                  .forEach(replacement -> {
                    String oldComponent = components.get(abbreviation).toString();
                    String regex = String.format("\b%s\b", replacements.get("src").asText());
                    Pattern p = Pattern.compile(regex);
                    Matcher m = p.matcher(oldComponent);
                    String newComponent = m.replaceFirst(replacement.get("dest").asText());
                    components.put(abbreviation.toString(), newComponent);
                  })));
    }

    Pattern p = Pattern.compile("^https?://");
    return components.entrySet().stream().filter(component -> {
      if (component.getValue() == null) {
        return false;
      }

      Matcher m = p.matcher(component.getValue().toString());

      if (m.matches()) {
        m.reset();
        return false;
      }

      m.reset();
      return true;
    }).collect(Collectors.toMap(Entry::getKey, Entry::getValue));
  }

  Map<String, Object> applyAliases(Map<String, Object> components) {
    Map<String, Object> aliasedComponents = new HashMap<>();
    components.forEach((key, value) -> {
      String newKey = key;
      Iterator<JsonNode> iterator = aliases.elements();
      while (iterator.hasNext()) {
        JsonNode pair = iterator.next();
        if (pair.get("alias").asText().equals(key)
            && components.get(pair.get("name").asText()) == null) {
          newKey = pair.get("name").asText();
          break;
        }
      }
      aliasedComponents.put(key, value);
      aliasedComponents.put(newKey, value);
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

  JsonNode chooseTemplateText(JsonNode template, Map<String, Object> components) {
    JsonNode selected;
    if (template.has("address_template")) {
      if (worldwide.has(template.get("address_template").asText())) {
        selected = worldwide.get(template.get("address_template").asText());
      } else {
        selected = template.get("address_template");
      }
    } else {
      JsonNode defaults = worldwide.get("default");
      selected = worldwide.get(defaults.get("address_template").textValue());
    }

    List<String> required = Arrays.asList("road", "postcode");
    long count = required.stream().filter(req -> !components.containsKey(req)).count();
    if (count == 2) {
      if (template.has("fallback_template")) {
        if (worldwide.has(template.get("fallback_template").asText())) {
          selected = worldwide.get(template.get("fallback_template").asText());
        } else {
          selected = template.get("fallback_template");
        }
      } else {
        JsonNode defaults = worldwide.get("default");
        selected = worldwide.get(defaults.get("fallback_template").textValue());
      }
    }
    return selected;
  }

  String getStateCode(String state, String countryCode) {
    if (!stateCodes.has(countryCode)) {
      return null;
    }

    JsonNode countryCodes = stateCodes.get(countryCode);
    Iterator<String> iterator = countryCodes.fieldNames();
    return StreamSupport
        .stream(Spliterators.spliteratorUnknownSize(iterator,
        Spliterator.ORDERED), false).filter(key-> {
          JsonNode code = countryCodes.get(key);
      if (code.isObject()) {
        if (code.has("default")) {
          return code.get("default").asText().toUpperCase().equals(state.toUpperCase());
        }
      } else {
        return code.asText().toUpperCase().equals(state.toUpperCase());
      }
      return false;
    }).findFirst().orElse(null);
  }

  String getCountyCode(String county, String countryCode) {
    if (!countyCodes.has(countryCode)) {
      return null;
    }
    JsonNode country = countyCodes.get(countryCode);
    Optional<JsonNode> countyCode = StreamSupport.stream(country.spliterator(), true).filter(posCounty -> {
      if (posCounty.isObject()) {
        if (posCounty.has("default")) {
          return posCounty.get("default").asText().toUpperCase().equals(county.toUpperCase());
        }
      } else {
        return posCounty.asText().toUpperCase().equals(county.toUpperCase());
      }
      return false;
    }).findFirst();

    return countyCode.map(JsonNode::asText).orElse(null);
  }

  String renderTemplate(JsonNode template, Map<String, Object> components) {
    Map<String, Object> callback = new HashMap<>();
    callback.put("first", (Function<String, String>) s -> {
      String[] splitted = s.split("\\s*\\|\\|\\s*");
      Optional<String> chosen = Arrays.stream(splitted).filter(v -> v.length() > 0).findFirst();
      return chosen.orElse("");
    });

    JsonNode templateText = chooseTemplateText(template, components);
    MustacheFactory mf = new DefaultMustacheFactory();
    Mustache m = mf.compile(new StringReader(templateText.asText()), "example");
    StringWriter st = new StringWriter();
    m.execute(st, new Object[]{ components, callback});
    String rendered = cleanupRender(st.toString());

    if (template.has("postformat_replace")) {
      ArrayNode postformat = (ArrayNode) template.get("postformat_replace");
      for (JsonNode regex : postformat) {
        Pattern p = Pattern.compile(regex.get(0).asText());
        Matcher m2 = p.matcher(rendered);
        rendered = m2.replaceAll(regex.get(1).asText());
      }
    }
    rendered = cleanupRender(rendered);
    String trimmed = rendered.strip();

    return trimmed + "\n";
  }

  String cleanupRender(String rendered) {
    Set<Map.Entry<String, String>> entries = replacements.entrySet();
    String deduped = rendered;

    for(Map.Entry<String, String> replacement : entries) {
      Pattern p = Pattern.compile(replacement.getKey(), Pattern.UNICODE_CHARACTER_CLASS);
      Matcher m = p.matcher(deduped);
      String predupe = m.replaceAll(replacement.getValue());
      deduped = dedupe(predupe);
    }

    return deduped;
  }

  String dedupe(String rendered) {
     return Arrays.stream(rendered.split("\n"))
        .map(s -> Arrays.stream(s.trim().split(", "))
            .map(String::trim).distinct().collect(Collectors.joining(", ")))
        .distinct()
        .collect(Collectors.joining("\n"));
  }

  public enum OutputType {STRING, ARRAY}
}
