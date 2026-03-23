package net.placemarkt.infrastructure.rules;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.placemarkt.domain.rules.CountryProfile;
import net.placemarkt.domain.rules.PatternReplacement;
import net.placemarkt.domain.rules.ReplacementRule;
import net.placemarkt.domain.rules.RuleCatalog;
import net.placemarkt.domain.rules.TemplateDefinition;

public final class BundledRuleCatalogLoader {

  private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<LinkedHashMap<String, Object>>() {};
  private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

  public RuleCatalog load() {
    try {
      Map<String, CountryProfile> profiles = loadProfiles();
      Map<String, String> aliases = new LinkedHashMap<>();
      Set<String> knownComponents = new LinkedHashSet<>();
      loadAliases(aliases, knownComponents);
      Map<String, List<String>> countryLanguages = loadCountryLanguages();
      Map<String, Map<String, Map<String, String>>> abbreviations = loadAbbreviations();
      Map<String, String> countryNames = loadCountryNames();
      Map<String, Map<String, String>> stateCodes = loadRegionCodes("address-formatting/conf/state_codes.yaml");
      Map<String, Map<String, String>> countyCodes = loadRegionCodes("address-formatting/conf/county_codes.yaml");
      return new RuleCatalog(
          profiles,
          aliases,
          knownComponents,
          countryLanguages,
          abbreviations,
          countryNames,
          stateCodes,
          countyCodes);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to load bundled formatting rules", e);
    }
  }

  private Map<String, CountryProfile> loadProfiles() throws IOException {
    JsonNode root = mapper.readTree(readResource("address-formatting/conf/countries/worldwide.yaml"));
    Map<String, CountryProfile> profiles = new LinkedHashMap<>();
    root.fieldNames().forEachRemaining(code -> {
      JsonNode node = root.get(code);
      if (!node.isObject()) {
        return;
      }
      profiles.put(code.toUpperCase(Locale.ROOT), new CountryProfile(
          code.toUpperCase(Locale.ROOT),
          new TemplateDefinition(resolveTemplate(root, node.get("address_template"))),
          new TemplateDefinition(resolveTemplate(root, node.get("fallback_template"))),
          parseInputReplacements(node.get("replace")),
          parsePatternReplacements(node.get("postformat_replace")),
          text(node.get("use_country")),
          text(node.get("change_country")),
          parseAddedComponents(text(node.get("add_component")))));
    });
    return profiles;
  }

  private void loadAliases(Map<String, String> aliases, Set<String> knownComponents) throws IOException {
    try (InputStream input = readResource("address-formatting/conf/components.yaml")) {
      MappingIterator<Map<String, Object>> documents = mapper.readerFor(MAP_TYPE).readValues(input);
      while (documents.hasNext()) {
        Map<String, Object> document = documents.next();
        String name = String.valueOf(document.get("name"));
        knownComponents.add(name);
        aliases.put(name, name);
        Object values = document.get("aliases");
        if (values instanceof List) {
          for (Object value : (List<?>) values) {
            String alias = String.valueOf(value);
            aliases.put(alias, name);
            knownComponents.add(alias);
          }
        }
      }
    }
  }

  private Map<String, List<String>> loadCountryLanguages() throws IOException {
    Map<String, List<String>> values = new LinkedHashMap<>();
    JsonNode root = mapper.readTree(readResource("address-formatting/conf/country2lang.yaml"));
    root.fieldNames().forEachRemaining(code -> {
      JsonNode node = root.get(code);
      List<String> languages = new ArrayList<>();
      if (node.isTextual()) {
        languages.addAll(Arrays.asList(node.asText().split(",")));
      }
      values.put(code.toUpperCase(Locale.ROOT), normalizeLanguages(languages));
    });
    return values;
  }

  private Map<String, Map<String, Map<String, String>>> loadAbbreviations() throws IOException {
    String[] files = {
        "no.yaml", "da.yaml", "vi.yaml", "cs.yaml", "sv.yaml", "es.yaml", "fr.yaml", "et.yaml",
        "gl.yaml", "eu.yaml", "ro.yaml", "ca.yaml", "de.yaml", "pl.yaml", "ru.yaml", "uk.yaml",
        "fi.yaml", "it.yaml", "sk.yaml", "pt.yaml", "en.yaml", "hu.yaml", "tr.yaml", "sl.yaml",
        "nl.yaml"
    };
    Map<String, Map<String, Map<String, String>>> abbreviations = new LinkedHashMap<>();
    for (String file : files) {
      JsonNode root = mapper.readTree(readResource("address-formatting/conf/abbreviations/" + file));
      Map<String, Map<String, String>> byComponent = new LinkedHashMap<>();
      root.fieldNames().forEachRemaining(component -> {
        JsonNode replacements = root.get(component);
        Map<String, String> componentReplacements = new LinkedHashMap<>();
        replacements.fieldNames().forEachRemaining(source -> {
          componentReplacements.put(source, replacements.get(source).asText());
        });
        byComponent.put(component, componentReplacements);
      });
      String language = file.substring(0, file.indexOf('.')).toUpperCase(Locale.ROOT);
      abbreviations.put(language, byComponent);
    }
    return abbreviations;
  }

