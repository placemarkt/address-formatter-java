package net.placemarkt.domain.rules;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CountryProfile {

  private final String code;
  private final TemplateDefinition addressTemplate;
  private final TemplateDefinition fallbackTemplate;
  private final List<ReplacementRule> inputReplacements;
  private final List<PatternReplacement> postFormatReplacements;
  private final String useCountry;
  private final String changeCountry;
  private final Map<String, String> addedComponents;

  public CountryProfile(
      String code,
      TemplateDefinition addressTemplate,
      TemplateDefinition fallbackTemplate,
      List<ReplacementRule> inputReplacements,
      List<PatternReplacement> postFormatReplacements,
      String useCountry,
      String changeCountry,
      Map<String, String> addedComponents) {
    this.code = code;
    this.addressTemplate = addressTemplate;
    this.fallbackTemplate = fallbackTemplate;
    this.inputReplacements = Collections.unmodifiableList(inputReplacements);
    this.postFormatReplacements = Collections.unmodifiableList(postFormatReplacements);
    this.useCountry = useCountry;
    this.changeCountry = changeCountry;
    this.addedComponents = Collections.unmodifiableMap(new LinkedHashMap<>(addedComponents));
  }

  public String code() {
    return code;
  }

  public TemplateDefinition addressTemplate() {
    return addressTemplate;
  }

  public TemplateDefinition fallbackTemplate() {
    return fallbackTemplate;
  }

  public List<ReplacementRule> inputReplacements() {
    return inputReplacements;
  }

  public List<PatternReplacement> postFormatReplacements() {
    return postFormatReplacements;
  }

  public String useCountry() {
    return useCountry;
  }

  public String changeCountry() {
    return changeCountry;
  }

  public Map<String, String> addedComponents() {
    return addedComponents;
  }
}
