package net.placemarkt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.placemarkt.AddressFormatter.OutputType;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized;
import org.junit.experimental.runners.Enclosed;
import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Stream;


@RunWith(Enclosed.class)
public class AddressFormatterTest {

  @RunWith(Parameterized.class)
  public static class ParameterizedAddressFormatterTest {
    final private String components;
    final private String address;
    final private String description;
    static AddressFormatter formatter;

    @BeforeClass
    public static void setup() {
      formatter = new AddressFormatter(OutputType.STRING, false, false);
    }

    public ParameterizedAddressFormatterTest(String components, String address, String description) {
      super();
      this.components = components;
      this.address = address;
      this.description = description;
    }

    @Parameters(name = "{2}")
    public static Collection<String[]> addresses() {
      ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
      ObjectMapper jsonWriter = new ObjectMapper();
      Collection<String[]> dict = new ArrayList<>();
      try {
        try(Stream<Path> paths = Files.list(Paths.get("address-formatting/testcases/countries"))) {
          paths.forEach(path -> {
            try {
              String yaml = Files.readString(path);
              Object obj = yamlReader.readValue(yaml, Object.class);
              ObjectNode node = jsonWriter.valueToTree(obj);
              String components = node.get("components").toString();
              String expected = node.get("expected").textValue();
              String description = node.get("description").toString();
              dict.add(new String[] {components, expected, description});
            } catch (IOException e) {
              e.printStackTrace();
            }
          });
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
      return dict;
    }

    @Test
    public void worksWithAddressesWorldwide() throws Exception {
      String formatted = formatter.format(this.components);
      Assert.assertEquals(this.address, formatted);
    }
  }

  public static class SingleTests {

    static AddressFormatter formatter;
    static AddressFormatter formatterWithAppendCountryFlag;
    static AddressFormatter formatterWithAbbreviationFlag;

    @BeforeClass
    public static void setup() {
      formatter = new AddressFormatter(OutputType.STRING, false, false);
      formatterWithAppendCountryFlag = new AddressFormatter(OutputType.STRING, false, true);
      formatterWithAbbreviationFlag = new AddressFormatter(OutputType.STRING, true, false);
    }

    @Test
    public void dealsWithEmptyStringCorrectly() {
      String json = "";
      IOException error = assertThrows(IOException.class, () -> {
        String formatted = formatter.format(json);
      });

      assertEquals("Json processing exception", error.getMessage());
    }

    @Test
    public void dealsWithImproperlyFormatterJsonCorrectly() {
      String json = "{";
      IOException error = assertThrows(IOException.class, () -> {
        String formatted = formatter.format(json);
      });

      assertEquals("Json processing exception", error.getMessage());
    }

    @Test
    public void correctlySetsFallbackCountryCode() throws Exception {
      String json = "{city: 'Antwerp',"
          + "city_district: 'Antwerpen',"
          + "country: 'Belgium',"
          + "country_code: 'yu',"
          + "county: 'Antwerp',"
          + "house_number: 63,"
          + "neighbourhood: 'Sint-Andries',"
          + "postcode: 2000,"
          + "restaurant: 'Meat & Eat',"
          + "road: 'Vrijheidstraat',"
          + "state: 'Flanders'}";
      String formatted = formatter.format(json, "US");
      assertEquals(formatted, "Meat & Eat\n"
          + "63 Vrijheidstraat\n"
          + "Antwerp, Flanders 2000\n"
          + "Belgium\n");
    }

    @Test
    public void correctlyAppendsCountry() throws Exception {
      String json = "{\"houseNumber\": 301,\n"
          + "  \"road\": \"Hamilton Avenue\",\n"
          + "  \"neighbourhood\": \"Crescent Park\",\n"
          + "  \"city\": \"Palo Alto\",\n"
          + "  \"postcode\": 94303,\n"
          + "  \"county\": \"Santa Clara County\",\n"
          + "  \"state\": \"California\",\n"
          + "  \"countryCode\": \"US\",}";
      String formatted = formatterWithAppendCountryFlag.format(json);
      assertEquals("301 Hamilton Avenue\n"
          + "Palo Alto, CA 94303\n"
          + "United States of America\n", formatted);
    }

    @Test
    public void correctlyAbbreviatesAddresses() throws Exception {
      String json = "{\"houseNumber\": 301,\n"
          + "  \"road\": \"Hamilton Avenue\",\n"
          + "  \"neighbourhood\": \"Crescent Park\",\n"
          + "  \"city\": \"Palo Alto\",\n"
          + "  \"postcode\": 94303,\n"
          + "  \"county\": \"Santa Clara County\",\n"
          + "  \"state\": \"California\",\n"
          + "  \"countryCode\": \"US\",}";
      String formatted = formatterWithAbbreviationFlag.format(json);
      assertEquals("301 Hamilton Avenue\n"
          + "Palo Alto, CA 94303\n"
          + "United States of America\n", formatted);
    }
  }
}
