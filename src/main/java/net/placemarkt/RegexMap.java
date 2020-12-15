package net.placemarkt;

import java.util.Map;
import java.util.Set;
import java.util.Collection;
import java.util.HashMap;
import java.util.regex.Pattern;

public class RegexMap implements Map<String, Pattern> {
  private HashMap<String, Pattern> map = new HashMap<>();

  public Pattern put(String regex, Pattern pattern) {
    if (map.containsKey(regex)) {
      return map.get(regex);
    } else {
      map.put(regex, java.util.regex.Pattern.compile(regex));
    }

    return pattern;
  }

  @Override
  public Pattern get(Object key) {
    if (containsKey(key)) {
      return map.get(key);
    } else {
      Pattern newPattern = Pattern.compile((String) key);
      map.put((String) key, newPattern);
      return newPattern;
    }
  }

  @Override
  public Pattern remove(Object key) {
    return map.remove(key);
  }

  @Override
  public void putAll(Map<? extends String, ? extends Pattern> m) {
    map.putAll(m) ;
  }

  @Override
  public void clear() {
    map.clear();
  }

  @Override
  public int size() {
    return map.size();
  }

  @Override
  public boolean isEmpty() {
    return size() == 0 ? true : false;
  }

  @Override
  public boolean containsKey(Object key) {
    return map.containsKey(key);
  }

  @Override
  public boolean containsValue(Object object) {
    return map.containsValue(object);
  }

  @Override
  public Set<String> keySet() {
    return map.keySet();
  }

  @Override
  public Collection<Pattern> values() {
    return map.values();
  }

  @Override
  public Set<Map.Entry<String, Pattern>> entrySet() {
    return map.entrySet();
  }

}
