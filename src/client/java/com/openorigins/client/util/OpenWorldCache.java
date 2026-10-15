package com.openorigins.client.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * Caches open-world reset info seen in the server's navigator menus.
 * Raw lines are kept; countdown math is added once the format is known.
 */
public final class OpenWorldCache {
  private OpenWorldCache() {}

  public record ResetInfo(String rawLine, long capturedAt) {}

  public record WorldEntry(String color, String name) {}

  private static final Map<String, ResetInfo> RESETS = new ConcurrentHashMap<>();
  private static final Map<String, Long> RESET_AT = new ConcurrentHashMap<>();
  private static final Map<String, String> NAMES = new ConcurrentHashMap<>();
  private static final Map<String, Integer> SLOTS = new ConcurrentHashMap<>();
  private static final String[] COLORS = {"blue", "green", "yellow", "red", "orange", "purple"};
  private static volatile String currentWorldColor;

  public static ResetInfo resetFor(String worldColor) {
    return worldColor == null ? null : RESETS.get(worldColor);
  }

  /** World color seen in the boss bar ("Open World Yellow" or "Yellow Open World"). */
  public static String currentWorldColor() {
    return currentWorldColor;
  }

  /**
   * Matches boss-bar text against known world phrasing. Boss bars say
   * "Open World &lt;color&gt;", menus say "&lt;color&gt; Open World".
   */
  public static synchronized boolean noteBossBar(String text) {
    if (text == null) return false;
    String lower = text.toLowerCase(java.util.Locale.ROOT);
    if (!lower.contains("open world")) return false;
    for (String c : COLORS) {
      if (lower.contains(c)) {
        if (!c.equals(currentWorldColor)) {
          currentWorldColor = c;
        }
        return true;
      }
    }
    return false;
  }

  /** Menu-form world name ("Yellow Open World"), or a capitalized fallback. */
  public static String worldName(String color) {
    if (color == null) {
      return null;
    }
    String n = NAMES.get(color);
    if (n != null && !n.isBlank()) {
      return n;
    }
    return Character.toUpperCase(color.charAt(0)) + color.substring(1) + " Open World";
  }

  /** Scans live boss bars for world phrasing (throttle by caller). */
  public static void pollBossBars(net.minecraft.client.Minecraft mc) {
    if (mc == null || mc.gui == null || mc.gui.hud == null) {
      return;
    }
    try {
      var events = ((com.openorigins.client.mixin.BossBarAccessor) mc.gui.hud.getBossOverlay())
          .openorigins$getBossEvents().values();
      for (var e : events) {
        try {
          if (noteBossBar(e.getName().getString())) {
            return;
          }
        } catch (Throwable ignored) {
        }
      }
    } catch (Throwable ignored) {
    }
  }

  /** Known worlds in stable color order. */
  public static List<WorldEntry> worlds() {
    List<WorldEntry> out = new ArrayList<>();
    for (String c : COLORS) {
      String n = NAMES.get(c);
      if (n != null) {
        out.add(new WorldEntry(c, n));
      }
    }
    return out;
  }

  /** Menu slot of the world button, or -1. Refreshed on every worlds fetch. */
  public static int slotFor(String worldColor) {
    if (worldColor == null) {
      return -1;
    }
    return SLOTS.getOrDefault(worldColor, -1);
  }

  private static long lastAttempt;

  /** Teleports via a background menu click (fetches first if the slot is unknown). */
  public static void teleportTo(String worldColor) {
    if (worldColor == null) {
      return;
    }
    if (slotFor(worldColor) < 0) {
      com.openorigins.client.util.BackgroundMenu.setPendingTeleport(worldColor);
      fetch(() -> null);
      return;
    }
    com.openorigins.client.util.BackgroundMenu.setPendingTeleport(worldColor);
    fetch(() -> null);
  }

  /** Silent background fetch via the server open-world menu. */
  public static void fetch(java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> ret) {    if (!BackgroundMenu.tryArm("navigator worlds")) {
      return;
    }
    lastAttempt = System.currentTimeMillis();
  }

  /** Fetch worlds list once at join / when expired (throttled). */
  public static void ensureWorlds() {
    if (System.currentTimeMillis() - lastAttempt < 60000L) {
      return;
    }
    if (!worlds().isEmpty()) {
      boolean alive = false;
      long now = System.currentTimeMillis();
      for (WorldEntry w : worlds()) {
        if (resetAt(w.color()) > now) {
          alive = true;
          break;
        }
      }
      if (alive) {
        return;
      }
    }
    fetch(() -> null);
  }
  public static boolean shouldFetch(java.util.List<String> homeNames) {
    if (System.currentTimeMillis() - lastAttempt < 30 * 60000L) {
      return false;
    }
    if (homeNames == null || homeNames.isEmpty()) {
      return false;
    }
    for (String n : homeNames) {
      String w = HomeInfoCache.worldColor(n);
      if (w != null && resetAt(w) <= System.currentTimeMillis()) {
        return true;
      }
    }
    return HomeInfoCache.layout().isEmpty();
  }

  /** Absolute reset instant (epoch millis), 0 if unknown. */
  public static long resetAt(String worldColor) {    if (worldColor == null) {
      return 0;
    }
    return RESET_AT.getOrDefault(worldColor, 0L);
  }

  /** Millis until reset, <=0 if unknown or passed. */
  public static long remaining(String worldColor) {
    long at = resetAt(worldColor);
    if (at <= 0) {
      return -1;
    }
    return at - System.currentTimeMillis();
  }

