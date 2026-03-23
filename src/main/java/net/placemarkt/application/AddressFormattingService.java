package net.placemarkt.application;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.placemarkt.api.Address;
import net.placemarkt.api.FormattingOptions;
import net.placemarkt.domain.rules.CountryProfile;
import net.placemarkt.domain.rules.PatternReplacement;
import net.placemarkt.domain.rules.ReplacementRule;
import net.placemarkt.domain.rules.RuleCatalog;
import net.placemarkt.domain.rules.TemplateDefinition;

public final class AddressFormattingService {

  private static final Pattern URL_VALUE = Pattern.compile("^https?://", Pattern.CASE_INSENSITIVE);
  private static final Pattern NUMERIC_VALUE = Pattern.compile("^\\d+$");
  private final RuleCatalog rules;
  private final TemplateRenderer renderer;

  public AddressFormattingService(RuleCatalog rules) {
    this.rules = rules;
    this.renderer = new TemplateRenderer();
  }

  public String format(Address address, FormattingOptions options, String fallbackCountryCode) throws IOException {
    Map<String, String> components = normalize(address.components());
    if (fallbackCountryCode != null) {
      components.put("country_code", fallbackCountryCode);
    }
    String countryCode = resolveCountryCode(components, fallbackCountryCode);
    CountryProfile profile = resolveProfile(countryCode);
    applyCountryRedirect(profile, components);
    countryCode = components.get("country_code");
    profile = resolveProfile(countryCode);

    if (options.appendCountry() && !components.containsKey("country")) {
      String countryName = rules.countryNames().get(countryCode);
      if (countryName != null) {
        components.put("country", countryName);
      }
    }

    applyAliases(components);
    normalizeNumericCountry(components);
    enrichRegionCodes(components);
    sanitizePostcode(components);
    addAttentionField(components);
    if (options.abbreviate()) {
      applyAbbreviations(components);
    }
    applyInputReplacements(profile.inputReplacements(), components);
    TemplateDefinition selectedTemplate = selectTemplate(profile, components);
    return renderer.render(selectedTemplate, components, profile.postFormatReplacements());
  }

