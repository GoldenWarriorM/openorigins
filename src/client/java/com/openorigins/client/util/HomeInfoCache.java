package com.openorigins.client.util;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

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
    CACHE.put(name.toLowerCase(Locale.ROOT), new Info(List.copyOf(lore), color));
  }

  public static String iconPath(String name) {
    Info info = name == null ? null : CACHE.get(name.toLowerCase(Locale.ROOT));
    if (info != null && info.worldColor() != null) {
      return "textures/custom/ui/homes/open_world_" + info.worldColor() + ".png";
    }
    return DEFAULT_ICON;
  }

  /** Server lore verbatim (keeps pack glyphs/colors), or a basic fallback. */
  public static Component tooltip(String name) {
    Info info = name == null ? null : CACHE.get(name.toLowerCase(Locale.ROOT));
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
}
