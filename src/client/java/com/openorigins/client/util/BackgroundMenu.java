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
  private static int windowId = -1;
  private static int contentPackets;
  private static String pendingCommand;
  private static long lastOwnSendMs;
  private static long lastForeignCmdMs;
  private static int attempts;
  private static String lastTopSig;
  private static volatile long lastEmptyFinishMs;
  private static volatile String pendingTeleport;

  private static volatile boolean expectOwnSend;

  /** Our own command send, marked so manual input is never confused. */
  public static long lastOwnSendMsForProbe() {
    return lastOwnSendMs;
  }

  /** Consumed by the packet hook to attribute the next chat command. */
  public static boolean consumeOwnSend() {
    boolean own = expectOwnSend;
    expectOwnSend = false;
    if (own) {
      lastOwnSendMs = System.currentTimeMillis();
    }
    return own;
  }

  public static void sendCommand(String command) {
    try {
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      if (mc != null && mc.player != null) {
        expectOwnSend = true;
        mc.player.connection.sendCommand(command);
      }
    } catch (Throwable ignored) {
      expectOwnSend = false;
    }
  }

  /** A chat command not sent by us (user typed it): back off. */
  public static void noteForeignCommand() {
    lastForeignCmdMs = System.currentTimeMillis();
  }

  private static boolean userActive() {
    return System.currentTimeMillis() - lastForeignCmdMs < 10000;
  }

  /** @return false if a fetch is already in flight. */
  public static synchronized boolean tryArm(String command) {
    if (armed) {
      return false;
    }
    if (!com.openorigins.client.ConnectionUtil.isConnected()) {
      return false;
    }
    armed = true;
    deadline = System.currentTimeMillis() + 30000;
    menu = null;
    windowId = -1;
    contentPackets = 0;
    lastTopSig = null;
    pendingCommand = command;
    lastOwnSendMs = 0;
    attempts = 0;
    return true;
  }

  public static synchronized boolean isArmed() {
    return armed;
  }

  public static long lastEmptyFinishMs() {
    return lastEmptyFinishMs;
  }

  /** Public disarm for connection teardown. */
  public static synchronized void disarmNow() {
    disarm();
  }

  private static synchronized void disarm() {
    armed = false;
    menu = null;
    pendingTeleport = null;
  }

  /**
   * Intercepts a server GUI open. Returns true when swallowed (no screen).
   * Runs on the main thread (packet handler ensures it).
   */
  public static synchronized boolean handleOpen(ClientboundOpenScreenPacket packet) {
    if (!armed || menu != null) {
      return false;
    }
    if (attempts == 0) {
      return false;
    }
    if (userActive()) {
      return false;
    }
    if (System.currentTimeMillis() - lastOwnSendMs > 8000) {
      return false;
    }
    Minecraft mc = Minecraft.getInstance();
    if (mc == null || mc.player == null) {
      disarm();
      return false;
    }
    Player player = mc.player;
    try {
      AbstractContainerMenu created =
          packet.getType().create(packet.getContainerId(), player.getInventory());
      if (created == null) {
        disarm();
        return false;
      }
      menu = created;
      windowId = packet.getContainerId();
      Component title = packet.getTitle();
      com.openorigins.client.util.DebugLog.info("[bgmenu] opened '{}' silently (window {})",
          title == null ? "?" : title.getString(), windowId);
    } catch (Throwable t) {
      com.openorigins.client.util.DebugLog.warn("[bgmenu] open failed: {}", t.toString());
      disarm();
      return false;
    }
    return true;
  }

  /** Routes a full content packet to the background menu. */
  public static synchronized void routeContent(int containerId, int stateId,
      java.util.List<net.minecraft.world.item.ItemStack> items,
      net.minecraft.world.item.ItemStack carried) {
    if (!armed || menu == null || containerId != windowId) {
      return;
    }
    try {
      menu.initializeContents(stateId, items, carried);
    } catch (Throwable t) {
      com.openorigins.client.util.DebugLog.warn("[bgmenu] content failed: {}", t.toString());
    }
  }

  /** Routes a single slot packet to the background menu. */
  public static synchronized void routeSlot(int containerId, int slot, int stateId,
      net.minecraft.world.item.ItemStack item) {
    if (!armed || menu == null || containerId != windowId) {
      return;
    }
    try {
      menu.setItem(slot, stateId, item);
    } catch (Throwable t) {
      com.openorigins.client.util.DebugLog.warn("[bgmenu] slot failed: {}", t.toString());
    }
  }

  public static void setPendingTeleport(String worldColor) {
    pendingTeleport = worldColor;
  }

  /** Clicks a world button inside the background menu, then closes. */
  private static synchronized boolean tryTeleportClick() {
    String color = pendingTeleport;
    if (color == null || menu == null) {
      return false;
    }
    int slot = com.openorigins.client.util.OpenWorldCache.slotFor(color);
    if (slot < 0 || slot >= menu.slots.size()) {
      return false;
    }
    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
    if (mc == null || mc.player == null || mc.gameMode == null) {
      return false;
    }
    net.minecraft.world.entity.player.Player player = mc.player;
    net.minecraft.world.inventory.AbstractContainerMenu prev = player.containerMenu;
    try {
      player.containerMenu = menu;
      mc.gameMode.handleContainerInput(menu.containerId, slot, 0,
          net.minecraft.world.inventory.ContainerInput.PICKUP, player);
      com.openorigins.client.util.DebugLog.info("[bgmenu] teleported to {} (slot {})", color, slot);
    } catch (Throwable t) {
      com.openorigins.client.util.DebugLog.warn("[bgmenu] teleport click failed: {}", t.toString());
      return false;
    } finally {
      if (player.containerMenu == menu) {
        player.containerMenu = prev;
      }
    }
    pendingTeleport = null;
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
    if (pendingTeleport != null && tryTeleportClick()) {
      finish();
      return;
    }
    if (progress || contentPackets >= 3 || stableUnchanged()) {
      finish();
    }
  }

  private static String computeTopSig() {
    StringBuilder sb = new StringBuilder();
    try {
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      Object inv = mc != null && mc.player != null ? mc.player.getInventory() : null;
      for (net.minecraft.world.inventory.Slot s : menu.slots) {
        if (inv != null && s.container == inv) {
          continue;
        }
        if (s.hasItem()) {
          sb.append(s.getItem().getHoverName().getString()).append('x')
            .append(s.getItem().getCount()).append(';');
        } else {
          sb.append('.');
        }
      }
    } catch (Throwable ignored) {
    }
    return sb.toString();
  }

  private static boolean stableUnchanged() {
    String sig = computeTopSig();
    if (sig.equals(lastTopSig)) {
      return true;
    }
    lastTopSig = sig;
    return contentPackets >= 2;
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
    if (menu == null && pendingCommand != null && now - lastOwnSendMs > 3000 && attempts < 5) {
      if (userActive()) {
        return;
      }
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      if (mc != null && mc.player != null) {
        attempts++;
        sendCommand(pendingCommand);
        com.openorigins.client.util.DebugLog.info("[bgmenu] sending '{}' (attempt {})", pendingCommand, attempts);
      }
    }
  }

  /** Closes early so a manual menu open is never blocked. */
  public static synchronized void finishIfArmed() {
    if (armed) {
      finish();
    }
  }

  private static synchronized void finish() {    AbstractContainerMenu m = menu;
    menu = null;
    armed = false;
    pendingTeleport = null;
    if (com.openorigins.client.util.HomeInfoCache.layout().isEmpty()) {
      lastEmptyFinishMs = System.currentTimeMillis();
    }
    Minecraft mc = Minecraft.getInstance();
    if (mc == null || mc.player == null || m == null) {
      return;
    }
    try {
      int id = m.containerId;
      mc.player.connection.send(new ServerboundContainerClosePacket(id));
      try {
        m.removed(mc.player);
      } catch (Throwable ignored) {
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
