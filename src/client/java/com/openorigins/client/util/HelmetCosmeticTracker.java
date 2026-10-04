package com.openorigins.client.util;

import net.minecraft.world.item.ItemStack;

/**
 * Tracks the real helmet vs helmet-slot cosmetic.
 * Cosmetic names start with a pack glyph (PUA char), real items don't.
 */
public final class HelmetCosmeticTracker {
  private HelmetCosmeticTracker() {}

  private static volatile ItemStack realHelmet = ItemStack.EMPTY;

  public static boolean isCosmetic(ItemStack stack) {
    if (stack == null || stack.isEmpty()) {
      return false;
    }
    String s = stack.getHoverName().getString().strip();
    return !s.isEmpty() && s.charAt(0) > 0x2000;
  }

  /** Feeds a candidate real helmet (armor slot or cursor). */
  public static void offerReal(ItemStack stack) {
    if (stack == null || stack.isEmpty() || isCosmetic(stack)) {
      return;
    }
    ItemStack cur = realHelmet;
    if (cur.isEmpty() || !ItemStack.isSameItemSameComponents(cur, stack)) {
      realHelmet = stack.copy();
    }
  }

  public static ItemStack realHelmet() {
    ItemStack cur = realHelmet;
    return cur == null ? ItemStack.EMPTY : cur;
  }
}
