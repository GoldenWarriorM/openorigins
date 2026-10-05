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

  private static long flaggedTotal;
  private static long lastFlagMs;

  public static void markFlagged() {
    flaggedTotal++;
    lastFlagMs = System.currentTimeMillis();
  }

  public static String statusLine() {
    if (flaggedTotal == 0) {
      return "arm displays seen: 0 (fix sees nothing — assembly is not flagged item_displays)";
    }
    long agoS = (System.currentTimeMillis() - lastFlagMs) / 1000L;
    return "arm displays seen: " + flaggedTotal + " (last " + agoS + "s ago)";
  }

  /** One-shot census of nearby display/stand entities for chat feedback. */
  public static java.util.List<String> census(net.minecraft.client.Minecraft mc) {
    java.util.List<String> out = new java.util.ArrayList<>();
    if (mc == null || mc.level == null || mc.player == null) {
      return out;
    }
    int shown = 0;
    for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
      if (e == mc.player) {
        continue;
      }
      if (mc.player.distanceToSqr(e.getX(), e.getY(), e.getZ()) > 48 * 48) {
        continue;
      }
      String type =
          net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
      StringBuilder sb = new StringBuilder(type + "#" + e.getId());
      double dist = Math.sqrt(mc.player.distanceToSqr(e.getX(), e.getY(), e.getZ()));
      sb.append(String.format(java.util.Locale.ROOT, " %.0fm", dist));
      try {
        if (e instanceof net.minecraft.world.entity.Display.ItemDisplay disp
            && disp.itemRenderState() != null) {
          net.minecraft.world.item.ItemStack ds = disp.itemRenderState().itemStack();
          if (ds != null && !ds.isEmpty()) {
            sb.append(" ").append(net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(ds.getItem()));
            net.minecraft.world.item.component.CustomModelData cmd =
                ds.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
            if (cmd != null && !cmd.floats().isEmpty()) {
              sb.append(" floats=").append(cmd.floats());
            }
          } else {
            sb.append(" empty");
          }
        } else if (e instanceof net.minecraft.world.entity.decoration.ArmorStand stand) {
          for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[] {
              net.minecraft.world.entity.EquipmentSlot.MAINHAND,
              net.minecraft.world.entity.EquipmentSlot.OFFHAND,
              net.minecraft.world.entity.EquipmentSlot.HEAD }) {
            net.minecraft.world.item.ItemStack st = stand.getItemBySlot(slot);
            if (st != null && !st.isEmpty()) {
              sb.append(" ").append(slot.getSerializedName()).append("=")
                  .append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()));
              net.minecraft.world.item.component.CustomModelData cmd =
                  st.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
              if (cmd != null && !cmd.floats().isEmpty()) {
                sb.append(cmd.floats());
              }
            }
          }
        }
      } catch (Throwable ignored) {
      }
      out.add(sb.toString());
      if (++shown >= 10) {
        out.add("...truncated");
        break;
      }
    }
    return out;
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
    feedback.add("[OpenOrigins] allay arms X = " + v + " texels; " + statusLine());
  }
}
