package net.placemarkt.application;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.placemarkt.domain.rules.PatternReplacement;
import net.placemarkt.domain.rules.TemplateDefinition;

final class TemplateRenderer {

  private static final Pattern FIRST_BLOCK = Pattern.compile("\\{\\{#first}}(.*?)\\{\\{/first}}", Pattern.DOTALL);
  private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\{\\s*([\\w_]+)\\s*}}}");

  String render(TemplateDefinition template, Map<String, String> components, List<PatternReplacement> postFormatRules) {
    StringBuilder builder = new StringBuilder();
    for (String line : template.source().split("\\r?\\n")) {
      String rendered = renderLine(line, components);
      if (!rendered.trim().isEmpty()) {
        builder.append(rendered).append('\n');
      }
    }
    String output = cleanup(builder.toString());
    for (PatternReplacement rule : postFormatRules) {
      output = Pattern.compile(rule.pattern(), Pattern.UNICODE_CHARACTER_CLASS).matcher(output).replaceAll(rule.replacement());
      output = cleanup(output);
    }
    return output.trim() + "\n";
  }

  private String renderLine(String line, Map<String, String> components) {
    String withFirsts = replaceFirstBlocks(line, components);
    String withVariables = replaceVariables(withFirsts, components);
    return cleanupLine(withVariables);
  }

  private String replaceFirstBlocks(String line, Map<String, String> components) {
    Matcher matcher = FIRST_BLOCK.matcher(line);
    StringBuffer buffer = new StringBuffer();
    while (matcher.find()) {
      String replacement = chooseFirst(matcher.group(1), components);
      matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(buffer);
    return buffer.toString();
  }

  private String chooseFirst(String inner, Map<String, String> components) {
    for (String option : inner.split("\\s*\\|\\|\\s*")) {
      String rendered = cleanupLine(replaceVariables(option, components));
      if (!rendered.isEmpty()) {
        return rendered;
      }
    }
    return "";
  }

  private String replaceVariables(String value, Map<String, String> components) {
    Matcher matcher = VARIABLE.matcher(value);
    StringBuffer buffer = new StringBuffer();
    while (matcher.find()) {
      String replacement = components.getOrDefault(matcher.group(1), "");
      matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(buffer);
    return buffer.toString();
  }

  private String cleanup(String rendered) {
    String current = rendered;
    String previous;
    do {
      previous = current;
      current = current.replaceAll("[\\},\\s]+$", "");
      current = current.replaceAll("^[,\\s]+", "");
      current = current.replaceAll("^- ", "");
      current = current.replaceAll(",\\s*,", ", ");
      current = current.replaceAll("[ \\t]+,[ \\t]+", ", ");
      current = current.replaceAll("[ \\t][ \\t]+", " ");
      current = current.replaceAll("[ \\t]\\n", "\n");
      current = current.replaceAll("\\n,", "\n");
      current = current.replaceAll(",+", ",");
      current = current.replaceAll(",\\n", "\n");
      current = current.replaceAll("\\n[ \\t]+", "\n");
      current = current.replaceAll("\\n+", "\n");
      current = dedupe(current);
    } while (!current.equals(previous));
    return current;
  }

  private String cleanupLine(String value) {
    return value
        .replaceAll("[ \\t]+", " ")
        .replaceAll("\\s+,", ",")
        .replaceAll(",\\s*$", "")
        .replaceAll("^\\s+", "")
        .replaceAll("\\s+$", "");
  }

  private String dedupe(String rendered) {
    List<String> lines = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    for (String rawLine : rendered.split("\\r?\\n")) {
      String line = Arrays.stream(rawLine.trim().split(", "))
          .map(String::trim)
          .filter(part -> !part.isEmpty())
          .distinct()
          .collect(Collectors.joining(", "));
      if (!line.isEmpty() && seen.add(line)) {
        lines.add(line);
      }
    }
    return String.join("\n", lines);
  }
}
