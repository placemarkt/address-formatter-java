package net.placemarkt;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.ArrayList;

public class AddressFormatter {

  private static final RegexPatternCache regexPatternCache = new RegexPatternCache();
  private static final List<String> knownComponents = AddressFormatter.getKnownComponents();
  private static final Map<String, String> replacements = new HashMap<String, String>() {{
    put("[\\},\\s]+$", "");
    put("^[,\\s]+", "");
    put("^- ", "");
    put(",\\s*,", ", ");
    put("[ \t]+,[ \t]+", ", ");
    put("[ \t][ \t]+", " ");
    put("[ \t]\n", "\n");
    put("\n,", "\n");
    put(",+", ",");
    put(",\n", "\n");
    put("\n[ \t]+", "\n");
    put("\n+", "\n");
  }};

  private final boolean abbreviate;
  private final boolean appendCountry;

  public AddressFormatter(Boolean abbreviate, Boolean appendCountry) {
    this.abbreviate = abbreviate;
    this.appendCountry = appendCountry;
  }

  public String format(String json) throws IOException {
    return format(json, null);
  }

  public Map<String, String> hydrateCountryCode(Map<String, String> components, String fallbackCountryCode) {
    if (fallbackCountryCode != null) {
      components.put("country_code", fallbackCountryCode);
    }

    return components;
  }

  public String format(String json, String fallbackCountryCode) throws IOException {
    Map<String, String> components = Json.readToStringMap(json);

    components = normalizeFields(components);
    components = hydrateCountryCode(components, fallbackCountryCode);
    components = determineCountryCode(components, fallbackCountryCode);
    
    String countryCode = components.get("country_code");
    if (appendCountry && Template.COUNTRY_NAMES.has(countryCode) && components.get("country") == null) {
      components.put("country", Template.COUNTRY_NAMES.get(countryCode).asText());
    }

    components = applyAliases(components);
    JsonNode template = findTemplate(components);
    components = cleanupInput(components, template.get("replace"));
    return renderTemplate(template, components);
  }

