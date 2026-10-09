package com.openorigins.client;

import com.openorigins.client.server.EntityData;

public final class FeatureRegistry {
  private FeatureRegistry() {}

  public static void registerAll() {
    HotkeyFeatures.register();
    AuctionPing.register();
    CosmeticScanner.registerAutoScan();
    net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
      if (client.level == null) return;
      // Applies tag ops that arrived before their entities were tracked.
      if (client.level.getGameTime() % 10 == 0) {
        EntityData.flushPendingTags();
      }
    });
    net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementAfter(
        net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.MISC_OVERLAYS,
        BadgerHud.ID, new BadgerHud());
  }
}
