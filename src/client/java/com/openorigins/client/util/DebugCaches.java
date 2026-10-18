package com.openorigins.client.util;

/**
 * Debug action: purge every data cache except the auction price database.
 * Clears both in-memory state and the disk JSON the caches persist to; all
 * of them refill from server menus as the player opens them again.
 */
public final class DebugCaches {
  private DebugCaches() {}

  private static volatile long lastClearAt;

  public static void clearAll() {
    BadgerCache.clearAll();
    HomeInfoCache.clearAll();
    OpenWorldCache.clearAll();
    CosmeticCatalogCache.clearAll();
    ServerPackIcons.clearCache();
    lastClearAt = System.currentTimeMillis();
  }

  /** True for a couple of seconds after a clear, for button feedback. */
  public static boolean justCleared() {
    return System.currentTimeMillis() - lastClearAt < 2000;
  }
}
