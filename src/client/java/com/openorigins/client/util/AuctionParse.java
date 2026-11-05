package com.openorigins.client.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure auction-text parsers: lore folding, lot identity, rarity badges and
 * price statistics. No game types, so the rules are testable against captured
 * menu strings.
 */
public final class AuctionParse {
  private AuctionParse() {}

  /** Weighted average and median over {unit price, lot weight} pairs. */
  public record Aggregate(double avg, double med) {}

  /** Server badge glyph (name prefix) per rarity. */
  public static final Map<String, String> RARITY_GLYPHS = Map.of(
      "LIMITED", "ꑶ",
      "COMMON", "ꑲ",
      "RARE", "ꑳ",
      "EPIC", "ꑴ",
      "LEGENDARY", "ꑵ");

  private static final Map<String, String> GLYPH_RARITY = Map.of(
      "ꑶ", "LIMITED",
      "ꑲ", "COMMON",
      "ꑳ", "RARE",
      "ꑴ", "EPIC",
      "ꑵ", "LEGENDARY");

  private static final Pattern TIME_LEFT_NUM =
      Pattern.compile("(\\d+)\\s*(day|hour|min)", Pattern.CASE_INSENSITIVE);

  private static final Pattern PRICE =
      Pattern.compile("(?i)^price:\\s*([\\d,]+(?:\\.\\d+)?)");

  /** "Recently sold" rows in the same menu carry a "Sold: … ago" line. */
  private static final Pattern SOLD_LINE = Pattern.compile("(?i)^sold:\\s");

  /** Lore lines that describe the listing itself, never the item. */
  private static final List<String> NOISE_CONTAINS = List.of(
      "price:", "seller:", "time left:", "created:", "world:", "position:",
      "click", "visit realm", "pin realm", "players:", "delivered", "expired", "reset",
      "currently viewing", "auction sold", "sold:");

  /** Rarity from the server badge prefix; plain names get their own bucket. */
  public static String rarityOf(String name) {
    if (name != null && !name.isEmpty()) {
      String g = GLYPH_RARITY.get(name.substring(0, 1));
      if (g != null) {
        return g;
      }
    }
    return "NONE";
  }

  /** Nick without the rank badge prefix ("… Zoltraq" -> "zoltraq"). */
  public static String sellerNick(String seller) {
    if (seller == null) {
      return "";
    }
    String s = seller.strip();
    int sp = s.lastIndexOf(' ');
    if (sp >= 0) {
      s = s.substring(sp + 1);
    }
    return s.toLowerCase(Locale.ROOT);
  }

  /** Listing end instant from "Time left: 17 hours and 50 minutes", else -1. */
  public static long parseEndsAt(List<String> lines, long now) {
    try {
      for (String line : lines) {
        if (line == null) {
          continue;
        }
        String s = line.strip().toLowerCase(Locale.ROOT);
        if (!s.startsWith("time left:")) {
          continue;
        }
        Matcher m = TIME_LEFT_NUM.matcher(s);
        long ms = 0;
        boolean any = false;
        while (m.find()) {
          long v = Long.parseLong(m.group(1));
          String unit = m.group(2).toLowerCase(Locale.ROOT);
          if (unit.startsWith("day")) {
            ms += v * 24L * 60 * 60 * 1000;
          } else if (unit.startsWith("hour")) {
            ms += v * 60L * 60 * 1000;
          } else {
            ms += v * 60L * 1000;
          }
          any = true;
        }
        if (any && ms > 0) {
          return now + ms;
        }
      }
    } catch (Throwable ignored) {
    }
    return -1;
  }

  /** True when a lore line is a "Sold: … ago" marker. */
  public static boolean isSoldRow(List<String> lines) {
    for (String line : lines) {
      if (line != null && SOLD_LINE.matcher(line.strip()).find()) {
        return true;
      }
    }
    return false;
  }

  /** Stored description of a recently-sold row (legacy polluted entries). */
  public static boolean isSoldDesc(String desc) {
    if (desc == null || desc.isEmpty()) {
      return false;
    }
    for (String line : desc.split("\n")) {
      if (SOLD_LINE.matcher(line.strip()).find()) {
        return true;
      }
    }
    return false;
  }

