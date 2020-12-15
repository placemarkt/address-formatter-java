package net.placemarkt;

import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;

public class RegexPatternCache {
  private Map<String, Pattern> map = new HashMap<>();

  public Pattern get(String key) {
    if (map.containsKey(key)) {
      return map.get(key);
    } else {
      Pattern newPattern = Pattern.compile(key);
      map.put(key, newPattern);
      return newPattern;
    }
  }

  public Pattern get(String key, int flags) {
    if (map.containsKey(key)) {
      return map.get(key);
    } else {
      Pattern newPattern = Pattern.compile(key, flags);
      map.put(key, newPattern);
      return newPattern;
    }
  }
}
