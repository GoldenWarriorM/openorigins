package com.openorigins.client.util;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
  public static final String DEFAULT_ICON = "textures/custom_ui/transport_menu/button_homes.png";

  static String key(String name) {
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
    CACHE.put(key(name), new Info(List.copyOf(lore), color));
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
    int n = 0;
    int loreSlots = 0;
    String sample = null;
    for (Slot slot : screen.getMenu().slots) {
      if (!slot.hasItem()) {
        continue;
      }
      ItemStack stack = slot.getItem();
      ItemLore lore = stack.get(DataComponents.LORE);
      if (lore == null || lore.lines().isEmpty()) {
        continue;
      }
      loreSlots++;
      String name = stack.getHoverName().getString();
      if (sample == null && !lore.lines().isEmpty()) {
        sample = "'" + name + "' / '" + lore.lines().get(0).getString() + "'";
      }
      boolean looksLikeHome = false;
      for (Component line : lore.lines()) {
        String s = line.getString();
        if (s.contains("World:") || s.contains("Position:") || s.contains("Created:")) {
          looksLikeHome = true;
          break;
        }
      }
      if (looksLikeHome) {
        put(name, lore.lines());
        n++;
      }
    }
    if (loreSlots > 0 && n == 0) {
      org.slf4j.LoggerFactory.getLogger("openorigins")
          .info("[homes-lore] {} lore slots, no homes. sample: {}", loreSlots, sample);
    }
    return n;
  }
}
