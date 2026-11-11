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
  /** The server rate-limits chat commands: never follow the user's own send
   *  inside this window, or the answer is "you are speaking too fast". */
  private static final long USER_QUIET_MS = 2500L;
  /** A fetch is sent exactly once per arm. A retry produces a second answer,
   *  and that duplicate answer is what pops a server menu in the player's
   *  face; a lost answer only leaves the cache stale until the next trigger
   *  (join, menu open, quest completion) a minute later. */
  private static final long GIVE_UP_MS = 8000L;
  /** Right-clicks on entities (NPCs) that can open a server menu of their own. */
  private static volatile long lastEntityInteractMs;
  private static volatile String lastEntityInteractName = "?";
  private static final long ENTITY_INTERACT_GUARD_MS = 2500L;
  private static String lastSwallowedTitle = "?";
  /**
   * Answer titles seen per command. NPC menus and fetch answers are both
   * glyph-titled generic chests, so the title is the only way to tell an
   * in-flight answer from the NPC menu opened by the same click
   * (2026-10-26: /streaks answer raced the Badger click and was shown).
   */
  private static final java.util.Map<String, java.util.LinkedHashSet<String>> knownTitles =
      new java.util.HashMap<>();
  private static final int MAX_KNOWN_TITLES = 4;
  private static int attempts;
  private static String lastTopSig;
  private static volatile long lastEmptyFinishMs;
  private static volatile String pendingTeleport;

  private static volatile boolean expectOwnSend;

  /** Menu-opening command from our own UI waiting for the fetch to finish. */
  private static volatile String queuedMenuCommand;
  private static long queuedUntilMs;
  private static long lastFinishMs;
  private static final long QUEUE_TIMEOUT_MS = 10000L;
  /** Player's own command (from our UI) held while a fetch is in flight. */
  private static String queuedUserCommand;
  private static long queuedUserUntilMs;
  private static final long USER_QUEUE_TIMEOUT_MS = 15000L;
  /** The server needs a moment to accept our close before the next open. */
  private static final long CLOSE_SETTLE_MS = 250L;
  /** Back-to-back commands get "you're speaking too fast" from the server. */
  private static final long MIN_COMMAND_GAP_MS = 2000L;
  /** Breathing room after a server window the player saw has closed. */
  private static final long SHOWN_SETTLE_MS = 500L;
  private static final long RATE_LIMIT_BACKOFF_MS = 5000L;

  private static long lastCommandSentMs;
  /** Server window on screen right now, -1 when none is open. */
  private static int shownWindowId = -1;
  private static long shownWindowClosedMs;
  private static long rateLimitedUntil;

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
        sendNow(mc, command);
        OutboxStats.record("cmd /" + command, 30000L);
      }
    } catch (Throwable ignored) {
      expectOwnSend = false;
    }
  }

  /**
   * Sends a menu-opening command from our hotkeys, but only once no server
   * chest is in flight: a second /navigator while the previous answer is still
   * open makes two windows overlap, and an answer then lands in the wrong one.
   * Teleports never come here — making them wait would be worse than the bug.
   *
   * @return true when the command went out now, false when it was queued.
   */
  public static synchronized boolean requestMenuCommand(String command) {
    if (command == null || command.isBlank()) {
      return false;
    }
    Minecraft mc = Minecraft.getInstance();
    if (mc == null || mc.player == null) {
      return false;
    }
    long now = System.currentTimeMillis();
    if (!armed && canSendCommand() && now - lastFinishMs >= CLOSE_SETTLE_MS) {
      sendNow(mc, command);
      OutboxStats.record("key /" + command);
      return true;
    }
    queuedMenuCommand = command;
    queuedUntilMs = now + QUEUE_TIMEOUT_MS;
    com.openorigins.client.util.DebugLog.info(
        "[bgmenu] menu command '{}' queued: a fetch is in flight", command);
    return false;
  }

  /** Sends a queued menu command once the previous chest is really gone. */
  private static void flushQueuedMenuCommand(long now) {
    String command = queuedMenuCommand;
    if (command == null) {
      return;
    }
    if (now > queuedUntilMs) {
      queuedMenuCommand = null;
      com.openorigins.client.util.DebugLog.info("[bgmenu] queued '{}' expired", command);
      return;
    }
    if (armed || now - lastFinishMs < CLOSE_SETTLE_MS) {
      return;
    }
    Minecraft mc = Minecraft.getInstance();
    if (mc == null || mc.player == null || !canSendCommand()) {
      return;
    }
    queuedMenuCommand = null;
    sendNow(mc, command);
    OutboxStats.record("key /" + command);
    com.openorigins.client.util.DebugLog.info("[bgmenu] queued '{}' sent", command);
  }

  /** Command the background fetch is working on, or null when idle. */
  public static synchronized String inFlightCommand() {
    return armed ? pendingCommand : null;
  }

  /** True while a command the player asked for is waiting its turn. */
  public static synchronized boolean hasQueuedUserCommand() {
    return queuedUserCommand != null;
  }

  /**
   * Sends a command the player asked for from our own UI, or holds it while a
   * background fetch is in flight. Our fetch command and the player's command
   * must never reach the server inside one limiter window: the server answers
   * the later one with "you're speaking too fast" (2026-11-07: our /homes and
   * the player's /balloon landed in the same second).
   *
   * @return true when it went out now, false when it waits for the fetch.
   */
  public static synchronized boolean sendOrQueueUserCommand(String command) {
    if (command == null || command.isBlank()) {
      return false;
    }
    Minecraft mc = Minecraft.getInstance();
    if (mc == null || mc.player == null) {
      return false;
    }
    if (!armed && canSendCommand()) {
      mc.player.connection.sendCommand(command);
      OutboxStats.record("user /" + command);
      lastCommandSentMs = System.currentTimeMillis();
      return true;
    }
    queuedUserCommand = command;
    queuedUserUntilMs = System.currentTimeMillis() + USER_QUEUE_TIMEOUT_MS;
    com.openorigins.client.util.DebugLog.info(
        "[bgmenu] user '{}' queued behind '{}'", command, pendingCommand);
    return false;
  }

  /** Sends the held player command once the fetch is gone and the gap is clear. */
  private static void flushQueuedUserCommand(long now) {
    String command = queuedUserCommand;
    if (command == null) {
      return;
    }
    if (now > queuedUserUntilMs) {
      queuedUserCommand = null;
      com.openorigins.client.util.DebugLog.info("[bgmenu] queued user '{}' expired", command);
      return;
    }
    if (armed || now - lastFinishMs < CLOSE_SETTLE_MS) {
      return;
    }
    Minecraft mc = Minecraft.getInstance();
    if (mc == null || mc.player == null || !canSendCommand()) {
      return;
    }
    queuedUserCommand = null;
    mc.player.connection.sendCommand(command);
    OutboxStats.record("user /" + command);
    lastCommandSentMs = System.currentTimeMillis();
    com.openorigins.client.util.DebugLog.info("[bgmenu] queued user '{}' sent", command);
  }

  /** A chat command not sent by us (user typed it): back off. */
  public static void noteForeignCommand() {
    noteForeignCommand(null);
  }

  /**
   * Same, with the command text so menu answers can be told apart. A command
   * that opens a menu of its own ends our fetch (that answer must stay
   * visible); a plain command (home teleport, spawn...) does not, so the
   * answer already in flight is still swallowed instead of popping over the
   * player as a chest menu.
   */
  public static synchronized void noteForeignCommand(String command) {
    lastForeignCmdMs = System.currentTimeMillis();
    lastForeignCommand = command == null ? null : command.strip().toLowerCase(java.util.Locale.ROOT);
    if (!isNonMenuCommand(lastForeignCommand)) {
      finishIfArmed();
    }
  }

  private static volatile String lastForeignCommand;

  /**
   * User commands that never open a menu: an orphaned answer arriving after
   * one of these is still ours (e.g. our bg /streaks answered right after
   * the user hit /realm tp), and a fetch already in flight keeps its claim on
   * the next window (2026-11-05: closing the Badger quest and teleporting home
   * with 'home base' let the /streaks answer through as a chest menu).
   * Menu-opening commands are absent here on purpose: their answers stay
   * visible.
   */
  static boolean isNonMenuCommand(String command) {
    if (command == null || command.isEmpty()) {
      return false;
    }
    return command.equals("realm tp") || command.equals("spawn")
        || command.startsWith("balloon ")
        || command.startsWith("home ") || command.equals("home")
        || command.startsWith("delhome ") || command.startsWith("sethome ")
        || command.equals("tpa") || command.startsWith("tpa ")
        || command.equals("tpaccept");
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
  /**
   * Any GUI on screen means the user is busy with the server: a fetch started
   * now answers into their window and the swallow closes it
   * (2026-11-08: /streaks armed inside a custom block menu, its answer took
   * the block's window id, closed it and the streaks menu popped in its place).
   */
  private static boolean screenOpen() {
    try {
      Minecraft mc = Minecraft.getInstance();
      return mc != null && mc.gui != null && mc.gui.screen() != null;
    } catch (Throwable t) {
      return false;
    }
  }

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

  private static void rememberTitle(String command, String title) {
    if (command == null || command.isEmpty() || title == null || title.isEmpty()
        || "?".equals(title)) {
      return;
    }
    java.util.LinkedHashSet<String> titles =
        knownTitles.computeIfAbsent(command, k -> new java.util.LinkedHashSet<>());
    if (!titles.remove(title) && titles.size() >= MAX_KNOWN_TITLES) {
      java.util.Iterator<String> it = titles.iterator();
      it.next();
      it.remove();
    }
    titles.add(title);
  }

  private static boolean titleKnownFor(String command, String title) {
    if (command == null || title == null) {
      return false;
    }
    java.util.Set<String> titles = knownTitles.get(command);
    return titles != null && titles.contains(title);
  }

  /** A quest completed: the user is done touching containers, drop the cooldown. */
  public static synchronized void clearManualCooldown() {
    lastManualContainerMs = 0;
  }

  /** True while the given menu is our captured background menu (not the user's). */
  public static synchronized boolean isBackground(AbstractContainerMenu m) {
    return armed && m != null && m == menu;
  }

  /** True when the user asked for a menu themselves: that answer must show. */
  private static boolean userActive() {
    if (System.currentTimeMillis() - lastForeignCmdMs >= 10000) {
      return false;
    }
    return !isNonMenuCommand(lastForeignCommand);
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
    if (screenOpen()) {
      if (now - lastTryArmLogMs > 15000) {
        lastTryArmLogMs = now;
        com.openorigins.client.util.DebugLog.info("[bgmenu] tryArm '{}' refused: screen open",
            command);
      }
      return false;
    }
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
    queuedMenuCommand = null;
    queuedUntilMs = 0;
    queuedUserCommand = null;
    queuedUserUntilMs = 0;
    shownWindowId = -1;
    rateLimitedUntil = 0;
    lastCommandSentMs = 0;
  }

  /** A server window the player can see opened: commands wait for its close. */
  public static synchronized void noteShownWindow(int containerId) {
    shownWindowId = containerId;
  }

  /**
   * The server takes a command, opens a chest for its answer and only then
   * accepts the next one; a command fired over that chest is answered with
   * "you're speaking too fast" (2026-11-07: 'streaks' right over the Badger
   * menu). Everything we send goes through this one gate.
   */
  private static boolean canSendCommand() {
    long now = System.currentTimeMillis();
    if (now < rateLimitedUntil) {
      return false;
    }
    Minecraft mc = Minecraft.getInstance();
    if (mc == null || mc.player == null) {
      return false;
    }
    if (openContainerScreenShown(mc) || shownWindowOpen(mc)) {
      return false;
    }
    if (now - shownWindowClosedMs < SHOWN_SETTLE_MS) {
      return false;
    }
    return now - lastCommandSentMs >= MIN_COMMAND_GAP_MS;
  }

  /** True while the server window the player saw is still open. */
  private static synchronized boolean shownWindowOpen(Minecraft mc) {
    if (shownWindowId < 0) {
      return false;
    }
    boolean open = false;
    try {
      open = mc != null && mc.gui != null
          && mc.gui.screen()
              instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> cs
          && cs.getMenu() != null && cs.getMenu().containerId == shownWindowId;
    } catch (Throwable ignored) {
    }
    if (!open) {
      shownWindowId = -1;
      shownWindowClosedMs = System.currentTimeMillis();
    }
    return open;
  }

  private static void sendNow(Minecraft mc, String command) {
    mc.player.connection.sendCommand(command);
    lastCommandSentMs = System.currentTimeMillis();
  }

  /**
   * The server refused a command as too fast. That is a limiter, not a broken
   * area: back every send off for a moment and keep the location usable.
   */
  public static synchronized void noteRateLimited() {
    rateLimitedUntil = System.currentTimeMillis() + RATE_LIMIT_BACKOFF_MS;
    if (armed) {
      disarm();
    }
    com.openorigins.client.util.DebugLog.info(
        "[bgmenu] server rate limit hit, backing off {}ms", RATE_LIMIT_BACKOFF_MS);
  }

  /**
   * Server closed a window on its own (menu timeout). If it was ours, drop the
   * stale handle WITHOUT sending close back: the id will be recycled (the
   * server reuses 1-2-3), and our finish() must never close the window that
   * reuses it (e.g. the player's chest) nor route its content into our menu.
   */
  public static synchronized void noteServerClose(int containerId) {
    if (containerId == shownWindowId) {
      shownWindowId = -1;
      shownWindowClosedMs = System.currentTimeMillis();
    }
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
          // Only adopt an open whose title we have already seen as this
          // command's answer. A different glyph title here is the user's NPC
          // menu (2026-10-26: adopting it made the fetch guard swallow it).
          if (isGenericRows(packet.getType()) && titleKnownFor(orphanCommand, titleStr)) {
            adopted = true;
            armed = true;
            attempts = 1;
            pendingCommand = orphanCommand;
            armedAtMs = System.currentTimeMillis();
            deadline = armedAtMs + 30000;
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
          // Someone else's menu on screen: the user is browsing the server, so
          // keep fetches off until it is closed and the cooldown runs out.
          lastManualContainerMs = now;
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
    boolean knownAnswer = titleKnownFor(pendingCommand, titleStr);
    String verdict;
    boolean skipUserChecks;
    try {
      skipUserChecks = knownAnswer || (armed && attempts > 0 && menu == null
          && System.currentTimeMillis() - lastManualContainerMs < 15000
          && openContainerScreenShown(mc));
    } catch (Throwable t) {
      skipUserChecks = knownAnswer;
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
    } else if (afterEntityInteraction(now) && !knownAnswer) {
      // The user just clicked an NPC: this open is the NPC's menu, not our
      // fetch answer. Do not swallow it. A fetch answer that races the click
      // is recognised by its title and swallowed below instead.
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
          rememberTitle(pendingCommand, titleStr);
          verdict = afterEntityInteraction(now) ? "swallowed-known-answer" : "swallowed";
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

  private static long lastTeleportMissLogMs;

  private static void logTeleportMiss(String format, Object... args) {
    long now = System.currentTimeMillis();
    if (now - lastTeleportMissLogMs < 2000L) {
      return;
    }
    lastTeleportMissLogMs = now;
    com.openorigins.client.util.DebugLog.warn(format, args);
  }

  /** Clicks a world button inside the background menu, then closes. */
  private static synchronized boolean tryTeleportClick() {
    String color = pendingTeleport;
    if (color == null || menu == null) {
      return false;
    }
    // Only ever click a slot in the navigator menu the click was armed for.
    // 2026-10-20: a queued world click leaked into a /homes menu and pressed
    // a random slot (world teleport fired 64s late, after the wrong menu).
    if (!"navigator worlds".equals(pendingCommand)) {
      logTeleportMiss("[bgmenu] teleport to {} skipped: menu is '{}'", color, pendingCommand);
      return false;
    }
    int slot = com.openorigins.client.util.OpenWorldCache.slotFor(color);
    if (slot < 0 || slot >= menu.slots.size()) {
      logTeleportMiss("[bgmenu] teleport to {}: slot {} not in menu (size {})",
          color, slot, menu.slots.size());
      return false;
    }
    String expected = com.openorigins.client.util.OpenWorldCache.worldName(color);
    String actual = null;
    try {
      actual = menu.getSlot(slot).getItem().getHoverName().getString();
    } catch (Throwable ignored) {
      // fall through: no name check possible
    }
    if (actual == null || actual.isBlank()) {
      return false;
    }
    if (expected != null && !actual.equalsIgnoreCase(expected)
        && !actual.toLowerCase(java.util.Locale.ROOT)
            .contains(expected.toLowerCase(java.util.Locale.ROOT))) {
      logTeleportMiss("[bgmenu] teleport to {} aborted: slot {} holds '{}'",
          color, slot, actual);
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
    long now = System.currentTimeMillis();
    flushQueuedMenuCommand(now);
    flushQueuedUserCommand(now);
    if (!armed) {
      return;
    }
    if (now > deadline) {
      com.openorigins.client.util.DebugLog.info("[bgmenu] deadline, closing");
      finish();
      return;
    }
    if (menu == null && pendingCommand != null) {
      if (attempts > 0) {
        // One send per arm. No resend: an unanswered command deserves a later
        // trigger, not a second window that has to be told apart from the
        // first answer. The orphan claim keeps a late answer swallowed.
        if (now - lastOwnSendMs > GIVE_UP_MS) {
          com.openorigins.client.util.DebugLog.info(
              "[bgmenu] no answer to '{}' in {}ms, giving up", pendingCommand, GIVE_UP_MS);
          disarm();
        }
        return;
      }
      if (userActive()) {
        return;
      }
      if (now - lastForeignCmdMs < USER_QUIET_MS) {
        // The user's own command just went out (a home teleport, a chest they
        // opened): ours would land in the same burst and get rate-limited.
        return;
      }
      if (afterEntityInteraction(now)) {
        // The user is opening an NPC menu right now; a fetch sent here would
        // race its container (and the wrong window would be swallowed).
        return;
      }
      // Never fire a fetch command while a server window is open: the answer
      // would race it and the server answers "speaking too fast".
      if (canSendCommand()) {
        attempts = 1;
        sendCommand(pendingCommand);
        com.openorigins.client.util.DebugLog.info("[bgmenu] sending '{}'", pendingCommand);
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
    lastFinishMs = System.currentTimeMillis();
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
