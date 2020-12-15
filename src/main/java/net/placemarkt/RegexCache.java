package net.placemarkt;

import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;

public class RegexCache {
  private Map<String, Pattern> map = new HashMap<>();

  public Pattern get(Object key) {
    if (map.containsKey(key)) {
      return map.get(key);
    } else {
      Pattern newPattern = Pattern.compile((String) key);
      map.put((String) key, newPattern);
      return newPattern;
    }
  }
}
