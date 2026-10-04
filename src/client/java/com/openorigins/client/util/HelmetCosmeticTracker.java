package com.openorigins.client.util;

import net.minecraft.world.item.ItemStack;

/**
 * Tracks the real helmet vs helmet-slot cosmetic.
 * Cosmetic names start with a pack glyph (PUA char), real items don't.
 */
public final class HelmetCosmeticTracker {
  private HelmetCosmeticTracker() {}

  private static volatile ItemStack realHelmet = ItemStack.EMPTY;
  private static volatile String lastArmorKey = "";
  private static volatile long lastArmorChangeMs;
  private static volatile boolean cursorHoldingReal;
  private static volatile long pendingClearAt = -1;

  private static String keyOf(ItemStack stack) {
    if (stack == null || stack.isEmpty()) {
      return "<empty>";
    }
    return describe(stack);
  }

  /** Called on every armor-slot-5 server sync. */
  public static void noteArmor(ItemStack stack) {
    String k = keyOf(stack);
    if (!k.equals(lastArmorKey)) {
      lastArmorKey = k;
      lastArmorChangeMs = System.currentTimeMillis();
      pendingClearAt = -1;
    }
  }

  /** Called on every cursor sync. */
  public static void noteCursor(ItemStack stack) {
    boolean holding = isStashedReal(stack);
    boolean was = cursorHoldingReal;
    cursorHoldingReal = holding;
    if (was && !holding && (stack == null || stack.isEmpty())) {
      if (System.currentTimeMillis() - lastArmorChangeMs > 1000) {
        pendingClearAt = System.currentTimeMillis();
      }
    }
  }

  /** Drops the snapshot if the helmet left without any slot activity. */
  public static void resolvePending() {
    if (pendingClearAt > 0 && System.currentTimeMillis() - pendingClearAt > 500) {
      pendingClearAt = -1;
      org.slf4j.LoggerFactory.getLogger("openorigins").info("[helmet] pending clear fired, dropping {}",
          describe(realHelmet));
      realHelmet = ItemStack.EMPTY;
    }
  }

  public static boolean isCosmetic(ItemStack stack) {
    if (stack == null || stack.isEmpty()) {
      return false;
    }
    String s = stack.getHoverName().getString().strip();
    return !s.isEmpty() && s.charAt(0) > 0x2000;
  }

  /** Feeds a candidate real helmet (armor slot or cursor): head armor only. */
  public static void offerReal(ItemStack stack) {
    if (stack == null || stack.isEmpty()) {
      return;
    }
    if (isCosmetic(stack)) {
      return;
    }
    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
    if (mc == null || mc.player == null) {
      return;
    }
    net.minecraft.world.entity.EquipmentSlot slot = mc.player.getEquipmentSlotForItem(stack);
    if (slot != net.minecraft.world.entity.EquipmentSlot.HEAD) {
      org.slf4j.LoggerFactory.getLogger("openorigins").info("[helmet] skip non-head {} ({})",
          describe(stack), slot);
      return;
    }
    ItemStack cur = realHelmet;
    if (cur.isEmpty() || !ItemStack.isSameItemSameComponents(cur, stack)) {
      realHelmet = stack.copy();
      org.slf4j.LoggerFactory.getLogger("openorigins").info("[helmet] stash {} (was {})",
          describe(stack), cur.isEmpty() ? "<empty>" : describe(cur));
    }
  }

  /** Clears the snapshot (helmet fully removed). */
  public static void clear() {
    realHelmet = ItemStack.EMPTY;
  }

  /** True if the stack equals the stashed real helmet. */
  public static boolean isStashedReal(ItemStack stack) {
    ItemStack cur = realHelmet;
    return stack != null && !stack.isEmpty() && !cur.isEmpty()
        && ItemStack.isSameItemSameComponents(cur, stack);
  }

  /** Debug form: id + escaped hover name + count. */
  public static String describe(ItemStack stack) {
    if (stack == null || stack.isEmpty()) {
      return "<empty>";
    }
    String id = net.minecraft.core.registries.BuiltInRegistries.ITEM
        .getKey(stack.getItem()).toString();
    String name = stack.getHoverName().getString();
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (c < 0x20 || c > 0x2000) {
        sb.append(String.format("\\u%04x", (int) c));
      } else {
        sb.append(c);
      }
    }
    return id + " '" + sb + "' x" + stack.getCount();
  }

  public static ItemStack realHelmet() {
    ItemStack cur = realHelmet;
    return cur == null ? ItemStack.EMPTY : cur;
  }
}
