package net.placemarkt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized;
import org.junit.experimental.runners.Enclosed;
import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
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
      formatter = new AddressFormatter(false, false);
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
              String yaml = Transpiler.readFile(path.toString());
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
      formatter = new AddressFormatter(false, false);
      formatterWithAppendCountryFlag = new AddressFormatter(false, true);
      formatterWithAbbreviationFlag = new AddressFormatter(true, false);
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
      String json = "{city: 'Antwerp',\n"
          + "city_district: 'Antwerpen',\n"
          + "country: 'Belgium',\n"
          + "country_code: 'yu',\n"
          + "county: 'Antwerp',\n"
          + "house_number: 63,\n"
          + "neighbourhood: 'Sint-Andries',\n"
          + "postcode: 2000,\n"
          + "restaurant: 'Meat & Eat',\n"
          + "road: 'Vrijheidstraat',\n"
          + "state: 'Flanders'}";
      String formatted = formatter.format(json, "US");
      assertEquals(formatted, "Meat & Eat\n"
          + "63 Vrijheidstraat\n"
          + "Antwerp, Flanders 2000\n"
          + "Belgium\n");
    }

    @Test
    public void correctlyAppendsCountry() throws Exception {
      String json = "{houseNumber: '301',\n"
          + "road: 'Hamilton Avenue',\n"
          + "neighbourhood: 'Crescent Park',\n"
          + "city: 'Palo Alto',\n"
          + "postcode: '94303',\n"
          + "county: 'Santa Clara County',\n"
          + "state: 'California',\n"
          + "countryCode: 'US',}";
      String formatted = formatterWithAppendCountryFlag.format(json);
      assertEquals("301 Hamilton Avenue\n"
          + "Palo Alto, CA 94303\n"
          + "United States of America\n", formatted);
    }

    @Test
    public void correctlyAbbreviatesAvenue() throws Exception {
      String json = "{country_code: 'US',\n"
          + "house_number: '301',\n"
          + "road: 'Hamilton Avenue',\n"
          + "neighbourhood: 'Crescent Park',\n"
          + "city: 'Palo Alto',\n"
          + "postcode: '94303',\n"
          + "county: 'Santa Clara County',\n"
          + "state: 'California',\n"
          + "country: 'United States',}";
      String formatted = formatterWithAbbreviationFlag.format(json);
      assertEquals("301 Hamilton Ave\n"
          + "Palo Alto, CA 94303\n"
          + "United States of America\n", formatted);
    }

    @Test
    public void correctlyAbbreviatesRoad() throws Exception {
      String json = "{country_code: 'US',\n"
          + "house_number: '301',\n"
          + "road: 'Northwestern University Road',\n"
          + "neighbourhood: 'Crescent Park',\n"
          + "city: 'Palo Alto',\n"
          + "postcode: '94303',\n"
          + "county: 'Santa Clara County',\n"
          + "state: 'California',\n"
          + "country: 'United States'}";
      String formatted = formatterWithAbbreviationFlag.format(json);
      assertEquals("301 Northwestern University Rd\n"
          + "Palo Alto, CA 94303\n"
          + "United States of America\n", formatted);
    }

    @Test
    public void doesNotAbbreviateWhenComponentNotPresent() throws Exception {
      String json = "{country_code: 'US',\n"
          + "house_number: '301',\n"
          + "neighbourhood: 'Crescent Park',\n"
          + "city: 'Palo Alto',\n"
          + "postcode: '94303',\n"
          + "county: 'Santa Clara County',\n"
          + "state: 'California',\n"
          + "country: 'United States'}";
      String formatted = formatterWithAbbreviationFlag.format(json);
      assertEquals("301\n"
          + "Palo Alto, CA 94303\n"
          + "United States of America\n", formatted);
    }

    @Test
    public void correctlyAbbreviatesCAAvenue() throws Exception {
      String json = "{city: 'Vancouver',\n"
          + "country: 'Canada',\n"
          + "country_code: 'ca',\n"
          + "county: 'Greater Vancouver Regional District',\n"
          + "postcode: 'V6K',\n"
          + "road: 'Cornwall Avenue',\n"
          + "state: 'British Columbia',\n"
          + "suburb: 'Kitsilano',}";
      String formatted = formatterWithAbbreviationFlag.format(json);
      assertEquals("Cornwall Ave\n"
          + "Vancouver, BC V6K\n"
          + "Canada\n", formatted);
    }

    @Test
    public void correctlyAbbreviatesESCarrer() throws Exception {
      String json = "{city: 'Barcelona',\n"
          + "city_district: 'Sarrià - Sant Gervasi',\n"
          + "country: 'Spain',\n"
          + "country_code: 'es',\n"
          + "county: 'BCN',\n"
          + "house_number: '68',\n"
          + "neighbourhood: 'Sant Gervasi',\n"
          + "postcode: '08017',\n"
          + "road: 'Carrer de Calatrava',\n"
          + "state: 'Catalonia',\n"
          + "suburb: 'les Tres Torres'}";
      String formatted = formatterWithAbbreviationFlag.format(json);
      assertEquals("C Calatrava, 68\n"
          + "08017 Barcelona\n"
          + "Spain\n", formatted);
    }

    @Test
    public void doesNotAddHyphenWhenCountryIsIndiaWithNoCountryCode() throws IOException {
      String json = "{country_code: 'IN',\n"
          + "road: 'Baner',\n"
          + "city: 'Pune',\n"
          + "state: 'Maharashtra',\n"
          + "country: 'India',}";
      String formatted = formatter.format(json);
      assertEquals("Baner\nPune\nMaharashtra\nIndia\n", formatted);
    }
  }
}
