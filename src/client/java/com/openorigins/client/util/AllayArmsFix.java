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

  /** Every known allay part float (head/body/arms/wings/whole, all variants). */
  private static boolean isPartCmd(float v) {
    return (v >= 2378f && v <= 2382f) || (v >= 2404f && v <= 2405f)
        || (v >= 2903f && v <= 2915f) || (v >= 13025f && v <= 13041f)
        || (v >= 13973f && v <= 13977f) || (v >= 14292f && v <= 14298f);
  }

  public static Float partCmd(ItemStack stack) {
    if (stack == null || stack.isEmpty()) {
      return null;
    }
    net.minecraft.world.item.component.CustomModelData cmd =
        stack.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
    if (cmd == null) {
      return null;
    }
    for (Float f : cmd.floats()) {
      if (f != null && isPartCmd(f.floatValue())) {
        return f;
      }
    }
    return null;
  }

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

  /** Player's own cosmetic rig markers (backitem/cape/stand floats). */
  private static boolean isOwnRig(net.minecraft.world.entity.Entity e) {
    try {
      java.util.List<Float> floats = new java.util.ArrayList<>();
      if (e instanceof net.minecraft.world.entity.Display.ItemDisplay disp) {
        net.minecraft.world.item.ItemStack ds = slotStack(disp);
        if (ds != null && !ds.isEmpty()) {
          net.minecraft.world.item.component.CustomModelData cmd = ds
              .get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
          if (cmd != null) {
            floats.addAll(cmd.floats());
          }
        }
      } else if (e instanceof net.minecraft.world.entity.decoration.ArmorStand stand) {
        for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[] {
            net.minecraft.world.entity.EquipmentSlot.MAINHAND,
            net.minecraft.world.entity.EquipmentSlot.OFFHAND }) {
          net.minecraft.world.item.ItemStack st = stand.getItemBySlot(slot);
          if (st != null && !st.isEmpty()) {
            net.minecraft.world.item.component.CustomModelData cmd =
                st.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
            if (cmd != null) {
              floats.addAll(cmd.floats());
            }
          }
        }
      } else {
        return false;
      }
      if (floats.isEmpty()) {
        return false;
      }
      for (Float f : floats) {
        if (f == null) {
          return false;
        }
        float v = f.floatValue();
        if (v < 13033f || v > 13038f) {
          return false;
        }
      }
      return true;
    } catch (Throwable t) {
      return false;
    }
  }

  /** Synced display item via entity data (fresher than itemRenderState()). */
  private static net.minecraft.world.item.ItemStack slotStack(
      net.minecraft.world.entity.Display.ItemDisplay disp) {
    try {
      net.minecraft.world.entity.SlotAccess slot = disp.getSlot(0);
      return slot == null ? null : slot.get();
    } catch (Throwable t) {
      return null;
    }
  }

  /** One-shot census of nearby display/stand entities for chat feedback. */
  public static java.util.List<String> census(net.minecraft.client.Minecraft mc) {
    java.util.List<String> out = new java.util.ArrayList<>();
    if (mc == null || mc.level == null || mc.player == null) {
      return out;
    }
    java.util.List<java.util.AbstractMap.SimpleEntry<Double, String>> found =
        new java.util.ArrayList<>();
    for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
      if (e == mc.player) {
        continue;
      }
      double distSq = mc.player.distanceToSqr(e.getX(), e.getY(), e.getZ());
      if (distSq > 48 * 48) {
        continue;
      }
      if (isOwnRig(e)) {
        continue;
      }
      String type =
          net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
      StringBuilder sb = new StringBuilder(type + "#" + e.getId());
      double dist = Math.sqrt(mc.player.distanceToSqr(e.getX(), e.getY(), e.getZ()));
      sb.append(String.format(java.util.Locale.ROOT, " %.0fm", dist));
      try {
        if (e instanceof net.minecraft.world.entity.Display.ItemDisplay disp) {
          net.minecraft.world.item.ItemStack ds = slotStack(disp);
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
      found.add(new java.util.AbstractMap.SimpleEntry<>(dist, sb.toString()));
    }
    found.sort(java.util.Comparator.comparingDouble(java.util.AbstractMap.SimpleEntry::getKey));
    int shown = 0;
    for (java.util.AbstractMap.SimpleEntry<Double, String> en : found) {
      out.add(en.getValue());
      if (++shown >= 12) {
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
