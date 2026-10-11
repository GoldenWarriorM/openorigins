package com.openorigins.client;

import com.openorigins.client.server.EntityData;

public final class FeatureRegistry {
  private FeatureRegistry() {}

  public static void registerAll() {
    HotkeyFeatures.register();
    AuctionPing.register();
    CosmeticScanner.registerAutoScan();
    com.openorigins.client.util.MobExtrapolation.register();
    com.openorigins.client.util.OutboxStats.register();
    net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementAfter(
        net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.MISC_OVERLAYS,
        BadgerHud.ID, new BadgerHud());
    net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementAfter(
        BadgerHud.ID, ServerOutboxHud.ID, new ServerOutboxHud());
    net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
      if (client.level == null) return;
      // Applies tag ops that arrived before their entities were tracked.
      if (client.level.getGameTime() % 10 == 0) {
        EntityData.flushPendingTags();
      }
    });
    // Entity-bound server data (nametags) must die with the entity object:
    // the server re-sends full tag state on every re-track, and a global
    // id-keyed map would accumulate stale lines across re-tracks.
    net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents.ENTITY_UNLOAD
        .register((entity, level) -> EntityData.clearTag(entity.getId()));
    net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents.ENTITY_LOAD
        .register((entity, level) -> EntityData.flushPendingTags());
  }
}
