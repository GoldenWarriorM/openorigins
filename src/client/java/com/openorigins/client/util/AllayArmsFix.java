package com.openorigins.client.util;

import com.openorigins.client.OpenOriginsConfig;
import net.minecraft.world.item.ItemStack;

/**
 * Render-time nudge for the server's custom allay arm parts. The arm geometry
 * ships as item models held by armor stands; on every client version they sit
 * detached from the body, so flagged hand items are translated in item space.
 */
public final class AllayArmsFix {
  /** CustomModelData floats of every known allay arm part model. */
  private static final float[] ARM_CMDS = {
      2380f, 2906f, 2909f, 2913f, 2915f, 13035f, 13973f, 14294f,
  };

  /** Player cosmetics share the 13033-13038 float range with vanilla_allay parts.
   *  Tell them apart by item_model: cosmetics carry an explicit override,
   *  allay parts use CMD dispatch or an allay model path. */
  public static boolean isOwnCosmetic(ItemStack stack) {
    if (stack == null || stack.isEmpty()) {
      return false;
    }
    try {
      net.minecraft.world.item.component.CustomModelData cmd =
          stack.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
      if (cmd == null || cmd.floats().isEmpty()) {
        return false;
      }
      for (Float f : cmd.floats()) {
        if (f == null || f.floatValue() < 13033f || f.floatValue() > 13038f) {
          return false;
        }
      }
      net.minecraft.resources.Identifier model =
          stack.get(net.minecraft.core.component.DataComponents.ITEM_MODEL);
      if (model != null && model.getPath().contains("allay")) {
        return false;
      }
      return true;
    } catch (Throwable t) {
      return false;
    }
  }

  private AllayArmsFix() {
  }

  public static boolean isArmsItem(ItemStack stack) {
    if (stack == null || stack.isEmpty()) {
      return false;
    }
    try {
      net.minecraft.resources.Identifier model =
          stack.get(net.minecraft.core.component.DataComponents.ITEM_MODEL);
      if (model != null) {
        String p = model.getPath();
        if (p.contains("allay") && p.contains("arm") && !p.contains("plushie")) {
          return true;
        }
      }
    } catch (Throwable ignored) {
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
}
