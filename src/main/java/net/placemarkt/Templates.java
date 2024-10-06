package net.placemarkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Iterator;
import java.io.IOException;
import java.io.InputStream;

enum Template {
  WORLDWIDE("worldwide.json"),
  COUNTRY_NAMES("countryNames.json"),
  ALIASES("aliases.json"),
  ABBREVIATIONS("abbreviations.json"),
  COUNTRY_2_LANG("country2Lang.json"),
  COUNTY_CODES("countyCodes.json"),
  STATE_CODES("stateCodes.json");

  private interface Constants {
    ObjectMapper jsonWriter = new ObjectMapper();
  }

  private final JsonNode data;

  Template(String fileName) {
    this.data = setData(fileName);
  }

  public boolean has(String key) {
    return getData().has(key);
  }

  public JsonNode get(String key) {
    return getData().get(key);
  }

  public Iterator<JsonNode> elements() {
    return getData().elements();
  }

  private JsonNode getData() {
    return this.data;
  }

  private static JsonNode setData(String fileName) {
    JsonNode node = null;
    try {
      ClassLoader cl = Thread.currentThread().getContextClassLoader();
      InputStream is = cl.getResourceAsStream(fileName);
      node = Constants.jsonWriter.readTree(is);
    } catch (IOException e) {
      e.printStackTrace();
    }

    return node;
  }
}
