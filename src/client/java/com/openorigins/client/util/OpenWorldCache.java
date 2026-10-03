package com.openorigins.client.util;

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

  private static final Map<String, ResetInfo> RESETS = new ConcurrentHashMap<>();
  private static final Map<String, Long> RESET_AT = new ConcurrentHashMap<>();
  private static final String[] COLORS = {"blue", "green", "yellow", "red", "orange", "purple"};

  public static ResetInfo resetFor(String worldColor) {
    return worldColor == null ? null : RESETS.get(worldColor);
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
  public static String countdown(String worldColor) {    long at = resetAt(worldColor);
    if (at <= 0) {
      return null;
    }
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
    if (screen == null || screen.getMenu() == null) {
      return 0;
    }
    Minecraft mc = Minecraft.getInstance();
    Object playerInv = (mc != null && mc.player != null) ? mc.player.getInventory() : null;
    int found = 0;
    for (Slot slot : screen.getMenu().slots) {
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
      HomeInfoCache.save();
      StringBuilder all = new StringBuilder();
      for (Component line : lore.lines()) {
        if (all.length() > 0) {
          all.append(" // ");
        }
        all.append(line.getString());
      }
      org.slf4j.LoggerFactory.getLogger("openorigins").info(
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
