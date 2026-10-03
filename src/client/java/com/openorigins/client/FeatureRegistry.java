package com.openorigins.client;

public final class FeatureRegistry {
  private FeatureRegistry() {}

  public static void registerAll() {
    HotkeyFeatures.register();
    AuctionPing.register();
    CosmeticScanner.registerAutoScan();
    net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementAfter(
        net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.MISC_OVERLAYS,
        BadgerHud.ID, new BadgerHud());
  }
}
