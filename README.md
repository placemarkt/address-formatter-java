# Address Formatter

## Overview

`address-formatter-java` formats postal addresses using the country-specific rules from the vendored [OpenCage address-formatting](https://github.com/OpenCageData/address-formatting) data set.

The current implementation is a clean-room rewrite with explicit separation between:

- domain rules
- bundled rule loading
- address adaptation
- formatting pipeline
- public API

It targets Java 8 and above.

## Installation

Add the dependency to your `pom.xml`:

```xml
<dependency>
  <groupId>net.placemarkt</groupId>
  <artifactId>address-formatter-java</artifactId>
  <version>0.0.12</version>
</dependency>
```

## APIs

There are two supported entrypoints.

### Compatibility Facade

This preserves the existing simple API used by the basis tests:

```java
import net.placemarkt.AddressFormatter;

AddressFormatter formatter = new AddressFormatter(false, false);
String formatted = formatter.format(json);
String formattedWithFallback = formatter.format(json, "US");
```

Supported methods:

```java
format(String json)
format(String json, String fallbackCountryCode)
format(Map<String, Object> components)
format(Map<String, Object> components, String fallbackCountryCode)
```

### Primary Typed API

The newer API separates the address payload from formatting options:

```java
import net.placemarkt.api.Address;
import net.placemarkt.api.AddressFormatterEngine;
import net.placemarkt.api.FormattingOptions;

FormattingOptions options = FormattingOptions.builder()
    .abbreviate(true)
    .appendCountry(false)
    .build();

AddressFormatterEngine engine = AddressFormatterEngine.standard(options);

Address address = Address.builder()
    .put("country_code", "US")
    .put("house_number", "301")
    .put("road", "Hamilton Avenue")
    .put("city", "Palo Alto")
    .put("postcode", "94303")
    .put("state", "California")
    .put("country", "United States")
    .build();

String formatted = engine.format(address);
```

## Examples

### Basic Formatting

```java
AddressFormatter formatter = new AddressFormatter(false, false);

String json = "{country_code: 'US',\n"
    + "house_number: '301',\n"
    + "road: 'Hamilton Avenue',\n"
    + "city: 'Palo Alto',\n"
    + "postcode: '94303',\n"
    + "state: 'California',\n"
    + "country: 'United States'}";

String formatted = formatter.format(json);
/*
301 Hamilton Avenue
Palo Alto, CA 94303
United States of America
*/
```

### Abbreviations

```java
AddressFormatter formatter = new AddressFormatter(true, false);

String formatted = formatter.format("{country_code: 'US', road: 'Hamilton Avenue', house_number: '301', city: 'Palo Alto', postcode: '94303', state: 'California', country: 'United States'}");
/*
301 Hamilton Ave
Palo Alto, CA 94303
United States of America
*/
```

### Fallback Country Code

```java
AddressFormatter formatter = new AddressFormatter(false, false);

String formatted = formatter.format("{city: 'Antwerp', road: 'Vrijheidstraat', house_number: 63, country: 'Belgium'}", "US");
```

The fallback country code takes precedence over an invalid or missing input `country_code`.

## Architecture

The source tree is organized by responsibility:

- `net.placemarkt.domain.rules`
  immutable rule models such as country profiles, template definitions, and replacement rules
- `net.placemarkt.infrastructure.rules`
  bundled YAML loading and translation into an in-memory rule catalog
- `net.placemarkt.application`
  canonicalization, country resolution, region enrichment, replacements, and rendering
- `net.placemarkt.api`
  typed public API
- `net.placemarkt`
  compatibility facade

The build now loads the vendored `address-formatting/conf` YAML resources directly. It does not depend on a compile-time transpiler step.

## Behavior

The formatter is intentionally data-driven:

- aliases such as `houseNumber` and `housenumber` are normalized
- country-specific templates and fallback templates are selected from the vendored rule data
- state and county codes are derived from bundled region-code tables
- optional abbreviations are applied by country-language mapping
- post-format cleanup removes duplicate lines and normalizes punctuation and whitespace

Current correctness is validated against the existing worldwide regression corpus under `address-formatting/testcases`.

## Development

Run the full test suite with:

```bash
mvn test
```

The tests exercise both focused unit behavior and the worldwide address-formatting corpus.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).

## Acknowledgements

- [OpenCage address-formatting](https://github.com/OpenCageData/address-formatting)
- [fragaria/address-formatter](https://github.com/fragaria/address-formatter)
