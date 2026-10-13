package com.openorigins.client.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Server-emoji registry backed by config/openorigins/emoji.json:
 * {"emojis":[{"id":"angry","display":"&lt;glyph&gt;"}],"order":["angry",...]}.
 * Ids arrive from server Tab-suggestions, glyphs are learned from our own
 * echoed messages. Approach inspired by OriginAddons (MIT, ItsVerday),
 * reimplemented from scratch for current mappings.
 */
public final class OaEmojiStore {
  private static final Pattern CODE = Pattern.compile(":([a-z0-9_]{2,32}):");
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  private static final Map<String, String> display = new LinkedHashMap<>();
  private static final List<String> order = new ArrayList<>();
  private static final java.util.Set<String> unlocked = new java.util.HashSet<>();
  private static final Deque<String> pendingSent = new ArrayDeque<>();

  private OaEmojiStore() {}

  private static Path path() {
    return FabricLoader.getInstance().getConfigDir().resolve("openorigins/emoji.json");
  }

  public static synchronized void load() {
    seed("kekw", "\uB740");
    seed("kekwhyper", "\uB741");
    seed("pog", "\uB742");
    seed("wut", "\uB743");
    Path p = path();
    if (!Files.exists(p)) {
      save();
      return;
    }
    try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
      JsonObject root = GSON.fromJson(r, JsonObject.class);
      if (root == null) return;
      JsonArray arr = root.has("emojis") ? root.getAsJsonArray("emojis") : null;
      if (arr != null) {
        for (JsonElement e : arr) {
          if (!e.isJsonObject()) continue;
          JsonObject o = e.getAsJsonObject();
          if (!o.has("id")) continue;
          String id = o.get("id").getAsString().trim().toLowerCase(java.util.Locale.ROOT);
          if (!CODE.matcher(":" + id + ":").matches()) continue;
          String d = o.has("display") ? o.get("display").getAsString() : "";
          display.put(id, d == null ? "" : d);
        }
      }
      order.clear();
      JsonArray ord = root.has("order") ? root.getAsJsonArray("order") : null;
      if (ord != null) {
        for (JsonElement e : ord) {
          String id = e.getAsString().trim().toLowerCase(java.util.Locale.ROOT);
          if (display.containsKey(id) && !order.contains(id)) order.add(id);
        }
      }
      for (String id : display.keySet()) {
        if (!order.contains(id)) order.add(id);
      }
    } catch (IOException ignored) {
    }
  }

  private static void seed(String id, String glyph) {
    display.putIfAbsent(id, glyph);
    if (!order.contains(id)) order.add(id);
  }

  public static synchronized void save() {
    try {
      Files.createDirectories(path().getParent());
      JsonObject root = new JsonObject();
      JsonArray arr = new JsonArray();
      for (String id : order) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("display", display.getOrDefault(id, ""));
        arr.add(o);
      }
      for (String id : display.keySet()) {
        if (order.contains(id)) continue;
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("display", display.getOrDefault(id, ""));
        arr.add(o);
        order.add(id);
      }
      root.add("emojis", arr);
      JsonArray ord = new JsonArray();
      for (String id : order) ord.add(id);
      root.add("order", ord);
      try (Writer w = Files.newBufferedWriter(path(), StandardCharsets.UTF_8)) {
        GSON.toJson(root, w);
      }
    } catch (IOException ignored) {
    }
  }

  public static synchronized List<String> orderedIds() {
    List<String> out = new ArrayList<>();
    for (String id : order) {
      if (unlocked.contains(id)) out.add(id);
    }
    return out;
  }

  public static synchronized boolean isUnlocked(String id) {
    return unlocked.contains(id);
  }

  /** Returns true if newly unlocked. Unknown ids are registered on the fly. */
  public static synchronized boolean setUnlocked(String id) {
    String c = id.trim().toLowerCase(java.util.Locale.ROOT);
    if (!CODE.matcher(":" + c + ":").matches()) return false;
    if (!display.containsKey(c)) {
      display.put(c, "");
      order.add(c);
      save();
    }
    return unlocked.add(c);
  }

  public static synchronized void clearUnlocked() {
    unlocked.clear();
  }

  public static synchronized String displayFor(String id) {
    String d = display.get(id);
    return d == null || d.isEmpty() ? null : d;
  }

  public static synchronized List<String> findCodes(String text) {
    List<String> out = new ArrayList<>();
    Matcher m = CODE.matcher(text);
    while (m.find()) out.add(m.group(1));
    return out;
  }

  public static synchronized String replaceKnown(String text) {
    Matcher m = CODE.matcher(text);
    StringBuffer sb = new StringBuffer();
    while (m.find()) {
      String d = display.get(m.group(1));
      m.appendReplacement(sb, d == null || d.isEmpty()
          ? Matcher.quoteReplacement(m.group(0)) : Matcher.quoteReplacement(d));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  public static synchronized int harvestTexts(List<String> texts) {
    int n = 0;
    for (String t : texts) {
      String s = t.trim();
      if (!s.startsWith(":")) continue;
      s = s.substring(1);
      if (s.endsWith(":") && s.length() > 1) s = s.substring(0, s.length() - 1);
      if (CODE.matcher(":" + s + ":").matches() && !display.containsKey(s)) {
        display.put(s, "");
        order.add(s);
        n++;
      }
    }
    if (n > 0) save();
    return n;
  }

  public static synchronized void noteSent(String text) {
    if (findCodes(text).isEmpty()) return;
    pendingSent.addLast(text);
    while (pendingSent.size() > 5) pendingSent.removeFirst();
  }

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
        String known = display.get(code);
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
            display.put(code, glyph);
            save();
            pendingSent.remove(sent);
            DebugLog.info("[emoji] learned :" + code + ":");
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
