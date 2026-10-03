package com.openorigins.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class HotkeyFeatures {
  private static KeyMapping navigatorKey;
  private static KeyMapping gestureKey;
  private static KeyMapping spawnKey;
  private static KeyMapping realmKey;
  private static KeyMapping configKey;
  private static KeyMapping scanKey;
  private static KeyMapping teleportMenuKey;

  private HotkeyFeatures() {}

  public static void register() {
    KeyMapping.Category category =
        KeyMapping.Category.register(Identifier.fromNamespaceAndPath("openorigins", "keys"));
    navigatorKey = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.openorigins.navigator", InputConstants.KEY_N, category));
    gestureKey = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.openorigins.gesture_menu", InputConstants.KEY_G, category));
    spawnKey = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.openorigins.spawn", InputConstants.UNKNOWN.getValue(), category));
    realmKey = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.openorigins.realm", InputConstants.UNKNOWN.getValue(), category));
    configKey = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.openorigins.config", InputConstants.UNKNOWN.getValue(), category));
    scanKey = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.openorigins.scan_cosmetics", InputConstants.UNKNOWN.getValue(), category));
    teleportMenuKey = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.openorigins.teleport_menu", InputConstants.UNKNOWN.getValue(), category));

    ClientTickEvents.END_CLIENT_TICK.register(client -> {
      while (scanKey.consumeClick()) {
        CosmeticScanner.scanAndReport();
      }
      while (teleportMenuKey.consumeClick()) {
        if (client.gui.screen() == null && ConnectionUtil.isConnected()) {
          client.gui.setScreen(new com.openorigins.client.screen.TeleportMenuScreen());
        }
      }
      while (configKey.consumeClick()) {
        if (client.gui.screen() == null) {
          client.gui.setScreen(new com.openorigins.client.screen.ConfigScreen(null));
        }
      }
      if (!ConnectionUtil.isConnected() || client.player == null) return;
      while (navigatorKey.consumeClick()) client.player.connection.sendCommand("navigator");
      while (gestureKey.consumeClick()) client.player.connection.sendCommand("gesture");
      while (spawnKey.consumeClick()) client.player.connection.sendCommand("spawn");
      while (realmKey.consumeClick()) client.player.connection.sendCommand("realm tp");
    });
  }
}
