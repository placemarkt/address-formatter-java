package net.placemarkt;

import com.fasterxml.jackson.databind.JsonNode;

public class AddressFormatterBuilder {

  static {
    final JsonNode worldwide = TemplateProcessor.transpileWorldwide();
    final JsonNode countryNames = TemplateProcessor.transpileCountryNames();
    final JsonNode aliases = TemplateProcessor.transpileAliases();
    final JsonNode abbreviations = TemplateProcessor.transpileAbbreviations();
    final JsonNode country2Lang = TemplateProcessor.transpileCountry2Lang();
    final JsonNode countryCodes = TemplateProcessor.transpileCountyCodes();
    final JsonNode stateCodes = TemplateProcessor.transpileStateCodes();
  }

  private String countryCode;
  private Boolean abbreviate;
  private String outputFormat;

  public AddressFormatterBuilder() {}

  public AddressFormatterBuilder setCountryCode(String val) {
    this.countryCode = val; return this;
  }

  public AddressFormatterBuilder setAbbreviate(Boolean val) {
    this.abbreviate = val; return this;
  }

  public AddressFormatterBuilder setOutputFormat(String val) {
    this.outputFormat = val; return this;
  }

  public AddressFormatter build() {
    return new AddressFormatter(countryCode, abbreviate, outputFormat);
  }

}
