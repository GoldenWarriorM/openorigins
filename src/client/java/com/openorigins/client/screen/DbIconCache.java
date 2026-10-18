package com.openorigins.client.screen;

import java.util.Map;
import net.minecraft.world.item.ItemStack;

/**
 * Pure display cache for auction-DB rows: the {@link AuctionPriceDb.Entry}
 * icon is the captured data snapshot; rows without one fall back to a plain
 * reconstruction from the vanilla item id. That construction walks the
 * identifier parse + registry lookup per row rebuild (search typing, scroll,
 * rarity filter), so the screen caches its result per entry key. Contents
 * never change for a fixed vanilla id; entries evicted wholesale past 2048.
 */
final class DbIconCache {
  private DbIconCache() {}

  private static final Map<String, ItemStack> FALLBACK =
      new java.util.concurrent.ConcurrentHashMap<>();

  /** Pure reconstruction from the vanilla item id; keeps count of 1. */
  static ItemStack vanilla(String key, String vanillaId) {
    ItemStack cached = FALLBACK.get(key);
    if (cached != null) {
      return cached;
    }
    ItemStack built = ItemStack.EMPTY;
    try {
      net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(vanillaId);
      if (id != null) {
        net.minecraft.world.item.Item item =
            net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id);
        built = new ItemStack(item, 1);
      }
    } catch (Throwable ignored) {
    }
    if (!built.isEmpty()) {
      if (FALLBACK.size() > 2048) {
        FALLBACK.clear();
      }
      FALLBACK.put(key, built);
    }
    return built;
  }
}
