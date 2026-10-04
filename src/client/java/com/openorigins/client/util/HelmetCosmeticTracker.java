package com.openorigins.client.util;

import java.util.Map;
import net.minecraft.world.item.ItemStack;

/**
 * Tracks the real helmet vs helmet-slot cosmetic.
 * Cosmetic names start with a pack glyph (PUA char), real items don't.
 */
public final class HelmetCosmeticTracker {
  private HelmetCosmeticTracker() {}

  private static volatile ItemStack realHelmet = ItemStack.EMPTY;
  private static volatile java.util.List<net.minecraft.network.chat.Component> realTip =
      java.util.Collections.emptyList();
  private static volatile String realTipKey = "";
  private static volatile java.util.List<net.minecraft.network.chat.Component> frozenTip;
  private static volatile String frozenTipKey = "";
  private static final Map<Integer, ItemStack> INV_HEADS = new java.util.concurrent.ConcurrentHashMap<>();
  private static volatile Map<Integer, ItemStack> diffBase;
  private static volatile long diffAt;
  private static volatile ItemStack pendingHelmet = ItemStack.EMPTY;
  private static volatile long pendingAt;
  private static final long PENDING_TTL_MS = 2000;
  private static volatile String lastArmorKey = "";
  private static volatile long lastArmorChangeMs;
  private static volatile long lastCommitMs;
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
      if (!pendingHelmet.isEmpty()) {
        ItemStack cur = realHelmet;
        if (cur.isEmpty() || !ItemStack.isSameItemSameComponents(cur, pendingHelmet)) {
          org.slf4j.LoggerFactory.getLogger("openorigins").info("[helmet] commit {} (was {})",
              describe(pendingHelmet), cur.isEmpty() ? "<empty>" : describe(cur));
        }
        realHelmet = pendingHelmet.copy();
        setTip(pendingHelmet);
        lastCommitMs = System.currentTimeMillis();
        pendingHelmet = ItemStack.EMPTY;
      }
      if (isFirework(stack)) {
        Map<Integer, ItemStack> base = new java.util.HashMap<>();
        for (Map.Entry<Integer, ItemStack> e : INV_HEADS.entrySet()) {
          if (e.getValue() != null && !e.getValue().isEmpty()) {
            base.put(e.getKey(), e.getValue().copy());
          }
        }
        diffBase = base;
        diffAt = System.currentTimeMillis();
        diffStashVanished();
      }
    }
  }

  private static boolean isFirework(ItemStack stack) {
    return stack != null && !stack.isEmpty()
        && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())
            .toString().equals("minecraft:firework_star");
  }

  /** Tracks head-armor items in player storage (menu slots 9-44). */
  public static void noteInvSlot(int menuSlot, ItemStack stack) {
    if (menuSlot < 9 || menuSlot > 44) {
      return;
    }
    if (isHeadArmor(stack)) {
      INV_HEADS.put(menuSlot, stack.copy());
    } else {
      INV_HEADS.remove(menuSlot);
    }
  }

  public static void clearInv() {
    INV_HEADS.clear();
  }

  private static boolean isHeadArmor(ItemStack stack) {
    if (stack == null || stack.isEmpty() || isCosmetic(stack)) {
      return false;
    }
    try {
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      return mc != null && mc.player != null
          && mc.player.getEquipmentSlotForItem(stack)
              == net.minecraft.world.entity.EquipmentSlot.HEAD;
    } catch (Throwable t) {
      return false;
    }
  }

  private static boolean presentAnywhere(ItemStack stack) {
    try {
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      if (mc == null || mc.player == null) {
        return true;
      }
      if (isStashedReal(mc.player.containerMenu.getCarried())
          && ItemStack.isSameItemSameComponents(stack, mc.player.containerMenu.getCarried())) {
        return true;
      }
      net.minecraft.world.entity.player.Inventory inv = mc.player.getInventory();
      for (int i = 0; i < inv.getContainerSize(); i++) {
        if (ItemStack.isSameItemSameComponents(stack, inv.getItem(i))) {
          return true;
        }
      }
    } catch (Throwable ignored) {
      return true;
    }
    return false;
  }

  /** A head item vanished from storage exactly when the combo formed: it got merged. */
  private static void diffStashVanished() {
    Map<Integer, ItemStack> base = diffBase;
    if (base == null || base.isEmpty()) {
      return;
    }
    for (Map.Entry<Integer, ItemStack> e : base.entrySet()) {
      ItemStack gone = e.getValue();
      if (gone != null && !gone.isEmpty() && !presentAnywhere(gone)) {
        ItemStack cur = realHelmet;
        realHelmet = gone.copy();
        setTip(gone);
        lastCommitMs = System.currentTimeMillis();
        org.slf4j.LoggerFactory.getLogger("openorigins").info("[helmet] diff-stash {} (was {})",
            describe(gone), cur.isEmpty() ? "<empty>" : describe(cur));
        diffBase = null;
        return;
      }
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
    long now = System.currentTimeMillis();
    if (diffBase != null) {
      if (now - diffAt > 500) {
        diffStashVanished();
        diffBase = null;
      }
    }
    if (!pendingHelmet.isEmpty() && now - pendingAt > PENDING_TTL_MS) {
      org.slf4j.LoggerFactory.getLogger("openorigins").info("[helmet] pending expired {}",
          describe(pendingHelmet));
      pendingHelmet = ItemStack.EMPTY;
    }
    if (!realHelmet.isEmpty() && now - lastCommitMs > 3000 && isAvailableElsewhere()) {
      org.slf4j.LoggerFactory.getLogger("openorigins").info("[helmet] snapshot found in inventory, clearing {}",
          describe(realHelmet));
      clear();
      return;
    }
    if (pendingClearAt > 0 && now - pendingClearAt > 500) {
      pendingClearAt = -1;
      org.slf4j.LoggerFactory.getLogger("openorigins").info("[helmet] pending clear fired, dropping {}",
          describe(realHelmet));
      clear();
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
    offerReal(stack, false);
  }

  /**
   * @param provisional true when seen on cursor (transport?): commits only
   *                    once the armor slot moves; expires after TTL.
   */
  public static void offerReal(ItemStack stack, boolean provisional) {
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
      return;
    }
    if (provisional) {
      pendingHelmet = stack.copy();
      pendingAt = System.currentTimeMillis();
      return;
    }
    ItemStack cur = realHelmet;
    if (cur.isEmpty() || !ItemStack.isSameItemSameComponents(cur, stack)) {
      realHelmet = stack.copy();
      setTip(stack);
      lastCommitMs = System.currentTimeMillis();
      org.slf4j.LoggerFactory.getLogger("openorigins").info("[helmet] stash {} (was {})",
          describe(stack), cur.isEmpty() ? "<empty>" : describe(cur));
    }
  }

  private static void setTip(ItemStack stack) {
    try {
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      if (mc == null) {
        realTip = java.util.Collections.emptyList();
        realTipKey = "";
        return;
      }
      realTip = new java.util.ArrayList<>(
          net.minecraft.client.gui.screens.Screen.getTooltipFromItem(mc, stack));
      realTipKey = describe(stack);
    } catch (Throwable ignored) {
      realTip = java.util.Collections.emptyList();
      realTipKey = "";
    }
  }

  public static java.util.List<net.minecraft.network.chat.Component> realTip() {
    return realTip;
  }

  /** Clears the snapshot (helmet fully removed). */
  public static void clear() {
    realHelmet = ItemStack.EMPTY;
    realTip = java.util.Collections.emptyList();
    realTipKey = "";
  }

  /** True if the stack equals the stashed real helmet. */
  public static boolean isStashedReal(ItemStack stack) {
    ItemStack cur = realHelmet;
    return stack != null && !stack.isEmpty() && !cur.isEmpty()
        && ItemStack.isSameItemSameComponents(cur, stack);
  }

  /** True if the stashed helmet is currently sitting in inventory or on cursor. */
  public static boolean isAvailableElsewhere() {
    ItemStack cur = realHelmet;
    if (cur.isEmpty()) {
      return false;
    }
    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
    if (mc == null || mc.player == null) {
      return false;
    }
    try {
      if (isStashedReal(mc.player.containerMenu.getCarried())) {
        return true;
      }
      for (ItemStack stack : mc.player.getInventory().getNonEquipmentItems()) {
        if (isStashedReal(stack)) {
          return true;
        }
      }
    } catch (Throwable ignored) {
    }
    return false;
  }

  /** Frozen cosmetic tooltip: stable across equip flips, refreshed on removal. */
  public static java.util.List<net.minecraft.network.chat.Component> frozenCosmeticTip(
      ItemStack cosmetic) {
    String key = cosmeticKey(cosmetic);
    if (frozenTip == null || !key.equals(frozenTipKey)) {
      try {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        frozenTip = new java.util.ArrayList<>(
            net.minecraft.client.gui.screens.Screen.getTooltipFromItem(mc, cosmetic));
      } catch (Throwable ignored) {
        frozenTip = java.util.Collections.emptyList();
      }
      frozenTipKey = key;
    }
    return frozenTip;
  }

  public static void invalidateCosmeticTip() {
    frozenTip = null;
    frozenTipKey = "";
  }
  public static String cosmeticKey(ItemStack stack) {
    if (stack == null || stack.isEmpty()) {
      return "";
    }
    String id = net.minecraft.core.registries.BuiltInRegistries.ITEM
        .getKey(stack.getItem()).toString();
    String name = stack.getHoverName().getString().strip();
    int i = 0;
    while (i < name.length() && (name.charAt(i) <= 0x20 || name.charAt(i) > 0x2000)) {
      i++;
    }
    return id + "|" + name.substring(i);
  }

  /** Debug form: id + escaped hover name + count. */
  public static String describe(ItemStack stack) {    if (stack == null || stack.isEmpty()) {
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
