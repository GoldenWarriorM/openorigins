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
  private static final Map<Integer, ItemStack> INV_HEADS = new java.util.concurrent.ConcurrentHashMap<>();
  private static volatile Map<Integer, ItemStack> diffBase;
  private static volatile long diffAt;
  private static volatile ItemStack pendingHelmet = ItemStack.EMPTY;
  private static volatile long pendingAt;
  private static final long PENDING_TTL_MS = 30000;
  private static volatile String lastArmorKey = "";
  private static volatile ItemStack lastArmorStack = ItemStack.EMPTY;
  private static volatile ItemStack lastRealArmor = ItemStack.EMPTY;
  private static volatile long lastRealArmorMs;
  private static volatile boolean diffFresh;
  private static volatile boolean everNonEmptyArmor;
  private static volatile net.minecraft.world.entity.player.Player lastPlayer;
  private static volatile long lastArmorChangeMs;
  private static volatile long lastCommitMs;
  private static volatile long optimisticMs = -1;
  private static volatile boolean cursorHoldingReal;
  private static volatile long pendingClearAt = -1;

  private static String keyOf(ItemStack stack) {
    if (stack == null || stack.isEmpty()) {
      return "<empty>";
    }
    return describe(stack);
  }

  /** Called on every armor-slot-5 server sync. */
  public static void noteArmor(ItemStack stack) {    try {
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      if (mc != null && mc.player != lastPlayer) {
        lastPlayer = mc.player;
        everNonEmptyArmor = false;
        lastArmorKey = "";
      }
    } catch (Throwable ignored) {
    }
    if (stack != null && !stack.isEmpty()) {
      everNonEmptyArmor = true;
    }
    String k = keyOf(stack);
    if (!k.equals(lastArmorKey)) {
      ItemStack prevArmor = lastArmorStack;
      lastArmorStack = stack == null ? ItemStack.EMPTY : stack.copy();
      lastArmorKey = k;
      lastArmorChangeMs = System.currentTimeMillis();
      pendingClearAt = -1;
      confirmOptimistic();
      if (isFirework(stack) && prevArmor.isEmpty()) {
        // Fresh cover over a bare head: hidden state unknown again.
        bareHead = false;
      }
      if (isHeadArmor(stack)) {
        lastRealArmor = stack.copy();
        lastRealArmorMs = System.currentTimeMillis();
      }
      if (!pendingHelmet.isEmpty() && !heldAnywhere(pendingHelmet)
          && !storedAnywhere(pendingHelmet) && !menuHas(pendingHelmet)) {
        ItemStack cur = realHelmet;
        if (cur.isEmpty() || !ItemStack.isSameItemSameComponents(cur, pendingHelmet)) {
          com.openorigins.client.util.DebugLog.info("[helmet] commit {} (was {}) gone={}",
              describe(pendingHelmet), cur.isEmpty() ? "<empty>" : describe(cur), goneEverywhere(pendingHelmet));
        }
        realHelmet = pendingHelmet.copy();
        setTip(pendingHelmet);
        saveHelmet();
        lastCommitMs = System.currentTimeMillis();
        pendingHelmet = ItemStack.EMPTY;
      }
      if (isFirework(stack)) {
        if (isHeadArmor(prevArmor)) {
          ItemStack cur = realHelmet;
          realHelmet = prevArmor.copy();
          setTip(prevArmor);
          saveHelmet();
          lastCommitMs = System.currentTimeMillis();
          com.openorigins.client.util.DebugLog.info("[helmet] link-stash {} (was {})",
              describe(prevArmor), cur.isEmpty() ? "<empty>" : describe(cur));
        }
        Map<Integer, ItemStack> base = new java.util.HashMap<>();
        for (Map.Entry<Integer, ItemStack> e : INV_HEADS.entrySet()) {
          if (e.getValue() != null && !e.getValue().isEmpty()) {
            base.put(e.getKey(), e.getValue().copy());
          }
        }
        diffBase = base;
        diffAt = System.currentTimeMillis();
        // Only a fresh cover (real helmet just replaced by the cosmetic) may
        // promote vanished storage heads; re-sync wobble of the cosmetic itself
        // must not (teleport re-sends shuffle storage and re-sync armor).
        long now = System.currentTimeMillis();
        boolean freshCover = isHeadArmor(prevArmor)
            || (lastRealArmorMs > 0 && now - lastRealArmorMs < 5000);
        diffFresh = freshCover;
        if (!freshCover) {
          departedHelmet = ItemStack.EMPTY;
          return;
        }
        if (!departedHelmet.isEmpty() && now - departedAt < 3000
            && !presentAnywhere(departedHelmet)
            && (realHelmet.isEmpty() || !isSameHead(realHelmet, departedHelmet))) {
          ItemStack cur = realHelmet;
          realHelmet = departedHelmet.copy();
          setTip(departedHelmet);
          lastCommitMs = System.currentTimeMillis();
          com.openorigins.client.util.DebugLog.info("[helmet] departed-stash {} (was {}) gone={}",
              describe(departedHelmet), cur.isEmpty() ? "<empty>" : describe(cur), goneEverywhere(departedHelmet));
          departedHelmet = ItemStack.EMPTY;
        } else {
          diffStashVanished();
        }
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
    ItemStack old = INV_HEADS.get(menuSlot);
    if (isHeadArmor(stack)) {
      INV_HEADS.put(menuSlot, stack.copy());
    } else {
      INV_HEADS.remove(menuSlot);
    }
    if (isHeadArmor(old) && !isSameHead(old, stack)) {
      departedHelmet = old.copy();
      departedAt = System.currentTimeMillis();
    }
  }

  private static volatile ItemStack departedHelmet = ItemStack.EMPTY;
  private static volatile long departedAt;

  private static boolean isSameHead(ItemStack a, ItemStack b) {
    return a != null && b != null && !a.isEmpty() && !b.isEmpty()
        && ItemStack.isSameItemSameComponents(a, b);
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

  /** True if the stack sits in any slot of the currently open menu
   *  (crafting grids, chests, silent server windows, cursor handled by callers). */
  private static boolean menuHas(ItemStack stack) {
    try {
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      if (mc == null || mc.player == null || stack == null || stack.isEmpty()) {
        return false;
      }
      for (net.minecraft.world.inventory.Slot slot : mc.player.containerMenu.slots) {
        try {
          if (ItemStack.isSameItemSameComponents(stack, slot.getItem())) {
            return true;
          }
        } catch (Throwable ignored) {
        }
      }
    } catch (Throwable ignored) {
    }
    return false;
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
      if (menuHas(stack)) {
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
        saveHelmet();
        lastCommitMs = System.currentTimeMillis();
        com.openorigins.client.util.DebugLog.info("[helmet] diff-stash {} (was {})",
            describe(gone), cur.isEmpty() ? "<empty>" : describe(cur));
        diffBase = null;
        return;
      }
    }
  }

  /** Called on every cursor sync: picking up the linked helmet breaks the link. */
  public static void noteCursor(ItemStack stack) {
    if (isStashedReal(stack)) {
      com.openorigins.client.util.DebugLog.info("[helmet] linked picked up, clearing {}",
          describe(realHelmet));
      clear();
    }
  }

  /** Drops the snapshot if the helmet left without any slot activity. */
  public static void resolvePending() {
    long now = System.currentTimeMillis();
    if (diffBase != null) {
      if (now - diffAt > 500) {
        if (diffFresh) {
          diffStashVanished();
        }
        diffBase = null;
        diffFresh = false;
      }
    }
    if (!pendingHelmet.isEmpty() && now - pendingAt > PENDING_TTL_MS) {
      com.openorigins.client.util.DebugLog.info("[helmet] pending expired {}",
          describe(pendingHelmet));
      pendingHelmet = ItemStack.EMPTY;
    }
    if (!pendingHelmet.isEmpty() && !heldAnywhere(pendingHelmet)
        && storedAnywhere(pendingHelmet)) {
      com.openorigins.client.util.DebugLog.info("[helmet] pending back in storage {}",
          describe(pendingHelmet));
      pendingHelmet = ItemStack.EMPTY;
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
      saveHelmet();
      lastCommitMs = System.currentTimeMillis();
      com.openorigins.client.util.DebugLog.info("[helmet] stash {} (was {})",
          describe(stack), cur.isEmpty() ? "<empty>" : describe(cur));
    }
  }

  private static void setTip(ItemStack stack) {
    if (stack != null && !stack.isEmpty()) {
      bareHead = false;
    }
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
    invalidateCosmeticTip();
    saveHelmet();
  }

  private static java.nio.file.Path helmetFile() {
    return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
        .resolve("openorigins_helmet.json");
  }

  /** Persists the snapshot (server-confirmed states only). */
  public static synchronized void saveHelmet() {
    try {
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      com.google.gson.JsonObject root = new com.google.gson.JsonObject();
      if (!realHelmet.isEmpty() && mc != null && mc.level != null) {
        var ops = net.minecraft.resources.RegistryOps.create(
            com.mojang.serialization.JsonOps.INSTANCE, mc.level.registryAccess());
        com.google.gson.JsonElement el = net.minecraft.world.item.ItemStack.OPTIONAL_CODEC
            .encodeStart(ops, realHelmet).getOrThrow();
        root.add("stack", el);
      }
      java.nio.file.Files.writeString(helmetFile(), root.toString(),
          java.nio.charset.StandardCharsets.UTF_8);
    } catch (Throwable ignored) {
    }
  }

  /** Loads the raw snapshot; decoded lazily once a level exists. */
  public static synchronized void loadHelmet() {
    try {
      java.nio.file.Path f = helmetFile();
      if (!java.nio.file.Files.isRegularFile(f)) {
        return;
      }
      com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(
          java.nio.file.Files.readString(f, java.nio.charset.StandardCharsets.UTF_8))
          .getAsJsonObject();
      if (root.has("stack")) {
        rawSaved = root.get("stack");
      }
    } catch (Throwable ignored) {
    }
  }

  /** True if the stack is currently held on cursor or in hands. */
  private static boolean heldAnywhere(ItemStack stack) {
    try {
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      if (mc == null || mc.player == null) {
        return true;
      }
      if (ItemStack.isSameItemSameComponents(stack, mc.player.containerMenu.getCarried())) {
        return true;
      }
      if (ItemStack.isSameItemSameComponents(stack, mc.player.getMainHandItem())
          || ItemStack.isSameItemSameComponents(stack, mc.player.getOffhandItem())) {
        return true;
      }
    } catch (Throwable ignored) {
      return true;
    }
    return false;
  }

  /** True if the stack sits in storage (not armor). */
  private static boolean storedAnywhere(ItemStack stack) {
    try {
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      if (mc == null || mc.player == null) {
        return false;
      }
      for (ItemStack s : mc.player.getInventory().getNonEquipmentItems()) {
        if (ItemStack.isSameItemSameComponents(stack, s)) {
          return true;
        }
      }
    } catch (Throwable ignored) {
    }
    return false;
  }

  /** Periodic cleanup for headless flows (no GUI open). */
  public static void tick() {
    try {
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      if (mc != null && mc.player != null) {
        double v = mc.player.getAttributeValue(
            net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
        if (!pendingHelmet.isEmpty() && lastArmorValue >= 0 && v > lastArmorValue) {
          net.minecraft.world.item.ItemStack pend = pendingHelmet;
          if (!heldAnywhere(pend) && !storedAnywhere(pend) && !menuHas(pend)) {
            ItemStack cur = realHelmet;
            realHelmet = pend.copy();
            setTip(pend);
            lastCommitMs = System.currentTimeMillis();
            pendingHelmet = ItemStack.EMPTY;
            com.openorigins.client.util.DebugLog.info("[helmet] attr-commit {} (was {}) gone={}",
                describe(realHelmet), cur.isEmpty() ? "<empty>" : describe(cur), goneEverywhere(realHelmet));
          }
        }
        lastArmorValue = v;
      }
    } catch (Throwable ignored) {
    }
    checkOptimistic();
    resolvePending();
  }

  private static volatile double lastArmorValue = -1;
  private static volatile boolean bareHead;

  /** True once armor was removed with no hidden helmet left (suppresses fallback). */
  public static boolean isBareHead() {
    return bareHead;
  }

  /** Optimistic stash from client prediction; rolled back if server disagrees. */
  public static void optimisticStash(ItemStack stack) {
    if (stack == null || stack.isEmpty() || isCosmetic(stack)) {
      return;
    }
    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
    if (mc == null || mc.player == null) {
      return;
    }
    try {
      if (mc.player.getEquipmentSlotForItem(stack)
          != net.minecraft.world.entity.EquipmentSlot.HEAD) {
        return;
      }
    } catch (Throwable t) {
      return;
    }
    ItemStack cur = realHelmet;
    realHelmet = stack.copy();
    setTip(stack);
    optimisticMs = System.currentTimeMillis();
    lastCommitMs = optimisticMs;
    com.openorigins.client.util.DebugLog.info("[helmet] optimistic stash {} (was {})",
        describe(stack), cur.isEmpty() ? "<empty>" : describe(cur));
  }

  /** Optimistic clear from client prediction. */
  public static void optimisticClear() {
    if (!realHelmet.isEmpty()) {
      com.openorigins.client.util.DebugLog.info("[helmet] optimistic clear (was {})",
          describe(realHelmet));
    }
    bareHead = true;
    realHelmet = ItemStack.EMPTY;
    realTip = java.util.Collections.emptyList();
    realTipKey = "";
    invalidateCosmeticTip();
    optimisticMs = System.currentTimeMillis();
    lastCommitMs = optimisticMs;
  }

  /** Server confirmed (any armor change after the optimistic write): keep it. */
  private static void confirmOptimistic() {
    if (optimisticMs > 0 && lastArmorChangeMs >= optimisticMs) {
      // Confirm only if the optimistic stack actually left (merged or equipped).
      // An unrelated re-sync (same cosmetic, wobbled key) must roll back instead.
      if (goneEverywhere(realHelmet)) {
        optimisticMs = -1;
      } else {
        optimisticMs = -1;
        com.openorigins.client.util.DebugLog.info("[helmet] optimistic rollback, keeping nothing {}",
            describe(realHelmet));
        clear();
      }
    }
  }

  /**
   * A head item left its slot while cosmetic is (or becomes) present:
   * assume it is being equipped, server confirms or rolls back.
   */
  public static void optimisticEquipFrom(ItemStack moved, ItemStack armorNow) {
    if (moved == null || moved.isEmpty() || isCosmetic(moved)) {
      return;
    }
    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
    if (mc == null || mc.player == null) {
      return;
    }
    try {
      if (mc.player.getEquipmentSlotForItem(moved)
          != net.minecraft.world.entity.EquipmentSlot.HEAD) {
        return;
      }
    } catch (Throwable t) {
      return;
    }
    if (armorNow == null || armorNow.isEmpty()) {
      return;
    }
    // Never commit an item that is still around (cursor, hands, storage, menu):
    // pickup/putback must not paint the helmet slot. Only truly vanished
    // (merged) items qualify; the safety net below rolls back the rest.
    if (heldAnywhere(moved) || storedAnywhere(moved) || menuHas(moved)) {
      return;
    }
    ItemStack cur = realHelmet;
    realHelmet = moved.copy();
    setTip(moved);
    optimisticMs = System.currentTimeMillis();
    lastCommitMs = optimisticMs;
    com.openorigins.client.util.DebugLog.info("[helmet] optimistic equip {} (was {}) gone={}",
        describe(moved), cur.isEmpty() ? "<empty>" : describe(cur), goneEverywhere(moved));
  }

  /** Called periodically: rolls back unconfirmed optimistic writes. */
  public static void checkOptimistic() {
    if (optimisticMs > 0 && presentAnywhere(realHelmet)) {
      optimisticMs = -1;
      com.openorigins.client.util.DebugLog.info("[helmet] optimistic rollback, reappeared {}",
          describe(realHelmet));
      clear();
      return;
    }
    if (optimisticMs > 0 && System.currentTimeMillis() - optimisticMs > 3000
        && lastArmorChangeMs < optimisticMs) {
      optimisticMs = -1;
      com.openorigins.client.util.DebugLog.info("[helmet] optimistic rollback, dropping {}",
          describe(realHelmet));
      clear();
    }
  }
  /** True if the stack is absent from storage, cursor and armor (likely merged/consumed). */
  public static boolean goneEverywhere(ItemStack stack) {
    if (stack == null || stack.isEmpty()) {
      return true;
    }
    try {
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      if (mc == null || mc.player == null) {
        return false;
      }
      for (ItemStack st : mc.player.getInventory().getNonEquipmentItems()) {
        if (ItemStack.isSameItemSameComponents(stack, st)) {
          return false;
        }
      }
      if (ItemStack.isSameItemSameComponents(stack, mc.player.containerMenu.getCarried())) {
        return false;
      }
      if (menuHas(stack)) {
        return false;
      }
      for (int i = 0; i < 5; i++) {
        try {
          if (ItemStack.isSameItemSameComponents(stack, mc.player.containerMenu.getSlot(i).getItem())) {
            return false;
          }
        } catch (Throwable ignored) {
        }
      }
    } catch (Throwable ignored) {
      return false;
    }
    return true;
  }

  public static boolean isStashedReal(ItemStack stack) {
    ItemStack cur = realHelmet;
    return stack != null && !stack.isEmpty() && !cur.isEmpty()
        && ItemStack.isSameItemSameComponents(cur, stack);
  }

  private static volatile boolean stateDumped;

  /** One-time debug snapshot: why the helmet slot is empty. */
  public static void dumpState(ItemStack worn) {
    if (stateDumped) {
      return;
    }
    stateDumped = true;
    try {
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      StringBuilder sb = new StringBuilder();
      sb.append("worn=").append(describe(worn));
      sb.append(" real=").append(describe(realHelmet));
      sb.append(" everArmor=").append(everNonEmptyArmor);
      sb.append(" lastArmorKey=").append(lastArmorKey);
      sb.append(" pending=").append(pendingHelmet.isEmpty() ? "no" : describe(pendingHelmet));
      sb.append(" invHeads=").append(INV_HEADS.size());
      sb.append(" rawSaved=").append(rawSaved != null);
      if (mc != null && mc.player != null) {
        java.util.List<String> heads = new java.util.ArrayList<>();
        for (ItemStack st : mc.player.getInventory().getNonEquipmentItems()) {
          if (st == null || st.isEmpty() || isCosmetic(st)) {
            continue;
          }
          boolean head = false;
          try {
            head = mc.player.getEquipmentSlotForItem(st)
                == net.minecraft.world.entity.EquipmentSlot.HEAD;
          } catch (Throwable ignored) {
          }
          if (head) {
            heads.add(describe(st));
          }
        }
        sb.append(" storageHeads=").append(heads);
      }
      com.openorigins.client.util.DebugLog.info("[helmet] state {}", sb);
    } catch (Throwable ignored) {
    }
  }

  /** True once real armor content was seen (guards login-sync empties). */
  public static boolean hasSeenArmor() {
    return everNonEmptyArmor;
  }

  /** True if the stashed helmet is currently sitting in inventory or on cursor. */
  public static boolean isAvailableElsewhere() {
    ItemStack cur = realHelmet;
    if (cur.isEmpty()) {
      return false;
    }
    String id = net.minecraft.core.registries.BuiltInRegistries.ITEM
        .getKey(cur.getItem()).toString();
    String name = cur.getHoverName().getString();
    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
    if (mc == null || mc.player == null) {
      return false;
    }
    try {
      if (looseMatch(cur, id, name, mc.player.containerMenu.getCarried())) {
        return true;
      }
      for (ItemStack stack : mc.player.getInventory().getNonEquipmentItems()) {
        if (looseMatch(cur, id, name, stack)) {
          return true;
        }
      }
    } catch (Throwable ignored) {
    }
    return false;
  }

  private static boolean looseMatch(ItemStack ref, String id, String name, ItemStack stack) {
    if (stack == null || stack.isEmpty()) {
      return false;
    }
    if (ItemStack.isSameItemSameComponents(ref, stack)) {
      return true;
    }
    String sid = net.minecraft.core.registries.BuiltInRegistries.ITEM
        .getKey(stack.getItem()).toString();
    return sid.equals(id) && stack.getHoverName().getString().equals(name);
  }

  /** Live cosmetic tooltip (memoization from the change menu comes next). */
  public static java.util.List<net.minecraft.network.chat.Component> frozenCosmeticTip(
      ItemStack cosmetic) {
    try {
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      return new java.util.ArrayList<>(
          net.minecraft.client.gui.screens.Screen.getTooltipFromItem(mc, cosmetic));
    } catch (Throwable ignored) {
      return java.util.Collections.emptyList();
    }
  }

  public static void invalidateCosmeticTip() {
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
    StringBuilder key = new StringBuilder(id).append('|').append(name.substring(i));
    try {
      net.minecraft.world.item.component.ItemLore lore = stack.get(
          net.minecraft.core.component.DataComponents.LORE);
      if (lore != null) {
        for (net.minecraft.network.chat.Component line : lore.lines()) {
          key.append('\n').append(line.getString());
        }
      }
    } catch (Throwable ignored) {
    }
    return key.toString();
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
    if ((cur == null || cur.isEmpty()) && rawSaved != null) {
      try {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc != null && mc.level != null) {
          var ops = net.minecraft.resources.RegistryOps.create(
              com.mojang.serialization.JsonOps.INSTANCE, mc.level.registryAccess());
          ItemStack decoded = net.minecraft.world.item.ItemStack.OPTIONAL_CODEC
              .parse(ops, rawSaved).getOrThrow();
          rawSaved = null;
          if (!decoded.isEmpty()) {
            realHelmet = decoded;
            setTip(decoded);
            return decoded;
          }
        }
      } catch (Throwable ignored) {
        rawSaved = null;
      }
    }
    return cur == null ? ItemStack.EMPTY : cur;
  }

  private static volatile com.google.gson.JsonElement rawSaved;
}
