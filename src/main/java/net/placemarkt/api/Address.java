package net.placemarkt.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Address {

  private final Map<String, Object> components;

  private Address(Map<String, Object> components) {
    this.components = Collections.unmodifiableMap(new LinkedHashMap<>(components));
  }

  public static Address from(Map<String, Object> components) {
    return new Address(components);
  }

  public static Builder builder() {
    return new Builder();
  }

  public Map<String, Object> components() {
    return components;
  }

  public static final class Builder {
    private final Map<String, Object> components = new LinkedHashMap<>();

    public Builder put(String key, Object value) {
      components.put(key, value);
      return this;
    }

    public Address build() {
      return new Address(components);
    }
  }
}