  /** Live countdown text, or null if unknown. */
  public static String countdown(String worldColor) {
    long at = resetAt(worldColor);
    return at > System.currentTimeMillis() ? countdownAt(at) : null;
  }

  /** Live countdown text for an explicit instant, or null if passed. */
  public static String countdownAt(long at) {
    long left = at - System.currentTimeMillis();
    if (left <= 0) {
      return null;
    }
    long mins = left / 60000;
    long days = mins / 1440;
    long hours = (mins % 1440) / 60;
    long mm = mins % 60;
    if (days > 0) {
      return days + "d " + hours + "h";
    }
    if (hours > 0) {
      return hours + "h " + mm + "m";
    }
    return mm + "m";
  }

  /** Name snapshot for a world (display only, refreshed on fetch). */
  public static void putWorldName(String color, String name) {
    if (color == null || name == null || name.isBlank()) {
      return;
    }
    if (!name.equals(NAMES.get(color))) {
      NAMES.put(color, name);
    }
  }

  public static Map<String, Long> snapshot() {
    return Map.copyOf(RESET_AT);
  }

  public static void restore(Map<String, Long> map) {
    RESET_AT.clear();
    for (Map.Entry<String, Long> e : map.entrySet()) {
      if (e.getValue() != null && e.getValue() > 0) {
        RESET_AT.put(e.getKey(), e.getValue());
      }
    }
  }

  /** Short "7h 50m" form of a duration line, or the raw line if unparseable. */
  public static String shortDuration(String line) {
    long dur = parseDurationMillis(line);
    if (dur <= 0) {
      return line;
    }
    long mins = dur / 60000;
    long days = mins / 1440;
    long hours = (mins % 1440) / 60;
    long mm = mins % 60;
    if (days > 0) {
      return days + "d " + hours + "h";
    }
    if (hours > 0) {
      return hours + "h " + mm + "m";
    }
    return mm + "m";
  }

  static long parseDurationMillis(String line) {
    String s = line.toLowerCase(Locale.ROOT);
    java.util.regex.Matcher m = java.util.regex.Pattern.compile(
        "(?:(\\d+)\\s*days?)?[^\\d]*(?:(\\d+)\\s*hours?)?[^\\d]*(?:(\\d+)\\s*min(?:ute)?s?)?")
        .matcher(s);
    if (!m.find()) {
      return 0;
    }
    long days = num(m.group(1));
    long hours = num(m.group(2));
    long mins = num(m.group(3));
    if (days == 0 && hours == 0 && mins == 0) {
      return 0;
    }
    return ((days * 24 + hours) * 60 + mins) * 60000;
  }

  private static long num(String g) {
    if (g == null) {
      return 0;
    }
    try {
      return Long.parseLong(g);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  /** Scans any open container for world reset lore. Returns new findings count. */
  public static int scan(AbstractContainerScreen<?> screen) {
    if (screen == null || screen.getMenu() == null) return 0;
    return scanMenu(screen.getMenu());
  }

  public static int scanMenu(net.minecraft.world.inventory.AbstractContainerMenu menu) {
    if (menu == null) return 0;

    Minecraft mc = Minecraft.getInstance();
    Object playerInv = (mc != null && mc.player != null) ? mc.player.getInventory() : null;
    int found = 0;
    List<Slot> slots = menu.slots;
    for (int si = 0; si < slots.size(); si++) {
      Slot slot = slots.get(si);
      if (!slot.hasItem()) {
        continue;
      }
      if (playerInv != null && slot.container == playerInv) {
        continue;
      }
      ItemStack stack = slot.getItem();
      ItemLore lore = stack.get(DataComponents.LORE);
      if (lore == null || lore.lines().isEmpty()) {
        continue;
      }
      String name = stack.getHoverName().getString();
      String resetLine = null;
      for (Component line : lore.lines()) {
        String s = line.getString();
        if (s.toLowerCase(Locale.ROOT).contains("reset")) {
          resetLine = s;
          break;
        }
      }
      if (resetLine == null) {
        continue;
      }
      String color = colorOf(name, lore.lines());
      if (color == null) {
        continue;
      }
      if (!name.isBlank() && !NAMES.getOrDefault(color, "").equals(name)) {
        NAMES.put(color, name);
      }
      SLOTS.put(color, si);
      long now = System.currentTimeMillis();
      Long known = RESET_AT.get(color);
      if (known != null && known > now) {
        continue;
      }
      long dur = parseDurationMillis(resetLine);
      if (dur <= 0) {
        continue;
      }
      long at = now + dur;
      RESET_AT.put(color, at);
      RESETS.put(color, new ResetInfo(resetLine, now));
      HomeInfoCache.adoptReset(color, at);
      HomeInfoCache.save();
      StringBuilder all = new StringBuilder();
      for (Component line : lore.lines()) {
        if (all.length() > 0) {
          all.append(" // ");
        }
        all.append(line.getString());
      }
      com.openorigins.client.util.DebugLog.info(
          "[openworld] {} reset at {} (in '{}', item '{}', lore: {})",
          color, new java.util.Date(at), resetLine, name, all);
      found++;
    }
    return found;

  }


  private static String colorOf(String name, List<Component> lore) {
    StringBuilder sb = new StringBuilder(name).append(' ');
    for (Component line : lore) {
      sb.append(line.getString()).append(' ');
    }
    String s = sb.toString().toLowerCase(Locale.ROOT);
    for (String c : COLORS) {
      if (s.contains(c)) {
        return c;
      }
    }
    return null;
  }
}
