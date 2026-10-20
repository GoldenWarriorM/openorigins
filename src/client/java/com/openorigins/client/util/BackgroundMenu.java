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
  private static long armedAtMs;
  private static AbstractContainerMenu menu;
  private static int windowId = -1;
  private static int contentPackets;
  private static String pendingCommand;
  private static long lastOwnSendMs;
  private static long lastForeignCmdMs;
  private static long lastManualContainerMs;
  private static long lastTryArmLogMs;
  private static long lastMismatchLogMs;
  private static final long MANUAL_COOLDOWN_MS = 15000;
  /** Right-clicks on entities (NPCs) that can open a server menu of their own. */
  private static volatile long lastEntityInteractMs;
  private static volatile String lastEntityInteractName = "?";
  private static final long ENTITY_INTERACT_GUARD_MS = 2500L;
  private static String lastSwallowedTitle = "?";
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
        OutboxStats.record("cmd /" + command, 30000L);
      }
    } catch (Throwable ignored) {
      expectOwnSend = false;
    }
  }

  /** A chat command not sent by us (user typed it): back off. */
  public static void noteForeignCommand() {
    noteForeignCommand(null);
  }

  /** Same, with the command text so menu answers can be told apart. */
  public static synchronized void noteForeignCommand(String command) {
    lastForeignCmdMs = System.currentTimeMillis();
    lastForeignCommand = command == null ? null : command.strip().toLowerCase(java.util.Locale.ROOT);
  }

  private static volatile String lastForeignCommand;

  /**
   * User commands that never open a menu: an orphaned answer arriving after
   * one of these is still ours (e.g. our bg /streaks answered right after
   * the user hit /realm tp). Menu-opening commands are absent here on
   * purpose: their answers stay visible.
   */
  private static boolean isNonMenuCommand(String command) {
    if (command == null || command.isEmpty()) {
      return false;
    }
    return command.equals("realm tp") || command.equals("spawn")
        || command.startsWith("balloon ");
  }

  /** A late answer to our own send counts as ours when the user only typed
   * non-menu commands (or nothing) since. */
  private static synchronized boolean lateOrphanOurs() {
    long now = System.currentTimeMillis();
    if (now - lastOwnSendMs > 60000) {
      return false;
    }
    if (now - lastForeignCmdMs > 60000) {
      return true;
    }
    if (lastForeignCommand != null && (lastForeignCommand.equals("streaks")
        || lastForeignCommand.equals("homes") || lastForeignCommand.startsWith("navigator")
        || lastForeignCommand.equals("profile"))) {
      // The user asked for a menu themselves: its answer stays visible.
      return false;
    }
    return isNonMenuCommand(lastForeignCommand);
  }

  private static volatile String deadZoneLocation;
  private static volatile long deadZoneUntil;
  private static volatile long lastSuppressedErrorMs;
  private static final long DEAD_ZONE_MS = 300000;

  /**
   * The server answered a recent send of ours with vanilla "unknown command"
   * (boss rooms, intermission, server finder...). Kills the fetch and marks
   * the location command-dead for a while, so we stop spamming failing
   * commands there.
   */
  public static synchronized void noteCommandError() {
    noteCommandError(DEAD_ZONE_MS);
  }

  public static synchronized void noteCommandError(long cooldownMs) {
    lastSuppressedErrorMs = System.currentTimeMillis();
    if (armed) {
      com.openorigins.client.util.DebugLog.info("[bgmenu] command failed in this area, disarming '{}'",
          pendingCommand);
      disarm();
    }
    try {
      String loc = com.openorigins.client.server.OriginStateTracker.INSTANCE.getLocation();
      deadZoneLocation = loc;
      deadZoneUntil = System.currentTimeMillis() + cooldownMs;
      com.openorigins.client.util.DebugLog.info("[bgmenu] dead zone '{}' for {}s", loc,
          cooldownMs / 1000);
    } catch (Throwable ignored) {
    }
  }

  /** True right after we suppressed a command error (its context line follows). */
  public static boolean isAfterSuppressedError() {
    return System.currentTimeMillis() - lastSuppressedErrorMs < 1500;
  }

  /** True when a vanilla command error right now is attributable to our send. */
  public static boolean isServerErrorOurs() {
    long now = System.currentTimeMillis();
    return now - lastOwnSendMs < 3000 && now - lastForeignCmdMs > 3000;
  }

  public static boolean isInDeadZone() {
    return inDeadZone();
  }

  private static boolean inDeadZone() {
    try {
      if (System.currentTimeMillis() > deadZoneUntil || deadZoneLocation == null) {
        return false;
      }
      String loc = com.openorigins.client.server.OriginStateTracker.INSTANCE.getLocation();
      return deadZoneLocation.equals(loc);
    } catch (Throwable t) {
      return false;
    }
  }

  /**
   * The user touched a real container (a shown server window or a click in
   * one). Background fetches must never compete with that: disarm in flight,
   * and hold off new fetches for a cooldown. The server recycles window ids
   * (1-2-3), so any overlap risks clicks landing in the wrong window and
   * mass rollbacks on close.
   */
  public static synchronized void noteManualContainer() {
    lastManualContainerMs = System.currentTimeMillis();
    if (armed) {
      com.openorigins.client.util.DebugLog.info("[bgmenu] manual container activity, disarming '{}'",
          pendingCommand);
      disarm();
    }
  }

  /**
   * The user right-clicked an entity. NPC menus arrive as generic, glyph-titled
   * containers — exactly like our fetch answers — so the interaction is the
   * only reliable signal that the next open belongs to the user. Never swallow
   * the first open inside the guard window: on 2026-10-20 a pending /streaks
   * fetch was armed in the same tick as the Badger NPC click, swallowed the
   * task menu, closed its window id, and then the real streaks answer showed
   * up in its place.
   */
  public static synchronized void noteEntityInteraction(net.minecraft.world.entity.Entity entity) {
    lastEntityInteractMs = System.currentTimeMillis();
    lastEntityInteractName = entity == null ? "?" : String.valueOf(entity.getType());
    if (armed) {
      com.openorigins.client.util.DebugLog.info(
          "[bgmenu] entity interact {} while armed '{}' (its response stays background)",
          lastEntityInteractName, pendingCommand);
    }
  }

  private static boolean afterEntityInteraction(long now) {
    return now - lastEntityInteractMs < ENTITY_INTERACT_GUARD_MS;
  }

  /** A quest completed: the user is done touching containers, drop the cooldown. */
  public static synchronized void clearManualCooldown() {
    lastManualContainerMs = 0;
  }

  /** True while the given menu is our captured background menu (not the user's). */
  public static synchronized boolean isBackground(AbstractContainerMenu m) {
    return armed && m != null && m == menu;
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
    long now = System.currentTimeMillis();
    if (now - lastManualContainerMs < MANUAL_COOLDOWN_MS) {
      if (now - lastTryArmLogMs > 15000) {
        lastTryArmLogMs = now;
        com.openorigins.client.util.DebugLog.info("[bgmenu] tryArm '{}' refused: manual container {}s ago",
            command, (now - lastManualContainerMs) / 1000);
      }
      return false;
    }
    if (afterEntityInteraction(now)) {
      if (now - lastTryArmLogMs > 15000) {
        lastTryArmLogMs = now;
        com.openorigins.client.util.DebugLog.info(
            "[bgmenu] tryArm '{}' refused: entity interaction with {} {}ms ago",
            command, lastEntityInteractName, now - lastEntityInteractMs);
      }
      return false;
    }
    if (inDeadZone()) {
      if (now - lastTryArmLogMs > 15000) {
        lastTryArmLogMs = now;
        com.openorigins.client.util.DebugLog.info("[bgmenu] tryArm '{}' refused: dead zone '{}'",
            command, deadZoneLocation);
      }
      return false;
    }
    armed = true;
    armedAtMs = now;
    deadline = now + 30000;
    menu = null;
    windowId = -1;
    contentPackets = 0;
    lastTopSig = null;
    lastSwallowedTitle = "?";
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

  /**
   * Server closed a window on its own (menu timeout). If it was ours, drop the
   * stale handle WITHOUT sending close back: the id will be recycled (the
   * server reuses 1-2-3), and our finish() must never close the window that
   * reuses it (e.g. the player's chest) nor route its content into our menu.
   */
  public static synchronized void noteServerClose(int containerId) {
    if (armed && menu != null && containerId == windowId) {
      com.openorigins.client.util.DebugLog.info("[bgmenu] server closed window {}, dropping",
          containerId);
      disarm();
    }
    OutboxStats.ackExact("close w" + containerId);
  }

  private static String orphanCommand;
  private static long orphanUntilMs;

  private static synchronized void disarm() {
    if (armed && attempts > 0 && menu == null && pendingCommand != null) {
      orphanCommand = pendingCommand;
      orphanUntilMs = System.currentTimeMillis() + 60000;
    } else {
      orphanCommand = null;
      orphanUntilMs = 0;
    }
    armed = false;
    menu = null;
    pendingTeleport = null;
  }

  /**
   * Intercepts a server GUI open. Returns true when swallowed (no screen).
   * Runs on the main thread (packet handler ensures it).
   */
  public static synchronized boolean handleOpen(ClientboundOpenScreenPacket packet) {
    long now = System.currentTimeMillis();
    int id;
    try {
      id = packet.getContainerId();
    } catch (Throwable t) {
      return false;
    }
    String titleStr;
    try {
      Component t = packet.getTitle();
      titleStr = t == null ? "?" : t.getString();
    } catch (Throwable t) {
      titleStr = "?";
    }
    if (titleStr.length() > 48) {
      titleStr = titleStr.substring(0, 48);
    }
    String typeStr;
    try {
      typeStr = net.minecraft.core.registries.BuiltInRegistries.MENU
          .getKey(packet.getType()).toString();
    } catch (Throwable t) {
      try {
        typeStr = packet.getType().getClass().getSimpleName();
      } catch (Throwable t2) {
        typeStr = "?";
      }
    }
    if (!armed || menu != null) {
      boolean adopted = false;
      if (!armed && menu == null && orphanCommand != null
          && System.currentTimeMillis() < orphanUntilMs
          && (!userActive() || lateOrphanOurs())) {
        try {
          if (isGenericRows(packet.getType()) && !looksUserFacing(titleStr)) {
            adopted = true;
            armed = true;
            attempts = 1;
            pendingCommand = orphanCommand;
            deadline = System.currentTimeMillis() + 30000;
            contentPackets = 0;
            lastTopSig = null;
            orphanCommand = null;
            orphanUntilMs = 0;
          } else {
            orphanCommand = null;
            orphanUntilMs = 0;
          }
        } catch (Throwable t) {
          orphanCommand = null;
          orphanUntilMs = 0;
        }
      }
      if (!adopted) {
        if (armed && menu != null) {
          // A second open while our window is still captured: the server has
          // replaced it. This is exactly what a wrong swallow looks like
          // (task menu swallowed, streaks answer shown in its place).
          com.openorigins.client.util.DebugLog.warn(
              "[bgmenu] open window={} title='{}' while ours={} '{}' is captured"
                  + " -> dropping ours without close, shown (possible wrong swallow)",
              id, titleStr, windowId, lastSwallowedTitle);
          // Our id may now belong to the user's window: never close it.
          disarm();
        } else {
          com.openorigins.client.util.DebugLog.info(
              "[bgmenu] open window={} type={} title='{}' verdict=show (idle)",
              id, typeStr, titleStr);
        }
        return false;
      }
      com.openorigins.client.util.DebugLog.info(
          "[bgmenu] open window={} type={} title='{}' verdict=adopted-orphan", id, typeStr,
          titleStr);
    }
    Minecraft mc = Minecraft.getInstance();
    String verdict;
    boolean skipUserChecks;
    try {
      skipUserChecks = armed && attempts > 0 && menu == null
          && System.currentTimeMillis() - lastManualContainerMs < 15000
          && openContainerScreenShown(mc);
    } catch (Throwable t) {
      skipUserChecks = false;
    }
    boolean swallow = false;
    if (attempts == 0) {
      verdict = "no-attempts";
    } else if (!skipUserChecks && userActive()) {
      verdict = "user-cmd";
      disarm();
    } else if (!skipUserChecks && lastManualContainerMs > armedAtMs) {
      verdict = "manual-activity";
      disarm();
    } else if (mc == null || mc.player == null) {
      verdict = "no-player";
      disarm();
    } else if (!skipUserChecks && openContainerScreenShown(mc)) {
      // A real server window is on screen: showing the response honestly
      // beats swallowing it (the shown window may already be dead
      // server-side, and clicks into it would all be rejected).
      verdict = "screen-open";
      disarm();
    } else if (afterEntityInteraction(now)) {
      // The user just clicked an NPC: this open is the NPC's menu, not our
      // fetch answer. Do not swallow it; the real answer is adopted as an
      // orphan and stays invisible.
      verdict = "entity-interact";
    } else if (!isGenericRows(packet.getType())) {
      verdict = "not-generic";
    } else if (looksUserFacing(titleStr)) {
      // Server GUIs use glyph-only titles; readable words mean a real
      // container (realm chests arrive as generic type too).
      verdict = "readable-title";
    } else {
      Player player = mc.player;
      try {
        AbstractContainerMenu created =
            packet.getType().create(packet.getContainerId(), player.getInventory());
        if (created == null) {
          verdict = "create-null";
          disarm();
        } else {
          menu = created;
          windowId = packet.getContainerId();
          lastSwallowedTitle = titleStr;
          verdict = "swallowed";
          swallow = true;
          OutboxStats.ackExact("cmd /" + pendingCommand);
        }
      } catch (Throwable t) {
        com.openorigins.client.util.DebugLog.warn("[bgmenu] open failed: {}", t.toString());
        verdict = "open-failed";
        disarm();
      }
    }
    com.openorigins.client.util.DebugLog.info(
        "[bgmenu] open window={} type={} title='{}' verdict={}", id, typeStr, titleStr, verdict);
    return swallow;
  }

  /** Server GUI menus are generic chest rows; real block containers have own types. */
  private static boolean isGenericRows(net.minecraft.world.inventory.MenuType<?> type) {
    return type == net.minecraft.world.inventory.MenuType.GENERIC_9x1
        || type == net.minecraft.world.inventory.MenuType.GENERIC_9x2
        || type == net.minecraft.world.inventory.MenuType.GENERIC_9x3
        || type == net.minecraft.world.inventory.MenuType.GENERIC_9x4
        || type == net.minecraft.world.inventory.MenuType.GENERIC_9x5
        || type == net.minecraft.world.inventory.MenuType.GENERIC_9x6;
  }

  /** Readable ASCII words in the title mean a user-facing container. */
  private static boolean looksUserFacing(String titleStr) {
    for (int i = 0; i < titleStr.length(); i++) {
      char c = titleStr.charAt(i);
      if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
        return true;
      }
    }
    return false;
  }

  /** Routes a full content packet to the background menu. */
  public static synchronized void routeContent(int containerId, int stateId,
      java.util.List<net.minecraft.world.item.ItemStack> items,
      net.minecraft.world.item.ItemStack carried) {
    if (!armed || menu == null || containerId != windowId) {
      if (armed && menu != null && containerId != windowId) {
        logMismatchThrottled(containerId);
      }
      return;
    }
    try {
      menu.initializeContents(stateId, items, carried);
    } catch (Throwable t) {
      com.openorigins.client.util.DebugLog.warn("[bgmenu] content failed: {}", t.toString());
    }
  }

  private static void logMismatchThrottled(int containerId) {
    long now = System.currentTimeMillis();
    if (now - lastMismatchLogMs < 2000) {
      return;
    }
    lastMismatchLogMs = now;
    com.openorigins.client.util.DebugLog.info(
        "[bgmenu] ignoring packets for window {} (ours {}): id reuse or foreign window",
        containerId, windowId);
  }
  /** Routes a single slot packet to the background menu. */
  public static synchronized void routeSlot(int containerId, int slot, int stateId,
      net.minecraft.world.item.ItemStack item) {
    if (!armed || menu == null || containerId != windowId) {
      if (armed && menu != null && containerId != windowId) {
        logMismatchThrottled(containerId);
      }
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
      OutboxStats.record("click " + color + " #" + slot, 15000L);
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
      com.openorigins.client.util.AuctionPriceDb.scanMenu(menu, windowId);
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
      if (afterEntityInteraction(now)) {
        // The user is opening an NPC menu right now; a fetch sent here would
        // race its container (and the wrong window would be swallowed).
        return;
      }
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      // Never fire a fetch command while the user looks at a real server
      // window: the response would race it (flash over the chest, or worse,
      // a swallowed window the server still treats as open).
      if (mc != null && mc.player != null && !openContainerScreenShown(mc)) {
        attempts++;
        sendCommand(pendingCommand);
        com.openorigins.client.util.DebugLog.info("[bgmenu] sending '{}' (attempt {})", pendingCommand, attempts);
      }
    }
  }

  /** True while a nonzero server-window screen is shown (survival inventory excluded). */
  private static boolean openContainerScreenShown(Minecraft mc) {
    try {
      if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> cs
          && cs.getMenu() != null && cs.getMenu().containerId != 0) {
        return true;
      }
    } catch (Throwable ignored) {
    }
    return false;
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
      OutboxStats.record("close w" + id, 5000L);
      try {
        m.removed(mc.player);
      } catch (Throwable ignored) {
      }
      com.openorigins.client.util.DebugLog.info("[bgmenu] closed window {} (title '{}')", id,
          lastSwallowedTitle);
    } catch (Throwable t) {
      com.openorigins.client.util.DebugLog.warn("[bgmenu] close failed: {}", t.toString());
    }
    if (mc.gui.screen() instanceof com.openorigins.client.screen.HomePanelHost host) {
      host.rebuildHomeWidgets();
    }
  }
}
