package net.placemarkt.api;

public final class FormattingOptions {

  private final boolean abbreviate;
  private final boolean appendCountry;

  private FormattingOptions(Builder builder) {
    this.abbreviate = builder.abbreviate;
    this.appendCountry = builder.appendCountry;
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean abbreviate() {
    return abbreviate;
  }

  public boolean appendCountry() {
    return appendCountry;
  }

  public static final class Builder {
    private boolean abbreviate;
    private boolean appendCountry;

    public Builder abbreviate(boolean abbreviate) {
      this.abbreviate = abbreviate;
      return this;
    }

    public Builder appendCountry(boolean appendCountry) {
      this.appendCountry = appendCountry;
      return this;
    }

    public FormattingOptions build() {
      return new FormattingOptions(this);
    }
  }
}
