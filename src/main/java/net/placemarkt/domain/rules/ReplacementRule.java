package net.placemarkt.domain.rules;

public final class ReplacementRule {

  private final String component;
  private final String pattern;
  private final String replacement;

  public ReplacementRule(String component, String pattern, String replacement) {
    this.component = component;
    this.pattern = pattern;
    this.replacement = replacement;
  }

  public String component() {
    return component;
  }

  public String pattern() {
    return pattern;
  }

  public String replacement() {
    return replacement;
  }
}
