package com.openorigins.client.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * Caches home details (created/world/position lore) seen in the server's
 * own Homes container GUI, so our trimmed menu can show the same tooltips
 * and world-colored icons. Filled passively when the player opens that GUI.
 */
public final class HomeInfoCache {
  private HomeInfoCache() {}

  private record Info(List<Component> lore, String worldColor) {}

  private static final Map<String, Info> CACHE = new ConcurrentHashMap<>();
  private static final List<String> LAYOUT = new java.util.concurrent.CopyOnWriteArrayList<>();
  private static volatile int CAPACITY;
  public static final String DEFAULT_ICON = "textures/custom_ui/transport_menu/button_homes.png";
  public static final String PLUS_ICON = "textures/custom/ui/slot_add.png";

  public static int capacity() {
    return CAPACITY;
  }

  /** Parses lines like "5 home slots" / "Home slots: 5" from rank perk lore. */
  static int parseCapacity(String line) {
    String s = line.toLowerCase(java.util.Locale.ROOT);
    if (!(s.contains("home") || s.contains("slot"))) {
      return 0;
    }
    java.util.regex.Matcher m = java.util.regex.Pattern.compile(
        "(\\d+)\\s*(?:x\\s*)?(?:home|house)?\\s*slots?|(?:home|house)s?\\s*slots?\\s*[:x]?\\s*(\\d+)")
        .matcher(s);
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

  public static boolean has(String name) {
    return CACHE.containsKey(key(name));
  }

  public static void put(String name, List<Component> lore) {
    if (name == null || name.isBlank() || lore == null || lore.isEmpty()) {
      return;
    }
    String color = null;
    for (Component line : lore) {
      String s = line.getString().toLowerCase(Locale.ROOT);
      if (s.contains("world:")) {
        for (String c : new String[]{"blue", "green", "yellow", "red", "orange", "purple"}) {
          if (s.contains(c)) {
            color = c;
            break;
          }
        }
        break;
      }
    }
    String k = key(name);
    Info prev = CACHE.get(k);
    List<Component> copy = List.copyOf(lore);
    if (prev != null && java.util.Objects.equals(prev.worldColor(), color) && prev.lore().equals(copy)) {
      return;
    }
    CACHE.put(k, new Info(copy, color));
    save();
  }

  public static String iconPath(String name) {
    Info info = CACHE.get(key(name));
    if (info != null && info.worldColor() != null) {
      return "textures/custom/ui/homes/open_world_" + info.worldColor() + ".png";
    }
    return DEFAULT_ICON;
  }

  /** Server lore verbatim (keeps pack glyphs/colors), or a basic fallback. */
  public static Component tooltip(String name) {
    Info info = CACHE.get(key(name));
    if (info != null && !info.lore().isEmpty()) {
      MutableComponent tip = Component.literal(name == null ? "" : name);
      for (Component line : info.lore()) {
        tip.append(Component.literal("\n")).append(line);
      }
      return tip;
    }
    return Component.literal(name == null ? "" : name).withStyle(ChatFormatting.GOLD)
        .append(Component.literal("\n"))
        .append(Component.translatable("menu.openorigins.homes.teleport_hint").withStyle(ChatFormatting.GREEN))
        .append(Component.literal("\n"))
        .append(Component.translatable("menu.openorigins.homes.delete_hint").withStyle(ChatFormatting.YELLOW));
  }

  /**
   * Scans any open container for home-like item lore (identified by content,
   * not by title — server titles are pack glyphs). Returns captures count.
   */
  public static int captureFrom(AbstractContainerScreen<?> screen) {
    if (screen == null || screen.getMenu() == null) {
      return 0;
    }
    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
    Object playerInv = (mc != null && mc.player != null) ? mc.player.getInventory() : null;
    int n = 0;
    int loreSlots = 0;
    String sample = null;
    List<String> layout = new ArrayList<>();
    java.util.Set<String> others = new java.util.TreeSet<>();
    for (Slot slot : screen.getMenu().slots) {
      if (!slot.hasItem()) {
        continue;
      }
      if (playerInv != null && slot.container == playerInv) {
        continue;
      }
      ItemStack stack = slot.getItem();
      String name = stack.getHoverName().getString();
      ItemLore lore = stack.get(DataComponents.LORE);
      boolean hasLore = lore != null && !lore.lines().isEmpty();
      if (hasLore) {
        loreSlots++;
        if (sample == null) {
          sample = "'" + name + "' / '" + lore.lines().get(0).getString() + "'";
        }
      }
      boolean looksLikeHome = false;
      if (hasLore) {
        for (Component line : lore.lines()) {
          String s = line.getString();
          if (s.contains("World:") || s.contains("Position:") || s.contains("Created:")) {
            looksLikeHome = true;
            break;
          }
        }
      }
      if (hasLore) {
        for (Component capLine : lore.lines()) {
          int cap = parseCapacity(capLine.getString());
          if (cap > 0 && cap != CAPACITY) {
            CAPACITY = cap;
            save();
            org.slf4j.LoggerFactory.getLogger("openorigins")
                .info("[homes-lore] capacity: {} (from '{}')", cap, capLine.getString());
          }
        }
      }
      if (looksLikeHome) {
        put(name, lore.lines());
        layout.add("home:" + key(name));
        n++;
      } else {
        layout.add("locked:" + name);
        String fullLore = "<no lore>";
        if (hasLore) {
          StringBuilder lb = new StringBuilder();
          for (Component lc : lore.lines()) {
            if (lb.length() > 0) {
              lb.append(" // ");
            }
            lb.append(lc.getString());
            if (lb.length() > 160) {
              break;
            }
          }
          fullLore = lb.toString();
        }
        others.add(name + " :: " + fullLore);
      }
    }
    if (loreSlots > 0 && n == 0) {
      org.slf4j.LoggerFactory.getLogger("openorigins")
          .info("[homes-lore] {} lore slots, no homes. sample: {}", loreSlots, sample);
    }
    if (n > 0 && !layout.isEmpty() && !layout.equals(LAYOUT)) {
      LAYOUT.clear();
      LAYOUT.addAll(layout);
      save();
      if (!others.isEmpty()) {
        org.slf4j.LoggerFactory.getLogger("openorigins")
            .info("[homes-lore] layout: {} homes, {} other. others: {}", n, layout.size() - n, others);
      }
    }
    return n;
  }

  /** True for filler entries that are never free slots (rank locks, close button). */
  public static boolean isFiller(String layoutEntry) {
    String e = layoutEntry.toLowerCase(java.util.Locale.ROOT);
    return e.contains("rank") || e.contains("close");
  }

  public static List<String> layout() {
    return List.copyOf(LAYOUT);
  }

  /**
   * Ordered panel cells for the given home names: {@code "home:<realName>"}
   * or {@code "locked"} for free slots. Capped at 15 (3x5).
   */
  public static List<String> cellsFor(List<String> names) {
    Map<String, String> byKey = new java.util.HashMap<>();
    for (String n : names) {
      byKey.put(key(n), n);
    }
    List<String> cells = new ArrayList<>();
    java.util.Set<String> covered = new java.util.HashSet<>();
    int plusLeft = CAPACITY > 0 ? Math.max(0, CAPACITY - names.size()) : -1;
    int needed = ((names.size() + 4) / 5) * 5;
    int pos = 0;
    for (String e : LAYOUT) {
      if (e.startsWith("home:")) {
        String k = e.substring(5);
        String real = byKey.get(k);
        if (real != null) {
          cells.add("home:" + real);
          covered.add(k);
          pos++;
        }
      } else if (e.toLowerCase(java.util.Locale.ROOT).contains("close")) {
        continue;
      } else if (plusLeft > 0) {
        cells.add("locked");
        plusLeft--;
        pos++;
      } else if (plusLeft < 0 && pos < needed) {
        cells.add("locked");
        pos++;
      }
    }
    for (String n : names) {
      if (!covered.contains(key(n))) {
        cells.add("home:" + n);
      }
    }
    return cells.size() > 15 ? cells.subList(0, 15) : cells;
  }

  private static Path file() {
    return FabricLoader.getInstance().getConfigDir().resolve("openorigins_homes.json");
  }

  private static String encodeLine(Component line) {
    ByteBuf buf = Unpooled.buffer();
    try {
      net.minecraft.network.chat.ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.encode(buf, line);
      byte[] b = new byte[buf.readableBytes()];
      buf.readBytes(b);
      return Base64.getEncoder().encodeToString(b);
    } finally {
      buf.release();
    }
  }

  private static Component decodeLine(String s) {
    byte[] b = Base64.getDecoder().decode(s);
    ByteBuf buf = Unpooled.wrappedBuffer(b);
    try {
      return net.minecraft.network.chat.ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.decode(buf);
    } finally {
      buf.release();
    }
  }

  /** Persists cache to disk (only called when data actually changed). */
  public static synchronized void save() {
    try {
      JsonArray arr = new JsonArray();
      for (Map.Entry<String, Info> e : CACHE.entrySet()) {
        JsonObject o = new JsonObject();
        o.addProperty("key", e.getKey());
        if (e.getValue().worldColor() != null) {
          o.addProperty("world", e.getValue().worldColor());
        }
        JsonArray lore = new JsonArray();
        for (Component line : e.getValue().lore()) {
          lore.add(encodeLine(line));
        }
        o.add("lore", lore);
        arr.add(o);
      }
      JsonObject root = new JsonObject();
      root.add("homes", arr);
      if (CAPACITY > 0) {
        root.addProperty("capacity", CAPACITY);
      }
      JsonArray lay = new JsonArray();
      for (String e : LAYOUT) {
        lay.add(e);
      }
      root.add("layout", lay);
      Files.writeString(file(), root.toString(), StandardCharsets.UTF_8);
    } catch (Exception ex) {
      org.slf4j.LoggerFactory.getLogger("openorigins")
          .warn("[homes-lore] save failed: {}", ex.toString());
    }
  }

  /** Loads disk cache at startup; server data overwrites it when captured. */
  public static synchronized void load() {
    try {
      Path f = file();
      if (!Files.isRegularFile(f)) {
        return;
      }
      JsonObject root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
      JsonArray arr = root.getAsJsonArray("homes");
      if (arr == null) {
        return;
      }
      int n = 0;
      for (JsonElement el : arr) {
        JsonObject o = el.getAsJsonObject();
        String k = o.get("key").getAsString();
        String world = o.has("world") ? o.get("world").getAsString() : null;
        List<Component> lore = new ArrayList<>();
        for (JsonElement le : o.getAsJsonArray("lore")) {
          lore.add(decodeLine(le.getAsString()));
        }
        if (!k.isEmpty() && !lore.isEmpty()) {
          CACHE.put(k, new Info(List.copyOf(lore), world));
          n++;
        }
      }
      org.slf4j.LoggerFactory.getLogger("openorigins")
          .info("[homes-lore] loaded {} homes from disk", n);
      JsonArray lay = root.getAsJsonArray("layout");
      if (lay != null) {
        LAYOUT.clear();
        for (JsonElement el : lay) {
          LAYOUT.add(el.getAsString());
        }
      }
      if (root.has("capacity")) {
        try {
          int c = root.get("capacity").getAsInt();
          if (c > 0 && c <= 100) {
            CAPACITY = c;
          }
        } catch (Exception ignored) {
        }
      }
    } catch (Exception ex) {
      org.slf4j.LoggerFactory.getLogger("openorigins")
          .warn("[homes-lore] load failed: {}", ex.toString());
    }
  }
}
