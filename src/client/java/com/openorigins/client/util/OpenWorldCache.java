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
  private static final String[] COLORS = {"blue", "green", "yellow", "red", "orange", "purple"};

  public static ResetInfo resetFor(String worldColor) {
    return worldColor == null ? null : RESETS.get(worldColor);
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
      ResetInfo prev = RESETS.get(color);
      if (prev == null || !prev.rawLine().equals(resetLine)) {
        RESETS.put(color, new ResetInfo(resetLine, System.currentTimeMillis()));
        StringBuilder all = new StringBuilder();
        for (Component line : lore.lines()) {
          if (all.length() > 0) {
            all.append(" // ");
          }
          all.append(line.getString());
        }
        org.slf4j.LoggerFactory.getLogger("openorigins").info(
            "[openworld] {} reset: '{}' (item '{}', lore: {})", color, resetLine, name, all);
        found++;
      }
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
