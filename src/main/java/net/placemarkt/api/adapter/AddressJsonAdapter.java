package net.placemarkt.api.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import net.placemarkt.api.Address;

public final class AddressJsonAdapter {

  private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<LinkedHashMap<String, Object>>() {};
  private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

  public Address fromJson(String json) throws IOException {
    Map<String, Object> components = mapper.readValue(json, MAP_TYPE);
    return Address.from(components);
  }
}
