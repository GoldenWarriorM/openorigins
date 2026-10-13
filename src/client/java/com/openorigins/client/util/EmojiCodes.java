package com.openorigins.client.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Server emoji codes (:angry:) and learned code -&gt; glyph mappings.
 * The code list lives server-side; we ship seeds, learn glyphs from our own
 * echoed messages, and persist everything to openorigins-emoji.properties
 * (user-editable: one {@code code=char} per line).
 */
public final class EmojiCodes {
  private static final Pattern CODE = Pattern.compile(":([a-z0-9_]{2,32}):");
  private static final List<String> SEEDS = List.of("angry", "pog", "kekw", "kekwhyper", "wut");

  private static final Map<String, String> codeToChar = new LinkedHashMap<>();
  private static final Deque<String> pendingSent = new ArrayDeque<>();

  private EmojiCodes() {}

  public static synchronized void load() {
    for (String s : SEEDS) codeToChar.putIfAbsent(s, "");
    Path p = path();
    if (!Files.exists(p)) {
      save();
      return;
    }
    Properties props = new Properties();
    try (InputStream in = Files.newInputStream(p)) {
      props.load(in);
      for (String k : props.stringPropertyNames()) {
        String code = k.trim().toLowerCase(java.util.Locale.ROOT);
        if (CODE.matcher(":" + code + ":").matches()) {
          codeToChar.put(code, props.getProperty(k, "").trim());
        }
      }
    } catch (IOException ignored) {
    }
    for (String s : SEEDS) codeToChar.putIfAbsent(s, "");
  }

  public static synchronized void save() {
    Properties props = new Properties();
    for (Map.Entry<String, String> e : codeToChar.entrySet()) {
      props.setProperty(e.getKey(), e.getValue());
    }
    try {
      Files.createDirectories(path().getParent());
      try (OutputStream out = Files.newOutputStream(path())) {
        props.store(out, "OpenOrigins emoji: code=glyph. Add codes as code= (empty) and use them once to learn the glyph.");
      }
    } catch (IOException ignored) {
    }
  }

  private static Path path() {
    return FabricLoader.getInstance().getConfigDir().resolve("openorigins-emoji.properties");
  }

  public static synchronized List<String> codes() {
    return new ArrayList<>(codeToChar.keySet());
  }

  public static synchronized boolean addCode(String code) {
    String c = code.trim().toLowerCase(java.util.Locale.ROOT);
    if (!CODE.matcher(":" + c + ":").matches() || codeToChar.containsKey(c)) return false;
    codeToChar.put(c, "");
    save();
    return true;
  }

  /** Harvest :code: entries from brigadier suggestion lists (rank-dependent). */
  public static synchronized int harvestTexts(java.util.List<String> texts) {
    int n = 0;
    StringBuilder probe = new StringBuilder();
    for (String t : texts) {
      String s = t.trim();
      if (s.startsWith(":")) {
        if (probe.length() < 200) {
          if (probe.length() > 0) probe.append(' ');
          probe.append(s);
        }
        s = s.substring(1);
      }
      if (s.endsWith(":") && s.length() > 1) s = s.substring(0, s.length() - 1);
      if (CODE.matcher(":" + s + ":").matches() && addCodeNoSave(s)) n++;
    }
    if (probe.length() > 0) {
      DebugLog.info("[emoji] suggest probe: " + probe + " -> +" + n);
    }
    if (n > 0) save();
    return n;
  }

  private static boolean addCodeNoSave(String c) {
    if (codeToChar.containsKey(c)) return false;
    codeToChar.put(c, "");
    return true;
  }

  public static synchronized String charFor(String code) {
    String c = codeToChar.get(code);
    return c == null || c.isEmpty() ? null : c;
  }

  public static synchronized List<String> findCodes(String text) {
    List<String> out = new ArrayList<>();
    Matcher m = CODE.matcher(text);
    while (m.find()) out.add(m.group(1));
    return out;
  }

  /** Replace mapped :code: with glyphs; unknown codes stay as text. */
  public static synchronized String replaceKnown(String text) {
    Matcher m = CODE.matcher(text);
    StringBuffer sb = new StringBuffer();
    while (m.find()) {
      String c = codeToChar.get(m.group(1));
      m.appendReplacement(sb, c == null || c.isEmpty()
          ? Matcher.quoteReplacement(m.group(0)) : Matcher.quoteReplacement(c));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  public static synchronized void noteSent(String text) {
    List<String> codes = findCodes(text);
    if (codes.isEmpty()) return;
    pendingSent.addLast(text);
    while (pendingSent.size() > 5) pendingSent.removeFirst();
  }

  /**
   * Align our sent text against an incoming line: if they differ only by one
   * :code: span replaced with 1-2 private-use chars, the mapping is learned.
   */
  public static synchronized void learnFromIncoming(String text) {
    if (text == null || text.isEmpty() || pendingSent.isEmpty()) return;
    boolean hasPua = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c >= 0xE000 && c <= 0xF8FF) {
        hasPua = true;
        break;
      }
    }
    if (!hasPua) return;
    for (String sent : new ArrayList<>(pendingSent)) {
      Matcher m = CODE.matcher(sent);
      while (m.find()) {
        String code = m.group(1);
        String known = codeToChar.get(code);
        if (known != null && !known.isEmpty()) continue;
        int a = m.start();
        int b = m.end();
        for (int len = 1; len <= 2; len++) {
          if (a + len > text.length()) continue;
          String cand = sent.substring(0, a) + text.substring(a, a + len)
              + sent.substring(b);
          if (!cand.equals(text)) continue;
          String glyph = text.substring(a, a + len);
          if (!isPua(glyph)) continue;
          codeToChar.put(code, glyph);
          save();
          pendingSent.remove(sent);
          DebugLog.info("[emoji] learned :" + code + ": -> glyph");
          return;
        }
      }
    }
  }

  private static boolean isPua(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c < 0xE000 || c > 0xF8FF) return false;
    }
    return !s.isEmpty();
  }
}
