package com.openorigins.client.util;

import com.openorigins.client.screen.HomesMenuScreen;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Silent server-menu fetch for home details: opens {@code /homes}, captures
 * lore via container packets, then returns to the requesting screen.
 */
public final class HomeFetch {
  private HomeFetch() {}

  private static boolean refetchRequested;
  private static boolean layoutFetchTried;
  private static final Set<String> TRIED =
      Collections.newSetFromMap(new ConcurrentHashMap<>());
  private static Supplier<Screen> returnTo = HomesMenuScreen::new;

  public static void start() {
    if (!BackgroundMenu.tryArm()) {
      return;
    }
    lastFetchMs = System.currentTimeMillis();
    Minecraft mc = Minecraft.getInstance();
    if (mc != null && mc.player != null) {
      mc.player.connection.sendCommand("homes");
    }
  }

  private static volatile long lastFetchMs;

  /** Background refresh if data missing or older than a minute. */
  public static void ensureFresh() {
    if (BackgroundMenu.isArmed()) {
      return;
    }
    if (HomeInfoCache.layout().isEmpty()
        || System.currentTimeMillis() - lastFetchMs > 60000L) {
      start();
    }
  }

  public static void start(Supplier<Screen> ret) {
    returnTo = ret;
    start();
  }

  public static boolean isArmed() {
    return BackgroundMenu.isArmed();
  }

  /** Next opened menu re-reads slot positions from the server once names arrive. */
  public static void requestRefetch() {
    refetchRequested = true;
  }

  /** Deferred silent refresh, executed when no screen is open. */
  private static boolean idleRefresh;

  public static void requestIdleRefresh() {
    idleRefresh = true;
  }

  public static boolean consumeIdleRefresh() {
    boolean r = idleRefresh;
    idleRefresh = false;
    return r;
  }

  public static boolean consumeRefetch() {
    boolean r = refetchRequested;
    refetchRequested = false;
    return r;
  }

  /** Called with fresh tab-complete names; pulls missing details once per home. */
  public static void autoFetch(List<String> homes) {
    if (homes == null || BackgroundMenu.isArmed()) {
      return;
    }
    boolean need = HomeInfoCache.layout().isEmpty() && !layoutFetchTried;
    for (String n : homes) {
      String k = HomeInfoCache.key(n);
      if (!HomeInfoCache.has(n) && TRIED.add(k)) {
        need = true;
      }
    }
    if (need) {
      layoutFetchTried = true;
      start();
    }
  }

  /** Called when home lore is captured; returns to the requesting menu. */
  public static void onCaptured() {
    Minecraft mc = Minecraft.getInstance();
    if (mc != null) {
      mc.execute(() -> {
        if (mc.gui.screen() instanceof com.openorigins.client.screen.HomePanelHost host) {
          host.rebuildHomeWidgets();
        }
      });
    }
  }
}
