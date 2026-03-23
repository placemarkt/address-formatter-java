package net.placemarkt.domain.rules;

public final class TemplateDefinition {

  private final String source;

  public TemplateDefinition(String source) {
    this.source = source == null ? "" : source;
  }

  public String source() {
    return source;
  }
}
