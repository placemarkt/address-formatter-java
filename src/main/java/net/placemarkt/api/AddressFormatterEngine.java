package net.placemarkt.api;

import java.io.IOException;
import net.placemarkt.application.AddressFormattingService;
import net.placemarkt.infrastructure.rules.BundledRuleCatalogLoader;

public final class AddressFormatterEngine {

  private static final BundledRuleCatalogLoader LOADER = new BundledRuleCatalogLoader();
  private static final AddressFormattingService DEFAULT_SERVICE =
      new AddressFormattingService(LOADER.load());

  private final AddressFormattingService service;
  private final FormattingOptions options;

  private AddressFormatterEngine(AddressFormattingService service, FormattingOptions options) {
    this.service = service;
    this.options = options;
  }

  public static AddressFormatterEngine standard(FormattingOptions options) {
    return new AddressFormatterEngine(DEFAULT_SERVICE, options);
  }

  public String format(Address address) throws IOException {
    return format(address, null);
  }

  public String format(Address address, String fallbackCountryCode) throws IOException {
    return service.format(address, options, fallbackCountryCode);
  }
}
