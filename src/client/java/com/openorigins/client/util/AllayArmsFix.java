package com.openorigins.client.util;

import com.openorigins.client.OpenOriginsConfig;
import java.util.Set;
import net.minecraft.world.item.ItemStack;

/**
 * Render-time nudge for the server's custom allay arm parts. The arm geometry
 * ships as item models shown through item_display entities; on 26.2 they sit
 * one texel off the body, so we translate the flagged displays in item space.
 */
public final class AllayArmsFix {
  /** CustomModelData floats of every known allay arm part model. */
  private static final float[] ARM_CMDS = {
      2380f, 2906f, 2909f, 2913f, 2915f, 13035f, 13973f, 14294f,
  };

  private AllayArmsFix() {
  }

  public static boolean isArmsItem(ItemStack stack) {
    if (stack == null || stack.isEmpty()) {
      return false;
    }
    net.minecraft.world.item.component.CustomModelData cmd =
        stack.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
    if (cmd == null) {
      return false;
    }
    for (float want : ARM_CMDS) {
      for (Float f : cmd.floats()) {
        if (f != null && Float.compare(f.floatValue(), want) == 0) {
          return true;
        }
      }
    }
    return false;
  }

  /** Current X shift in blocks (texels / 16). */
  public static float shiftBlocks() {
    return OpenOriginsConfig.getInstance().getAllayArmsShiftTexels() / 16.0f;
  }

  public static String describe(ItemStack stack) {
    if (stack == null || stack.isEmpty()) {
      return "<empty>";
    }
    String hover;
    try {
      hover = stack.getHoverName().getString();
    } catch (Throwable t) {
      hover = "?";
    }
    return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()) + " '" + hover
        + "' x" + stack.getCount();
  }

  public static void nudge(float deltaTexels, Set<String> feedback) {
    OpenOriginsConfig cfg = OpenOriginsConfig.getInstance();
    float v = Math.round((cfg.getAllayArmsShiftTexels() + deltaTexels) * 4.0f) / 4.0f;
    v = Math.max(-8.0f, Math.min(8.0f, v));
    cfg.setAllayArmsShiftTexels(v);
    feedback.add("[OpenOrigins] allay arms X = " + v + " texels");
  }
}
