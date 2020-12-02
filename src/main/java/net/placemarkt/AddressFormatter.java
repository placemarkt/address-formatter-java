package net.placemarkt;

class AddressFormatter {
  private final String countryCode;
  private final Boolean abbreviate;
  private final String outputFormat;

  AddressFormatter(String countryCode, Boolean abbreviate, String outputFormat) {
    this.countryCode = countryCode;
    this.abbreviate = abbreviate;
    this.outputFormat = outputFormat;
  }

  public String format(String json) {
    return "";
  }
}
