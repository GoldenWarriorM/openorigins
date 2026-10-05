package com.openorigins.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opens server menus invisibly: the container is created and fed by packets
 * without ever showing a screen, then closed again. Caches fill silently.
 */
public final class BackgroundMenu {
  private BackgroundMenu() {}

  private static final Logger LOG = LoggerFactory.getLogger("openorigins");

  private static boolean armed;
  private static long deadline;
  private static AbstractContainerMenu menu;
  private static int contentPackets;
  private static String pendingCommand;
  private static long lastSentMs;
  private static int attempts;
  private static String lastTopSig;

  /** @return false if a fetch is already in flight. */
  public static synchronized boolean tryArm(String command) {
    if (armed) {
      return false;
    }
    armed = true;
    deadline = System.currentTimeMillis() + 30000;
    menu = null;
    contentPackets = 0;
    lastTopSig = null;
    pendingCommand = command;
    lastSentMs = 0;
    attempts = 0;
    return true;
  }

  public static synchronized boolean isArmed() {
    return armed;
  }

  private static synchronized void disarm() {
    armed = false;
    menu = null;
  }

  /**
   * Intercepts a server GUI open. Returns true when swallowed (no screen).
   * Runs on the main thread (packet handler ensures it).
   */
  public static synchronized boolean handleOpen(ClientboundOpenScreenPacket packet) {
    if (!armed || menu != null) {
      return false;
    }
    if (attempts == 0 || System.currentTimeMillis() - lastSentMs > 5000) {
      return false;
    }
    Minecraft mc = Minecraft.getInstance();
    if (mc == null || mc.player == null) {
      disarm();
      return false;
    }
    Player player = mc.player;
    if (player.containerMenu.containerId != 0) {
      com.openorigins.client.util.DebugLog.info("[bgmenu] busy (window {}), skipping silent fetch", player.containerMenu.containerId);
      disarm();
      return false;
    }
    try {
      AbstractContainerMenu created =
          packet.getType().create(packet.getContainerId(), player.getInventory());
      if (created == null) {
        disarm();
        return false;
      }
      player.containerMenu = created;
      menu = created;
      Component title = packet.getTitle();
      com.openorigins.client.util.DebugLog.info("[bgmenu] opened '{}' silently (window {})",
          title == null ? "?" : title.getString(), packet.getContainerId());
    } catch (Throwable t) {
      com.openorigins.client.util.DebugLog.warn("[bgmenu] open failed: {}", t.toString());
      disarm();
      return false;
    }
    return true;
  }

  /** Runs after container content/slot packets; captures and maybe finishes. */
  public static synchronized void poll() {
    if (!armed || menu == null) {
      return;
    }
    contentPackets++;
    int top = 0;
    int filled = 0;
    StringBuilder topNames = new StringBuilder();
    try {
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      Object inv = mc != null && mc.player != null ? mc.player.getInventory() : null;
      for (net.minecraft.world.inventory.Slot s : menu.slots) {
        if (inv != null && s.container == inv) {
          continue;
        }
        top++;
        if (s.hasItem()) {
          filled++;
          if (topNames.length() < 300) {
            if (topNames.length() > 0) {
              topNames.append(" | ");
            }
            net.minecraft.world.item.ItemStack st = s.getItem();
            topNames.append(st.getHoverName().getString()).append(" x").append(st.getCount());
          }
        }
      }
    } catch (Throwable ignored) {
    }
    com.openorigins.client.util.DebugLog.info("[bgmenu] poll #{} top={} filled={} sample=[{}]", contentPackets, top, filled,
        topNames.toString());
    boolean progress = false;
    try {
      if (HomeInfoCache.captureMenu(menu) > 0) {
        progress = true;
      }
      if (OpenWorldCache.scanMenu(menu) > 0) {
        progress = true;
      }
      BadgerCache.scanMenu(menu);
      CosmeticCatalogCache.scanMenu(menu);
      if (CosmeticCatalogCache.profilePollMenu(menu)) {
        progress = true;
      }
    } catch (Throwable t) {
      com.openorigins.client.util.DebugLog.warn("[bgmenu] capture failed: {}", t.toString());
    }
    if (progress || contentPackets >= 3) {
      finish();
    }
  }

  /** Force-closes on timeout. Call from client tick. */
  public static synchronized void tick() {
    if (!armed) {
      return;
    }
    long now = System.currentTimeMillis();
    if (now > deadline) {
      com.openorigins.client.util.DebugLog.info("[bgmenu] deadline, closing");
      finish();
      return;
    }
    if (menu == null && pendingCommand != null && now - lastSentMs > 3000 && attempts < 5) {
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      if (mc != null && mc.player != null) {
        mc.player.connection.sendCommand(pendingCommand);
        lastSentMs = now;
        attempts++;
        com.openorigins.client.util.DebugLog.info("[bgmenu] sending '{}' (attempt {})", pendingCommand, attempts);
      }
    }
  }

  private static synchronized void finish() {
    AbstractContainerMenu m = menu;
    menu = null;
    armed = false;
    Minecraft mc = Minecraft.getInstance();
    if (mc == null || mc.player == null || m == null) {
      return;
    }
    try {
      int id = m.containerId;
      if (mc.player.containerMenu == m) {
        mc.player.connection.send(new ServerboundContainerClosePacket(id));
        mc.player.containerMenu = mc.player.inventoryMenu;
        try {
          m.removed(mc.player);
        } catch (Throwable ignored) {
        }
      }
      com.openorigins.client.util.DebugLog.info("[bgmenu] closed window {}", id);
    } catch (Throwable t) {
      com.openorigins.client.util.DebugLog.warn("[bgmenu] close failed: {}", t.toString());
    }
    if (mc.gui.screen() instanceof com.openorigins.client.screen.HomePanelHost host) {
      host.rebuildHomeWidgets();
    }
  }
}
