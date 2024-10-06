package net.placemarkt;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Iterator;
import java.io.IOException;
import java.io.File;


enum Template {
  WORLDWIDE("worldwide.json"),
  COUNTRY_NAMES("countryNames.json"),
  ALIASES("aliases.json"),
  ABBREVIATIONS("abbreviations.json"),
  COUNTRY_2_LANG("country2Lang.json"),
  COUNTY_CODES("countyCodes.json"),
  STATE_CODES("stateCodes.json");

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

  /* 
    TODO: Isolate this dependency on JsonNode 
  */
  private static JsonNode setData(String fileName) {
    JsonNode node = null;
    try {
      node = DataMapper.Helpers.jsonReader.readTree(new File("input.json"));
    } catch (IOException e) {
      e.printStackTrace();
    }

    return node;
  }
}
