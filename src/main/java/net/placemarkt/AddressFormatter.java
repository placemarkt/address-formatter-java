package net.placemarkt;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.util.Map;
import net.placemarkt.api.Address;
import net.placemarkt.api.AddressFormatterEngine;
import net.placemarkt.api.FormattingOptions;
import net.placemarkt.api.adapter.AddressJsonAdapter;

public final class AddressFormatter {

  private final AddressFormatterEngine delegate;
  private final AddressJsonAdapter jsonAdapter;

  public AddressFormatter(Boolean abbreviate, Boolean appendCountry) {
    this.delegate = AddressFormatterEngine.standard(
        FormattingOptions.builder()
            .abbreviate(Boolean.TRUE.equals(abbreviate))
            .appendCountry(Boolean.TRUE.equals(appendCountry))
            .build());
    this.jsonAdapter = new AddressJsonAdapter();
  }

  public String format(String json) throws IOException {
    return format(json, null);
  }

  public String format(String json, String fallbackCountryCode) throws IOException {
    try {
      return delegate.format(jsonAdapter.fromJson(json), fallbackCountryCode);
    } catch (JsonProcessingException e) {
      throw new IOException("Json processing exception", e);
    }
  }

  public String format(Map<String, Object> components) throws IOException {
    return format(components, null);
  }

  public String format(Map<String, Object> components, String fallbackCountryCode) throws IOException {
    return delegate.format(Address.from(components), fallbackCountryCode);
  }
}