  Map<String, String> normalizeFields(Map<String, String> components) {
    Map<String, String> normalizedComponents = new HashMap<>();
    for (Map.Entry<String, String> entry : components.entrySet()) {
      String newField = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, entry.getKey());
      if (!normalizedComponents.containsKey(newField)) {
        normalizedComponents.put(newField, entry.getValue());
      }
    }
    return normalizedComponents;
  }

  Map<String, String> determineCountryCode(Map<String, String> components,
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

    if (!Template.WORLDWIDE.has(countryCode) || countryCode.length() != 2) {
      throw new Error("Invalid country code");
    }

    if (countryCode.equals("UK")) {
      countryCode = "GB";
    }

    JsonNode country = Template.WORLDWIDE.get(countryCode);
    if (country != null && country.has("use_country")) {
      String oldCountryCode = countryCode;
      countryCode = country.get("use_country").asText().toUpperCase();

      if (country.has("change_country")) {
        String newCountry = country.get("change_country").asText();
        Pattern p = regexPatternCache.get("\\$(\\w*)");
        Matcher m = p.matcher(newCountry);
        String match;
        if (m.find()) {
          match = m.group(1); // $state
          Pattern p2 = regexPatternCache.get(String.format("\\$%s", match));
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

      JsonNode oldCountry = Template.WORLDWIDE.get(oldCountryCode);
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
      Pattern p1 = regexPatternCache.get("sint maarten", Pattern.CASE_INSENSITIVE);
      Matcher m1 = p1.matcher(state);
      Pattern p2 = regexPatternCache.get("aruba", Pattern.CASE_INSENSITIVE);
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

  Map<String, String> cleanupInput(Map<String, String> components, JsonNode replacements) {
    String country = components.get("country");
    String state = components.get("state");

    if (country != null && state != null && Ints.tryParse((String) country) != null) {
      components.put("country", state);
      components.remove("state");
    }
    if (replacements != null && replacements.size() > 0) {
      for (String component : components.keySet()) {
        Iterator<JsonNode> rIterator = replacements.iterator();
        String regex = String.format("^%s=", component);
        Pattern p = regexPatternCache.get(regex);
        while (rIterator.hasNext()) {
          ArrayNode replacement = (ArrayNode) rIterator.next();
          Matcher m = p.matcher(replacement.get(0).asText());
          if (m.find()) {
            m.reset();
            String value = m.replaceAll("");
            Pattern replace_pattern = regexPatternCache.get(value);
            if (replace_pattern.matcher(components.get(component).toString()).find()) {
              components.put(component, replacement.get(1).asText());
            }
            m.reset();
          } else {
            Pattern p2 = regexPatternCache.get(replacement.get(0).asText());
            Matcher m2 = p2.matcher(components.get(component).toString());
            String value = m2.replaceAll(replacement.get(1).asText());
            m.reset();
            components.put(component, value);
          }
        }
      }
    }

    if (!components.containsKey("state_code")  && components.containsKey("state")) {
      String stateCode = getCode(
        components.get("state").toString(),
        components.get("country_code").toString(),
        Template.STATE_CODES
      );
      components.put("state_code", stateCode);
      Pattern p = regexPatternCache.get("^washington,? d\\.?c\\.?");
      Matcher m = p.matcher(components.get("state").toString());
      if (m.find()) {
        components.put("state_code", "DC");
        components.put("state", "District of Columbia");
        components.put("city", "Washington");
      }
    }

    if (!components.containsKey("county_code") && components.containsKey("county")) {
      String countyCode = getCode(
        components.get("county").toString(),
        components.get("country_code").toString(),
        Template.COUNTY_CODES
      );
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
      Pattern p1 = regexPatternCache.get("^(\\d{5}),\\d{5}");
      Pattern p2 = regexPatternCache.get("\\d+;\\d+");
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

    if (abbreviate && components.containsKey("country_code") && Template.COUNTRY_2_LANG.has(components.get("country_code").toString())) {
      JsonNode languages = Template.COUNTRY_2_LANG.get(components.get("country_code").toString());
      StreamSupport.stream(languages.spliterator(), false)
          .filter(language -> Template.ABBREVIATIONS.has(language.textValue()))
          .map(language -> Template.ABBREVIATIONS.get(language.textValue())).forEach(
          languageAbbreviations -> StreamSupport.stream(languageAbbreviations.spliterator(), false)
              .filter(abbreviation -> abbreviation.has("component"))
              .forEach(abbreviation -> StreamSupport.stream(abbreviation.get("replacements").spliterator(), false)
                  .forEach(replacement -> {
                    String key = abbreviation.get("component").asText();
                    if (key == null) {
                      return;
                    }
                    if (components.get(key) == null) {
                      return;
                    }
                    String oldComponent = components.get(key).toString();
                    String src = replacement.get("src").asText();
                    if (replacement.get("src").equals("Avenue")) {
                      System.out.println("here");
                    }
                    String regex = String.format("\\b%s\\b", replacement.get("src").asText());
                    Pattern p = regexPatternCache.get(regex);
                    Matcher m = p.matcher(oldComponent);
                    String newComponent = m.replaceAll(replacement.get("dest").asText());
                    components.put(key, newComponent);
                  })));
    }

    Pattern p = regexPatternCache.get("^https?://");
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

  Map<String, String> applyAliases(Map<String, String> components) {
    Map<String, String> aliasedComponents = new HashMap<>();
    components.forEach((key, value) -> {
      String newKey = key;
      Iterator<JsonNode> iterator = Template.ALIASES.elements();
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

  JsonNode findTemplate(Map<String, String> components) {
    JsonNode template;
    if (Template.WORLDWIDE.has(components.get("country_code"))) {
      template = Template.WORLDWIDE.get(components.get("country_code"));
    } else {
      template = Template.WORLDWIDE.get("default");
    }

    return template;
  }

  JsonNode chooseTemplateText(JsonNode template, Map<String, String> components) {
    JsonNode selected;
    if (template.has("address_template")) {
      if (Template.WORLDWIDE.has(template.get("address_template").asText())) {
        selected = Template.WORLDWIDE.get(template.get("address_template").asText());
      } else {
        selected = template.get("address_template");
      }
    } else {
      JsonNode defaults = Template.WORLDWIDE.get("default");
      selected = Template.WORLDWIDE.get(defaults.get("address_template").textValue());
    }

    List<String> required = Arrays.asList("road", "postcode");
    long count = required.stream().filter(req -> !components.containsKey(req)).count();
    if (count == 2) {
      if (template.has("fallback_template")) {
        if (Template.WORLDWIDE.has(template.get("fallback_template").asText())) {
          selected = Template.WORLDWIDE.get(template.get("fallback_template").asText());
        } else {
          selected = template.get("fallback_template");
        }
      } else {
        JsonNode defaults = Template.WORLDWIDE.get("default");
        selected = Template.WORLDWIDE.get(defaults.get("fallback_template").textValue());
      }
    }
    return selected;
  }

  String getCode(String state, String code, Template codesTemplate) {
    if (!codesTemplate.has(code)) {
      return null;
    }

    JsonNode countryCode = codesTemplate.get(code);
    Iterator<String> iterator = countryCode.fieldNames();
    return StreamSupport
        .stream(Spliterators.spliteratorUnknownSize(iterator,
        Spliterator.ORDERED), false).filter(key-> {
          JsonNode name = countryCode.get(key);
      if (name.isObject()) {
        if (name.has("default")) {
          return name.get("default").asText().toUpperCase().equals(state.toUpperCase());
        }
      } else {
        return name.asText().toUpperCase().equals(state.toUpperCase());
      }
      return false;
    }).findFirst().orElse(null);
  }

  String renderTemplate(JsonNode template, Map<String, String> components) {
    Map<String, Function<String, String>> callback = new HashMap<>();
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
        Pattern p = regexPatternCache.get(regex.get(0).asText());
        Matcher m2 = p.matcher(rendered);
        rendered = m2.replaceAll(regex.get(1).asText());
      }
    }
    rendered = cleanupRender(rendered);
    String trimmed = rendered.trim();

    return trimmed + "\n";
  }

  String cleanupRender(String rendered) {
    Set<Map.Entry<String, String>> entries = replacements.entrySet();
    String deduped = rendered;

    for(Map.Entry<String, String> replacement : entries) {
      Pattern p = regexPatternCache.get(replacement.getKey(), Pattern.UNICODE_CHARACTER_CLASS);
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

  static List<String> getKnownComponents() {
    List<String> knownComponents = new ArrayList<>();
    Iterator<JsonNode> fields = net.placemarkt.Template.ALIASES.elements();
    while (fields.hasNext()) {
      JsonNode field = fields.next();
      knownComponents.add(field.get("alias").textValue());
    }

    return knownComponents;
  }
}
