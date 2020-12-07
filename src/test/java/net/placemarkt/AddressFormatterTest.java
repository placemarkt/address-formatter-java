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
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.junit.Assert;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Stream;

@RunWith(Parameterized.class)
public class AddressFormatterBuilderTest {
  final private String components;
  final private String address;
  final private String description;
  static AddressFormatterBuilder builder;
  static AddressFormatter formatter;

  @BeforeClass
  public static void setup() {
    formatter = new AddressFormatter();
  }

  public AddressFormatterBuilderTest(String components, String address, String description) {
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
            String expected = node.get("expected").toString();
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
  public void verifyAddressFormatting() throws Exception {
    String formatted = this.formatter.format(this.components);
    Assert.assertEquals(this.address, formatted);
  }
}
