package com.openorigins.client.util;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure home-menu text rules: rank capacity, cache keys and world colours. */
public final class HomeParse {
  private HomeParse() {}

  private static final Pattern CAPACITY = Pattern.compile(
      "(\\d+)\\s*(?:x\\s*)?(?:home|house)?\\s*slots?|(?:home|house)s?\\s*slots?\\s*[:x]?\\s*(\\d+)");

  private static final String[] WORLD_COLORS =
      {"blue", "green", "yellow", "red", "orange", "purple"};

  /** Parses lines like "5 home slots" / "Home slots: 5" from rank perk lore. */
  public static int parseCapacity(String line) {
    String s = line == null ? "" : line.toLowerCase(Locale.ROOT);
    if (!(s.contains("home") || s.contains("slot"))) {
      return 0;
    }
    Matcher m = CAPACITY.matcher(s);
    if (m.find()) {
      String num = m.group(1) != null ? m.group(1) : m.group(2);
      try {
        int v = Integer.parseInt(num);
        return (v > 0 && v <= 100) ? v : 0;
      } catch (NumberFormatException ignored) {
      }
    }
    return 0;
  }

  /** Cache key for a home name: lowercase, no spaces/underscores/controls. */
  public static String key(String name) {
    if (name == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    String lower = name.toLowerCase(Locale.ROOT);
    for (int i = 0; i < lower.length(); i++) {
      char c = lower.charAt(i);
      if (c != ' ' && c != '_' && !Character.isISOControl(c)) {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  /** First known world colour on the "World:" lore line, or null. */
  public static String worldColor(List<String> lines) {
    for (String line : lines) {
      String s = line == null ? "" : line.toLowerCase(Locale.ROOT);
      if (s.contains("world:")) {
        for (String c : WORLD_COLORS) {
          if (s.contains(c)) {
            return c;
          }
        }
        return null;
      }
    }
    return null;
  }
}