  private Map<String, String> loadCountryNames() throws IOException {
    Map<String, String> names = new LinkedHashMap<>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(
        readResource("address-formatting/conf/country_codes.yaml"), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
          continue;
        }
        int hashIndex = trimmed.indexOf('#');
        if (hashIndex < 0) {
          continue;
        }
        String key = trimmed.substring(0, trimmed.indexOf(':')).replace("\"", "").trim();
        String value = trimmed.substring(hashIndex + 1).trim();
        if (!value.isEmpty()) {
          names.put(key.toUpperCase(Locale.ROOT), value);
        }
      }
    }
    return names;
  }

  private Map<String, Map<String, String>> loadRegionCodes(String resource) throws IOException {
    JsonNode root = mapper.readTree(readResource(resource));
    Map<String, Map<String, String>> reverseLookup = new LinkedHashMap<>();
    root.fieldNames().forEachRemaining(countryCode -> {
      Map<String, String> values = new TreeMap<>();
      JsonNode countryNode = root.get(countryCode);
      countryNode.fieldNames().forEachRemaining(code -> {
        JsonNode node = countryNode.get(code);
        collectRegionNames(values, code, node);
      });
      reverseLookup.put(countryCode.toUpperCase(Locale.ROOT), values);
    });
    return reverseLookup;
  }

  private void collectRegionNames(Map<String, String> values, String code, JsonNode node) {
    if (node.isTextual()) {
      values.put(normalizeLookupKey(node.asText()), code);
      return;
    }
    if (!node.isObject()) {
      return;
    }
    node.fieldNames().forEachRemaining(name -> {
      JsonNode child = node.get(name);
      if (child.isTextual()) {
        values.put(normalizeLookupKey(child.asText()), code);
      }
    });
  }

  private List<ReplacementRule> parseInputReplacements(JsonNode node) {
    if (node == null || !node.isArray()) {
      return Collections.emptyList();
    }
    List<ReplacementRule> replacements = new ArrayList<>();
    for (JsonNode rule : node) {
      String pattern = rule.get(0).asText();
      String component = null;
      String expression = pattern;
      int index = pattern.indexOf('=');
      if (index > 0) {
        component = pattern.substring(0, index);
        expression = pattern.substring(index + 1);
      }
      replacements.add(new ReplacementRule(component, expression, rule.get(1).asText()));
    }
    return replacements;
  }

  private List<PatternReplacement> parsePatternReplacements(JsonNode node) {
    if (node == null || !node.isArray()) {
      return Collections.emptyList();
    }
    List<PatternReplacement> replacements = new ArrayList<>();
    for (JsonNode rule : node) {
      replacements.add(new PatternReplacement(rule.get(0).asText(), rule.get(1).asText()));
    }
    return replacements;
  }

  private Map<String, String> parseAddedComponents(String raw) {
    Map<String, String> values = new LinkedHashMap<>();
    if (raw == null || raw.isEmpty()) {
      return values;
    }
    int index = raw.indexOf('=');
    if (index > 0 && index < raw.length() - 1) {
      values.put(raw.substring(0, index), raw.substring(index + 1));
    }
    return values;
  }

  private List<String> normalizeLanguages(List<String> languages) {
    List<String> normalized = new ArrayList<>();
    for (String language : languages) {
      normalized.add(language.trim().toUpperCase(Locale.ROOT));
    }
    return normalized;
  }

  private String normalizeLookupKey(String value) {
    String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replaceAll("\\p{M}+", "")
        .toLowerCase(Locale.ROOT);
    return normalized.replaceAll("[^\\p{Alnum}]+", " ").trim();
  }

  private InputStream readResource(String path) throws IOException {
    InputStream input = BundledRuleCatalogLoader.class.getClassLoader().getResourceAsStream(path);
    if (input == null) {
      throw new IOException("Missing resource: " + path);
    }
    return input;
  }

  private String text(JsonNode node) {
    return node == null || node.isNull() ? null : node.asText();
  }

  private String resolveTemplate(JsonNode root, JsonNode templateNode) {
    String value = text(templateNode);
    if (value == null) {
      return null;
    }
    JsonNode referenced = root.get(value);
    if (referenced != null && referenced.isTextual()) {
      return referenced.asText();
    }
    return value;
  }
}
