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
  private static KeyMapping blueBalloonKey;
  private static KeyMapping greenBalloonKey;
  private static KeyMapping yellowBalloonKey;
  private static KeyMapping redBalloonKey;
  private static KeyMapping badgerHudKey;
  private static KeyMapping extrapolationKey;
  private static boolean joinFetchDone;
  private static boolean badgerFetchDone;
  private static boolean worldsFetchDone;
  private static int joinTicks;
  private static KeyMapping.Category scanCategory;
  private static boolean scanCaptureArmed;
  private HotkeyFeatures() {}

  /** Current scan binding display name for UI. */
  /** Rebuilds the hidden scan mapping from config (after reset/rebind). */
  public static void reloadScanKey() {
    try {
      com.mojang.blaze3d.platform.InputConstants.Key savedKey =
          OpenOriginsConfig.getInstance().getScanKey();
      scanKey = new KeyMapping("key.openorigins.scan_cosmetics", savedKey.getType(),
          savedKey.getValue(), scanCategory);
    } catch (Throwable ignored) {
    }
  }

  public static String scanKeyName() {
    try {
      return scanKey == null ? "?" : scanKey.getTranslatedKeyMessage().getString();
    } catch (Throwable t) {
      return "?";
    }
  }

  public static boolean isScanCaptureArmed() { return scanCaptureArmed; }
  public static void beginScanCapture() { scanCaptureArmed = true; }

  /** Rebinds the hidden scan key. ESC cancels. */
  public static void confirmScanCapture(int keyCode, int scanCode) {
    scanCaptureArmed = false;
    if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
      return;
    }
    try {
      com.mojang.blaze3d.platform.InputConstants.Key key =
          keyCode != com.mojang.blaze3d.platform.InputConstants.UNKNOWN.getValue()
              ? com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(keyCode)
              : com.mojang.blaze3d.platform.InputConstants.Type.SCANCODE.getOrCreate(scanCode);
      scanKey = new KeyMapping("key.openorigins.scan_cosmetics", key.getType(),
          key.getValue(), scanCategory);
      OpenOriginsConfig.getInstance().setScanKey(key);
    } catch (Throwable ignored) {
    }
  }

  /** True when the pressed key is the bound scan key (works inside GUIs too). */
  public static boolean matchesScan(net.minecraft.client.input.KeyEvent event) {
    try {
      return scanKey != null && scanKey.matches(event);
    } catch (Throwable t) {
      return false;
    }
  }

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
    scanCategory = category;
    reloadScanKey();
    teleportMenuKey = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.openorigins.teleport_menu", InputConstants.UNKNOWN.getValue(), category));
    blueBalloonKey = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.openorigins.balloon_blue", InputConstants.UNKNOWN.getValue(), category));
    greenBalloonKey = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.openorigins.balloon_green", InputConstants.UNKNOWN.getValue(), category));
    yellowBalloonKey = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.openorigins.balloon_yellow", InputConstants.UNKNOWN.getValue(), category));
    redBalloonKey = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.openorigins.balloon_red", InputConstants.UNKNOWN.getValue(), category));
    badgerHudKey = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.openorigins.badger_hud", InputConstants.UNKNOWN.getValue(), category));
    extrapolationKey = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.openorigins.extrapolation", InputConstants.UNKNOWN.getValue(), category));

    ClientTickEvents.END_CLIENT_TICK.register(client -> {
      while (badgerHudKey.consumeClick()) OpenOriginsConfig.getInstance().toggleBadgerHudVisible();
      while (extrapolationKey.consumeClick()) {
        com.openorigins.client.OpenOriginsConfig cfg = OpenOriginsConfig.getInstance();
        boolean v = !cfg.isEnabled(com.openorigins.client.OriginFeature.ENTITY_EXTRAPOLATION);
        cfg.setEnabled(com.openorigins.client.OriginFeature.ENTITY_EXTRAPOLATION, v);
        try {
          net.minecraft.client.gui.components.toasts.SystemToast.add(
              client.gui.toastManager(),
              new net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId(),
              net.minecraft.network.chat.Component.literal("OpenOrigins"),
              net.minecraft.network.chat.Component.literal("Entity Extrapolation: " + (v ? "ON" : "OFF")));
        } catch (Throwable ignored) {
        }
      }
      if (!com.openorigins.client.ConnectionUtil.isConnected()) {
        joinFetchDone = false;
        badgerFetchDone = false;
        worldsFetchDone = false;
        joinTicks = 0;
      } else {
        joinTicks++;
        com.openorigins.client.util.BackgroundMenu.tick();
        if (!badgerFetchDone && joinTicks > 100
            && !com.openorigins.client.util.BackgroundMenu.isArmed()
            && com.openorigins.client.util.BackgroundMenu.tryArm("streaks")) {
          badgerFetchDone = true;
        }
        if (!worldsFetchDone && joinTicks > 120
            && !com.openorigins.client.util.BackgroundMenu.isArmed()
            && com.openorigins.client.util.OpenWorldCache.worlds().isEmpty()
            && com.openorigins.client.util.BackgroundMenu.tryArm("navigator worlds")) {
          worldsFetchDone = true;
        }
        if (com.openorigins.client.util.BadgerCache.needsDeliveredRefresh()
            && !com.openorigins.client.util.BackgroundMenu.isArmed()
            && com.openorigins.client.util.BackgroundMenu.tryArm("streaks")) {
          com.openorigins.client.util.BadgerCache.markDeliveredRefreshSent();
        }
        if (joinTicks % 100 == 0 && client.player != null
            && !com.openorigins.client.util.BackgroundMenu.isArmed()) {
          if (com.openorigins.client.util.HomeInfoCache.layout().isEmpty()
              && System.currentTimeMillis()
                  - com.openorigins.client.util.BackgroundMenu.lastEmptyFinishMs()
                  > 10 * 60000L) {
            com.openorigins.client.util.HomeFetch.start();
          } else if (com.openorigins.client.util.HomeFetch.consumeIdleRefresh()) {
            com.openorigins.client.util.HomeFetch.start();
          } else if (com.openorigins.client.util.OpenWorldCache.shouldFetch(
              com.openorigins.client.util.HomeInfoCache.names())) {
            com.openorigins.client.util.OpenWorldCache.fetch(() -> null);
          }
        }
      }
      if (client.player != null && com.openorigins.client.ConnectionUtil.isConnected()) {
        com.openorigins.client.util.HelmetCosmeticTracker.offerReal(
            client.player.getMainHandItem(), true);
        com.openorigins.client.util.HelmetCosmeticTracker.tick();
      }
      while (scanKey.consumeClick()) {
        CosmeticScanner.scanAndReport();
        com.openorigins.client.util.ContainerDump.dump(client);
      }
      while (teleportMenuKey.consumeClick()) {
        if (client.gui.screen() == null && ConnectionUtil.isConnected()) {
          client.gui.setScreen(new com.openorigins.client.screen.TeleportMenuScreen());
        }
      }      while (configKey.consumeClick()) {
        if (client.gui.screen() == null) {
          client.gui.setScreen(new com.openorigins.client.screen.ConfigScreen(null));
        }
      }
      if (!ConnectionUtil.isConnected() || client.player == null) return;
      while (navigatorKey.consumeClick()) {
        client.player.connection.sendCommand("navigator");
        com.openorigins.client.util.OutboxStats.record("key /navigator");
      }
      while (gestureKey.consumeClick()) {
        client.player.connection.sendCommand("gesture");
        com.openorigins.client.util.OutboxStats.record("key /gesture");
      }
      while (spawnKey.consumeClick()) {
        client.player.connection.sendCommand("spawn");
        com.openorigins.client.util.OutboxStats.record("key /spawn");
      }
      while (realmKey.consumeClick()) {
        client.player.connection.sendCommand("realm tp");
        com.openorigins.client.util.OutboxStats.record("key /realm tp");
      }
      while (blueBalloonKey.consumeClick()) {
        client.player.connection.sendCommand("balloon blue-balloon");
        com.openorigins.client.util.OutboxStats.record("key /balloon blue-balloon");
      }
      while (greenBalloonKey.consumeClick()) {
        client.player.connection.sendCommand("balloon green-balloon");
        com.openorigins.client.util.OutboxStats.record("key /balloon green-balloon");
      }
      while (yellowBalloonKey.consumeClick()) {
        client.player.connection.sendCommand("balloon yellow-balloon");
        com.openorigins.client.util.OutboxStats.record("key /balloon yellow-balloon");
      }
      while (redBalloonKey.consumeClick()) {
        client.player.connection.sendCommand("balloon red-balloon");
        com.openorigins.client.util.OutboxStats.record("key /balloon red-balloon");
      }
    });
  }

}
