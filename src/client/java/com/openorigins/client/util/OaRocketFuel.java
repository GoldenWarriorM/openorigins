package com.openorigins.client.util;

import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Rocket-boots fuel readout. Custom id comes from our own resolver, fuel from
 * the "Fuel: N" tooltip line. Logic mirrors OriginAddons (MIT), reimplemented.
 */
public final class OaRocketFuel {
  private OaRocketFuel() {}

  public static String shortId(ItemStack stack) {
    Optional<String> id = CustomItemIds.resolve(stack);
    if (id.isEmpty()) return "";
    String s = id.get();
    int c = s.indexOf(':');
    return c >= 0 ? s.substring(c + 1) : s;
  }

  public static int maxFuel(String shortId) {
    switch (shortId) {
      case "rocket_boots_90":
        return 90;
      case "rocket_boots_30":
        return 30;
      default:
        return 0;
    }
  }

  public static int currentFuel(ItemStack stack) {
    Minecraft mc = Minecraft.getInstance();
    if (mc == null) return 0;
    List<Component> tip;
    try {
      tip = Screen.getTooltipFromItem(mc, stack);
    } catch (Throwable t) {
      return 0;
    }
    for (Component line : tip) {
      String s = line.getString();
      int i = s.indexOf("Fuel: ");
      if (i >= 0) {
        try {
          return Integer.parseInt(s.substring(i + "Fuel: ".length()).trim());
        } catch (NumberFormatException ignored) {
          return 0;
        }
      }
    }
    return 0;
  }

  public static float fraction(ItemStack stack, String shortId) {
    int max = maxFuel(shortId);
    if (max <= 0) return 0.0f;
    return Math.max(0.0f, Math.min(1.0f, (float) currentFuel(stack) / max));
  }

  public static int barColor(float fraction) {
    if (fraction > 0.75f) {
      return net.minecraft.util.Mth.hsvToRgb(0.5f, 1.0f - (fraction - 0.75f) * 2.0f, 1.0f);
    } else if (fraction > 0.25f) {
      return net.minecraft.util.Mth.hsvToRgb(0.666f - 0.166f * (fraction - 0.25f) * 2.0f, 1.0f, 1.0f);
    } else {
      return net.minecraft.util.Mth.hsvToRgb(0.666f, 1.0f, 1.0f - (0.25f - fraction) * 2.0f);
    }
  }

  public static ItemStack wornBoots(Player player) {
    if (player == null) return ItemStack.EMPTY;
    try {
      return player.getItemBySlot(EquipmentSlot.FEET);
    } catch (Throwable t) {
      return ItemStack.EMPTY;
    }
  }
}
