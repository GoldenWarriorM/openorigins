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
  private static KeyMapping homesMenuKey;
  private static KeyMapping blueBalloonKey;
  private static KeyMapping greenBalloonKey;
  private static KeyMapping yellowBalloonKey;
  private static KeyMapping redBalloonKey;

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
    homesMenuKey = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.openorigins.homes_menu", InputConstants.UNKNOWN.getValue(), category));
    blueBalloonKey = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.openorigins.balloon_blue", InputConstants.UNKNOWN.getValue(), category));
    greenBalloonKey = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.openorigins.balloon_green", InputConstants.UNKNOWN.getValue(), category));
    yellowBalloonKey = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.openorigins.balloon_yellow", InputConstants.UNKNOWN.getValue(), category));
    redBalloonKey = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.openorigins.balloon_red", InputConstants.UNKNOWN.getValue(), category));

    ClientTickEvents.END_CLIENT_TICK.register(client -> {
      while (scanKey.consumeClick()) {
        CosmeticScanner.scanAndReport();
        if (client.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> container
            && client.player != null) {
          Object inv = client.player.getInventory();
          int idx = 0;
          for (net.minecraft.world.inventory.Slot slot : container.getMenu().slots) {
            String tag = (inv != null && slot.container == inv) ? "[inv]" : "[top]";
            if (!slot.hasItem()) {
              idx++;
              continue;
            }
            net.minecraft.world.item.ItemStack stack = slot.getItem();
            StringBuilder lb = new StringBuilder();
            net.minecraft.world.item.component.ItemLore lore =
                stack.get(net.minecraft.core.component.DataComponents.LORE);
            if (lore != null) {
              for (net.minecraft.network.chat.Component line : lore.lines()) {
                if (lb.length() > 0) {
                  lb.append(" // ");
                }
                lb.append(line.getString());
              }
            }
            org.slf4j.LoggerFactory.getLogger("openorigins").info(
                "[menu-slot] {} {} '{}' x{} lore: {}", tag, idx,
                stack.getHoverName().getString(), stack.getCount(), lb);
            idx++;
          }
        }
      }
      while (teleportMenuKey.consumeClick()) {
        if (client.gui.screen() == null && ConnectionUtil.isConnected()) {
          client.gui.setScreen(new com.openorigins.client.screen.TeleportMenuScreen());
        }
      }
      while (homesMenuKey.consumeClick()) {
        if (client.gui.screen() == null && ConnectionUtil.isConnected()) {
          client.gui.setScreen(new com.openorigins.client.screen.HomesMenuScreen());
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
      while (blueBalloonKey.consumeClick()) client.player.connection.sendCommand("balloon blue-balloon");
      while (greenBalloonKey.consumeClick()) client.player.connection.sendCommand("balloon green-balloon");
      while (yellowBalloonKey.consumeClick()) client.player.connection.sendCommand("balloon yellow-balloon");
      while (redBalloonKey.consumeClick()) client.player.connection.sendCommand("balloon red-balloon");
    });
  }
}
