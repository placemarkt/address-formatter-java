package net.placemarkt.domain.rules;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RuleCatalog {

  private final Map<String, CountryProfile> profiles;
  private final Map<String, String> aliases;
  private final Set<String> knownComponents;
  private final Map<String, List<String>> countryLanguages;
  private final Map<String, Map<String, Map<String, String>>> abbreviations;
  private final Map<String, String> countryNames;
  private final Map<String, Map<String, String>> stateCodes;
  private final Map<String, Map<String, String>> countyCodes;

  public RuleCatalog(
      Map<String, CountryProfile> profiles,
      Map<String, String> aliases,
      Set<String> knownComponents,
      Map<String, List<String>> countryLanguages,
      Map<String, Map<String, Map<String, String>>> abbreviations,
      Map<String, String> countryNames,
      Map<String, Map<String, String>> stateCodes,
      Map<String, Map<String, String>> countyCodes) {
    this.profiles = Collections.unmodifiableMap(new LinkedHashMap<>(profiles));
    this.aliases = Collections.unmodifiableMap(new LinkedHashMap<>(aliases));
    this.knownComponents = Collections.unmodifiableSet(knownComponents);
    this.countryLanguages = Collections.unmodifiableMap(new LinkedHashMap<>(countryLanguages));
    this.abbreviations = Collections.unmodifiableMap(new LinkedHashMap<>(abbreviations));
    this.countryNames = Collections.unmodifiableMap(new LinkedHashMap<>(countryNames));
    this.stateCodes = Collections.unmodifiableMap(new LinkedHashMap<>(stateCodes));
    this.countyCodes = Collections.unmodifiableMap(new LinkedHashMap<>(countyCodes));
  }

  public Map<String, CountryProfile> profiles() {
    return profiles;
  }

  public Map<String, String> aliases() {
    return aliases;
  }

  public Set<String> knownComponents() {
    return knownComponents;
  }

  public Map<String, List<String>> countryLanguages() {
    return countryLanguages;
  }

  public Map<String, Map<String, Map<String, String>>> abbreviations() {
    return abbreviations;
  }

  public Map<String, String> countryNames() {
    return countryNames;
  }

  public Map<String, Map<String, String>> stateCodes() {
    return stateCodes;
  }

  public Map<String, Map<String, String>> countyCodes() {
    return countyCodes;
  }
}