  /** Parsed total price from a "Price: 1,234.5" line, or null. */
  public static Double parsePrice(String line) {
    if (line == null) {
      return null;
    }
    Matcher m = PRICE.matcher(line.strip());
    if (!m.find()) {
      return null;
    }
    try {
      return Double.parseDouble(m.group(1).replace(",", ""));
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  /** Whole units drop decimals, fractions keep two: 1,234 / 1,234.57. */
  public static String formatUnit(double unit) {
    if (unit == Math.floor(unit) && !Double.isInfinite(unit)) {
      return String.format(Locale.US, "%,d", (long) unit);
    }
    return String.format(Locale.US, "%,.2f", unit);
  }

  /** Normalized display name without the server badge prefix. */
  public static String normName(String name) {
    if (name == null) {
      return "";
    }
    String s = name.strip();
    while (!s.isEmpty() && s.codePointAt(0) > 0x2500) {
      s = s.substring(Character.charCount(s.codePointAt(0))).strip();
    }
    return s.toLowerCase(Locale.ROOT);
  }

  /**
   * Identity signals from the full description: normalized name plus the
   * first descriptive line and a hash of all of them. Returns {suffix, label,
   * full description}.
   */
  public static String[] variantOf(String name, List<String> loreLines) {
    List<String> desc = new ArrayList<>();
    boolean enchanted = false;
    for (int i = 0; i < loreLines.size(); i++) {
      String raw = loreLines.get(i);
      String s = raw == null ? "" : raw.strip();
      if (s.isEmpty()) {
        continue;
      }
      String lower = s.toLowerCase(Locale.ROOT);
      boolean noise = false;
      for (String n : NOISE_CONTAINS) {
        if (lower.contains(n)) {
          noise = true;
          break;
        }
      }
      if (noise) {
        continue;
      }
      // Enchant sockets and everything below are cut: only enchanted-vs-
      // clean matters (socket combos are unbounded).
      if (lower.contains("enchant slot")) {
        for (int j = i + 1; j < loreLines.size(); j++) {
          String rest = loreLines.get(j);
          String rs = rest == null ? "" : rest.strip();
          if (rs.startsWith("+") && !rs.toLowerCase(Locale.ROOT).contains("available slot")) {
            enchanted = true;
            break;
          }
        }
        break;
      }
      // Fill-state counters change over time ("Enchant Slots: (0/2)" vs
      // "(2/2)"): same item, not a new variant.
      s = s.replaceAll("\\(\\d+\\s*/\\s*\\d+\\)", "").replaceAll(" +", " ").strip();
      if (s.isEmpty() || !s.matches(".*[A-Za-z0-9].*")) {
        continue;
      }
      desc.add(s);
    }
    String nn = normName(name);
    String suffix = "";
    String label = "";
    if (!nn.isEmpty()) {
      suffix = "|n=" + nn;
    }
    if (!desc.isEmpty()) {
      try {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] hash = md.digest(String.join("\n", desc).getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < 5; i++) {
          hex.append(String.format("%02x", hash[i]));
        }
        suffix += "|v=" + hex;
        label = desc.get(0);
        if (label.length() > 30) {
          label = label.substring(0, 30) + "…";
        }
      } catch (Throwable ignored) {
      }
    }
    if (hasEnchantSection(loreLines)) {
      suffix += enchanted ? "|e=1" : "|e=0";
    }
    return new String[]{suffix, label, String.join("\n", desc)};
  }

  /** Volume-weighted average and median of already-filtered {unit, weight} pairs. */
  public static Aggregate aggregate(List<double[]> vals) {
    if (vals == null || vals.isEmpty()) {
      return null;
    }
    List<double[]> sorted = new ArrayList<>(vals);
    sorted.sort(Comparator.comparingDouble(a -> a[0]));
    long totalW = 0;
    double totalV = 0;
    for (double[] v : sorted) {
      long w = Math.max(1, (long) v[1]);
      totalW += w;
      totalV += v[0] * w;
    }
    if (totalW <= 0) {
      return null;
    }
    long half = (totalW + 1) / 2;
    long acc = 0;
    double med = sorted.get(sorted.size() - 1)[0];
    for (double[] v : sorted) {
      acc += Math.max(1, (long) v[1]);
      if (acc >= half) {
        med = v[0];
        break;
      }
    }
    return new Aggregate(totalV / totalW, med);
  }

  private static boolean hasEnchantSection(List<String> loreLines) {
    for (String line : loreLines) {
      if (line != null && line.toLowerCase(Locale.ROOT).contains("enchant slot")) {
        return true;
      }
    }
    return false;
  }
}
