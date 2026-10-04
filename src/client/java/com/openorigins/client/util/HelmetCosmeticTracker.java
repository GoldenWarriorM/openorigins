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

  /** Clears the snapshot (helmet fully removed). */
  public static void clear() {
    realHelmet = ItemStack.EMPTY;
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
