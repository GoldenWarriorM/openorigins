package com.openorigins.client;

public final class FeatureRegistry {
  private FeatureRegistry() {}

  public static void registerAll() {
    HotkeyFeatures.register();
    AuctionPing.register();
    CosmeticScanner.registerAutoScan();
  }
}