  private Map<String, String> normalize(Map<String, Object> rawComponents) {
    Map<String, String> normalized = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : rawComponents.entrySet()) {
      if (entry.getKey() == null || entry.getValue() == null) {
        continue;
      }
      String value = String.valueOf(entry.getValue());
      if (URL_VALUE.matcher(value).find()) {
        continue;
      }
      String snakeCase = toSnakeCase(entry.getKey());
      normalized.putIfAbsent(snakeCase, value);
    }
    return normalized;
  }

  private String resolveCountryCode(Map<String, String> components, String fallbackCountryCode) throws IOException {
    String countryCode = components.get("country_code");
    if (countryCode == null || countryCode.trim().isEmpty()) {
      if (fallbackCountryCode == null || fallbackCountryCode.trim().isEmpty()) {
        throw new IOException("Missing country code");
      }
      countryCode = fallbackCountryCode;
    }
    countryCode = countryCode.trim().toUpperCase(Locale.ROOT);
    if ("UK".equals(countryCode)) {
      countryCode = "GB";
    }
    if (!rules.profiles().containsKey(countryCode)) {
      throw new IOException("Invalid country code");
    }
    components.put("country_code", countryCode);
    return countryCode;
  }

  private CountryProfile resolveProfile(String countryCode) {
    CountryProfile profile = rules.profiles().get(countryCode);
    if (profile == null) {
      return rules.profiles().get("DEFAULT");
    }
    return profile;
  }

  private void applyCountryRedirect(CountryProfile profile, Map<String, String> components) {
    String countryCode = components.get("country_code");
    if ("NL".equals(countryCode) && components.containsKey("state")) {
      String state = components.get("state");
      if ("Curaçao".equals(state)) {
        components.put("country_code", "CW");
        components.put("country", "Curaçao");
        return;
      }
      String normalized = normalizeLookupKey(state);
      if (normalized.contains("sint maarten")) {
        components.put("country_code", "SX");
        components.put("country", "Sint Maarten");
        return;
      }
      if (normalized.contains("aruba")) {
        components.put("country_code", "AW");
        components.put("country", "Aruba");
        return;
      }
    }
    if (profile.useCountry() == null || profile.useCountry().isEmpty()) {
      return;
    }
    components.put("country_code", profile.useCountry().toUpperCase(Locale.ROOT));
    if (profile.changeCountry() != null && !profile.changeCountry().isEmpty()) {
      components.put("country", interpolate(profile.changeCountry(), components));
    }
    components.putAll(profile.addedComponents());
  }

  private void applyAliases(Map<String, String> components) {
    Map<String, String> additions = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : components.entrySet()) {
      String canonical = rules.aliases().get(entry.getKey());
      if (canonical != null && !components.containsKey(canonical)) {
        additions.put(canonical, entry.getValue());
      }
    }
    components.putAll(additions);
  }

  private void enrichRegionCodes(Map<String, String> components) {
    String countryCode = components.get("country_code");
    if (!components.containsKey("state_code") && components.containsKey("state")) {
      String state = components.get("state");
      if (matchesWashingtonDc(state)) {
        components.put("state_code", "DC");
        components.put("state", "District of Columbia");
        components.putIfAbsent("city", "Washington");
      } else {
        String code = lookupRegionCode(rules.stateCodes(), countryCode, state);
        if (code != null) {
          components.put("state_code", code);
        }
      }
    }
    if (!components.containsKey("county_code") && components.containsKey("county")) {
      String code = lookupRegionCode(rules.countyCodes(), countryCode, components.get("county"));
      if (code != null) {
        components.put("county_code", code);
      }
    }
  }

  private void sanitizePostcode(Map<String, String> components) {
    String postcode = components.get("postcode");
    if (postcode == null) {
      return;
    }
    if (postcode.length() > 20 || postcode.matches("\\d+;\\d+.*")) {
      components.remove("postcode");
      return;
    }
    Matcher matcher = Pattern.compile("^(\\d{5}),\\d{5}$").matcher(postcode);
    if (matcher.matches()) {
      components.put("postcode", matcher.group(1));
    }
  }

  private void normalizeNumericCountry(Map<String, String> components) {
    String country = components.get("country");
    String state = components.get("state");
    if (country != null && state != null && NUMERIC_VALUE.matcher(country).matches()) {
      components.put("country", state);
      components.remove("state");
      components.remove("state_code");
    }
  }

  private void addAttentionField(Map<String, String> components) {
    List<String> unknownValues = new ArrayList<>();
    for (Map.Entry<String, String> entry : components.entrySet()) {
      if (!rules.knownComponents().contains(entry.getKey())) {
        unknownValues.add(entry.getValue());
      }
    }
    if (!unknownValues.isEmpty()) {
      components.put("attention", String.join(", ", unknownValues));
    }
  }

  private void applyAbbreviations(Map<String, String> components) {
    String countryCode = components.get("country_code");
    List<String> languages = rules.countryLanguages().get(countryCode);
    if (languages == null) {
      return;
    }
    for (String language : languages) {
      Map<String, Map<String, String>> dictionaries = rules.abbreviations().get(language);
      if (dictionaries == null) {
        continue;
      }
      for (Map.Entry<String, Map<String, String>> dictionary : dictionaries.entrySet()) {
        String component = dictionary.getKey();
        String value = components.get(component);
        if (value == null) {
          continue;
        }
        String abbreviated = value;
        for (Map.Entry<String, String> replacement : dictionary.getValue().entrySet()) {
          abbreviated = Pattern.compile("\\b" + Pattern.quote(replacement.getKey()) + "\\b",
              Pattern.UNICODE_CHARACTER_CLASS).matcher(abbreviated).replaceAll(replacement.getValue());
        }
        components.put(component, abbreviated);
      }
    }
  }

  private void applyInputReplacements(List<ReplacementRule> replacements, Map<String, String> components) {
    for (ReplacementRule replacement : replacements) {
      if (replacement.component() != null) {
        String current = components.get(replacement.component());
        if (current == null) {
          continue;
        }
        Pattern pattern = Pattern.compile(replacement.pattern(), Pattern.UNICODE_CHARACTER_CLASS);
        if (pattern.matcher(current).find()) {
          components.put(replacement.component(), replacement.replacement());
        }
        continue;
      }
      for (Map.Entry<String, String> entry : new ArrayList<>(components.entrySet())) {
        String updated = Pattern.compile(replacement.pattern(), Pattern.UNICODE_CHARACTER_CLASS)
            .matcher(entry.getValue())
            .replaceAll(replacement.replacement());
        components.put(entry.getKey(), updated);
      }
    }
  }

  private TemplateDefinition selectTemplate(CountryProfile profile, Map<String, String> components) {
    boolean missingRoad = isBlank(components.get("road"));
    boolean missingPostcode = isBlank(components.get("postcode"));
    CountryProfile defaults = rules.profiles().get("DEFAULT");
    if (missingRoad && missingPostcode) {
      if (profile.fallbackTemplate().source().length() > 0) {
        return profile.fallbackTemplate();
      }
      if (defaults != null && defaults.fallbackTemplate().source().length() > 0) {
        return defaults.fallbackTemplate();
      }
    }
    if (profile.addressTemplate().source().length() > 0) {
      return profile.addressTemplate();
    }
    return defaults == null ? profile.addressTemplate() : defaults.addressTemplate();
  }

  private String lookupRegionCode(Map<String, Map<String, String>> lookup, String countryCode, String name) {
    Map<String, String> values = lookup.get(countryCode);
    if (values == null) {
      return null;
    }
    return values.get(normalizeLookupKey(name));
  }

  private boolean matchesWashingtonDc(String state) {
    return state != null && state.toLowerCase(Locale.ROOT).matches("^washington,? d\\.?c\\.?$");
  }

  private String interpolate(String template, Map<String, String> components) {
    Matcher matcher = Pattern.compile("\\$(\\w+)").matcher(template);
    StringBuffer buffer = new StringBuffer();
    while (matcher.find()) {
      String replacement = components.getOrDefault(matcher.group(1), "");
      matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(buffer);
    return buffer.toString();
  }

  private String toSnakeCase(String key) {
    String value = key.replace('-', '_');
    value = value.replaceAll("([a-z0-9])([A-Z])", "$1_$2");
    return value.toLowerCase(Locale.ROOT);
  }

  private String normalizeLookupKey(String value) {
    String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replaceAll("\\p{M}+", "")
        .toLowerCase(Locale.ROOT);
    return normalized.replaceAll("[^\\p{Alnum}]+", " ").trim();
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
