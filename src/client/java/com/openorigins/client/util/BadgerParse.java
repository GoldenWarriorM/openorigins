package com.openorigins.client.util;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure text parsers for the badger streaks menu. Kept free of game types so
 * the regex and timer rules can be tested without a running client; the menu
 * scanner in {@link BadgerCache} only supplies the strings.
 */
public final class BadgerParse {
  private BadgerParse() {}

  /** Quest stage, e.g. {@code Basic (2/5)} from a quest-item lore line. */
  public record Tier(String name, int done, int total) {}

  /** Number followed by a duration letter or the start of a duration word. */
  private static final Pattern TIME_PART = Pattern.compile("(\\d+)\\s*([dhms])");
  private static final Pattern SCORE = Pattern.compile("current streak score:\\s*(\\d+)");
  private static final Pattern HIGH = Pattern.compile("streak highscore:\\s*(\\d+)");
  private static final Pattern DAYS = Pattern.compile("days until streak resets:\\s*(\\d+)");
  private static final Pattern TIER = Pattern.compile("(.+?):\\s*\\((\\d+)/(\\d+)\\)");

  /** Parses "23 hours and 59 minutes" / "6h 55m" style timers to ms (0 on fail). */
  public static long parseTimeLeftMs(String raw) {
    String s = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
    long ms = 0;
    Matcher m = TIME_PART.matcher(s);
    while (m.find()) {
      long v = Long.parseLong(m.group(1));
      switch (m.group(2)) {
        case "d" -> ms += v * 86_400_000L;
        case "h" -> ms += v * 3_600_000L;
        case "m" -> ms += v * 60_000L;
        case "s" -> ms += v * 1_000L;
        default -> {}
      }
    }
    return ms;
  }

  /** Smallest parsed timer among "Time left" candidates (pairs ms/raw). */
  public static String minAvail(List<Long> ms, List<String> raw) {
    if (raw.isEmpty() || ms.isEmpty()) {
      return null;
    }
    int size = Math.min(ms.size(), raw.size());
    int best = 0;
    for (int i = 1; i < size; i++) {
      if (ms.get(i) < ms.get(best)) {
        best = i;
      }
    }
    return raw.get(best);
  }

  /** Smallest parsed timer among "Next available:" lore values. */
  public static String minRaw(List<String> raw) {
    if (raw.isEmpty()) {
      return null;
    }
    int best = 0;
    for (int i = 1; i < raw.size(); i++) {
      if (parseTimeLeftMs(raw.get(i)) < parseTimeLeftMs(raw.get(best))) {
        best = i;
      }
    }
    return raw.get(best);
  }

  /** Text after a lowercase prefix within a (lowercased, equal-length) line. */
  public static String timeValue(String lower, String prefix) {
    int i = lower.indexOf(prefix);
    return i < 0 ? "" : lower.substring(i + prefix.length()).trim();
  }

  /** Server-menu captions that leaked into REQUIRED before the sentinel
   *  fix; never legitimate badger task items. */
  public static boolean isMenuJunk(String name) {
    if (name == null || name.isBlank()) {
      return true;
    }
    String s = name.toLowerCase(Locale.ROOT).strip();
    return s.equals("your listings") || s.equals("refresh") || s.equals("shrink view")
        || s.equals("← back") || s.equals("back") || s.equals("events")
        || s.equals("your profile") || s.equals("badges") || s.equals("realms")
        || s.equals("social") || s.equals("close") || s.equals("learn more");
  }

  /** Streak score from a menu line, or null when the line does not carry it. */
  public static Integer parseScore(String line) {
    return firstInt(SCORE, line);
  }

  /** Streak highscore from a lore line, or null when absent. */
  public static Integer parseHighscore(String line) {
    return firstInt(HIGH, line);
  }

  /** Days until the streak resets, or null when absent. */
  public static Integer parseDaysUntilReset(String line) {
    return firstInt(DAYS, line);
  }

  /** Quest stage from a lore line like "Basic (2/5)", or null. */
  public static Tier parseTier(String line) {
    if (line == null) {
      return null;
    }
    Matcher m = TIER.matcher(line);
    if (!m.find()) {
      return null;
    }
    try {
      return new Tier(m.group(1).trim(), Integer.parseInt(m.group(2)),
          Integer.parseInt(m.group(3)));
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private static Integer firstInt(Pattern pattern, String line) {
    if (line == null) {
      return null;
    }
    Matcher m = pattern.matcher(line.toLowerCase(Locale.ROOT));
    if (!m.find()) {
      return null;
    }
    try {
      return Integer.parseInt(m.group(1));
    } catch (NumberFormatException ignored) {
      return null;
    }
  }
}
