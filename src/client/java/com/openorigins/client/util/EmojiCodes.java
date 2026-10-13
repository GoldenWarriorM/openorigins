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

  private static long joinTick = -1;
  private static long lastHarvestTick = -1;
  private static boolean learnSent;

  public static synchronized void registerAuto() {
    net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register(
        (handler, sender, client) -> {
          joinTick = client.level == null ? 0 : client.level.getGameTime();
          learnSent = false;
        });
    net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(
        client -> {
          if (client.level == null || client.player == null || joinTick < 0) return;
          long now = client.level.getGameTime();
          // Bulk-learn unlearned codes via one DM to self, once per join and
          // once more shortly after new codes arrive. Debt guard stops retries
          // when the server doesn't convert DMs.
          if (!learnSent && ((now - joinTick > 200 && now - joinTick < 900)
              || (lastHarvestTick >= 0 && now - lastHarvestTick > 100
                  && now - lastHarvestTick < 900))) {
            learnSent = true;
            lastHarvestTick = -1;
            bulkLearn(client);
          }
        });
  }

  private static synchronized void bulkLearn(net.minecraft.client.Minecraft client) {
    List<String> todo = new ArrayList<>();
    for (String code : codeToChar.keySet()) {
      String c = codeToChar.get(code);
      if (c == null || c.isEmpty()) todo.add(code);
    }
    if (todo.isEmpty()) return;
    int debt = debt();
    if (debt == todo.size()) return; // server doesn't convert DMs; don't spam
    setDebt(todo.size());
    String self;
    try {
      self = client.player.getName().getString();
    } catch (Throwable t) {
      return;
    }
    StringBuilder sb = new StringBuilder();
    for (String code : todo) {
      String tok = ":" + code + ": ";
      if (sb.length() + tok.length() > 200) {
        sendDm(client, self, sb.toString().trim());
        sb.setLength(0);
      }
      sb.append(tok);
    }
    if (sb.length() > 0) sendDm(client, self, sb.toString().trim());
    DebugLog.info("[emoji] bulk learn sent (" + todo.size() + " codes)");
  }

  private static void sendDm(net.minecraft.client.Minecraft client, String self, String body) {
    try {
      client.getConnection().sendCommand("msg " + self + " " + body);
    } catch (Throwable ignored) {
    }
  }

  private static int debt() {
    try {
      String v = propsCache().getProperty("_debt", "-1");
      return Integer.parseInt(v.trim());
    } catch (Throwable t) {
      return -1;
    }
  }

  private static void setDebt(int n) {
    try {
      Path p = path();
      Properties props = new Properties();
      if (Files.exists(p)) {
        try (InputStream in = Files.newInputStream(p)) {
          props.load(in);
        }
      }
      props.setProperty("_debt", Integer.toString(n));
      try (OutputStream out = Files.newOutputStream(p)) {
        props.store(out, "OpenOrigins emoji: code=glyph.");
      }
    } catch (IOException ignored) {
    }
  }

  private static Properties propsCache() {
    Properties props = new Properties();
    try {
      Path p = path();
      if (Files.exists(p)) {
        try (InputStream in = Files.newInputStream(p)) {
          props.load(in);
        }
      }
    } catch (IOException ignored) {
    }
    return props;
  }

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
        if (k.startsWith("_")) continue;
        String code = k.trim().toLowerCase(java.util.Locale.ROOT);
        if (CODE.matcher(":" + code + ":").matches()) {
          codeToChar.put(code, props.getProperty(k, "").trim());
        }
      }
    } catch (IOException ignored) {
    }
    for (String s : SEEDS) codeToChar.putIfAbsent(s, "");
    // One-time cleanup (marker _migrated=1): unlearned non-seed codes harvested
    // before the leading-colon rule (mostly player nicknames) are dropped;
    // correct ones re-harvest on the next Tab. Later manual additions stay.
    if (!"1".equals(props.getProperty("_migrated"))) {
      int dropped = 0;
      java.util.Iterator<Map.Entry<String, String>> it = codeToChar.entrySet().iterator();
      while (it.hasNext()) {
        Map.Entry<String, String> e = it.next();
        if ((e.getValue() == null || e.getValue().isEmpty()) && !SEEDS.contains(e.getKey())) {
          it.remove();
          dropped++;
        }
      }
      props.setProperty("_migrated", "1");
      try (OutputStream out2 = Files.newOutputStream(p)) {
        props.store(out2, "OpenOrigins emoji: code=glyph.");
      } catch (IOException ignored) {
      }
      if (dropped > 0) DebugLog.info("[emoji] dropped " + dropped + " junk codes");
      save();
    }
  }

  public static synchronized void save() {
    Properties props = new Properties();
    props.setProperty("_migrated", "1");
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

  private EmojiCodes() {}

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
      if (!s.startsWith(":")) continue;
      if (probe.length() < 200) {
        if (probe.length() > 0) probe.append(' ');
        probe.append(s);
      }
      s = s.substring(1);
      if (s.endsWith(":") && s.length() > 1) s = s.substring(0, s.length() - 1);
      if (CODE.matcher(":" + s + ":").matches() && addCodeNoSave(s)) n++;
    }
    if (probe.length() > 0) {
      DebugLog.info("[emoji] suggest probe: " + probe + " -> +" + n);
    }
    if (n > 0) {
      save();
      try {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc != null && mc.level != null) lastHarvestTick = mc.level.getGameTime();
      } catch (Throwable ignored) {
      }
    }
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
   * Align our sent text against an incoming line by surrounding context: the
   * server adds rank/name affixes, so only the neighbourhood of the :code:
   * has to match, with 1-2 private-use chars in place of the code.
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
        String pre = sent.substring(Math.max(0, a - 12), a);
        String post = sent.substring(b, Math.min(sent.length(), b + 12));
        int from = 0;
        boolean done = false;
        while (!done && (from = text.indexOf(pre, from)) >= 0) {
          int g0 = from + pre.length();
          for (int len = 1; len <= 2 && !done; len++) {
            if (g0 + len + post.length() > text.length()) continue;
            if (!text.startsWith(post, g0 + len)) continue;
            String glyph = text.substring(g0, g0 + len);
            if (!isPua(glyph)) continue;
            codeToChar.put(code, glyph);
            save();
            pendingSent.remove(sent);
            DebugLog.info("[emoji] learned :" + code + ": -> glyph");
            done = true;
          }
          from++;
        }
        if (done) return;
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
