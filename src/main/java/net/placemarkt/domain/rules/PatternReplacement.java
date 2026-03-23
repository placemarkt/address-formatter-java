package net.placemarkt.domain.rules;

public final class PatternReplacement {

  private final String pattern;
  private final String replacement;

  public PatternReplacement(String pattern, String replacement) {
    this.pattern = pattern;
    this.replacement = replacement;
  }

  public String pattern() {
    return pattern;
  }

  public String replacement() {
    return replacement;
  }
}
