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

  private static boolean armed;
  private static long deadline;
  private static boolean refetchRequested;
  private static boolean layoutFetchTried;
  private static final Set<String> TRIED =
      Collections.newSetFromMap(new ConcurrentHashMap<>());
  private static Supplier<Screen> returnTo = HomesMenuScreen::new;

  public static void start() {
    Minecraft mc = Minecraft.getInstance();
    if (mc != null && mc.player != null) {
      armed = true;
      deadline = System.currentTimeMillis() + 10000;
      mc.player.connection.sendCommand("homes");
    }
  }

  public static void start(Supplier<Screen> ret) {
    returnTo = ret;
    start();
  }

  public static boolean isArmed() {
    return armed;
  }

  /** Next opened menu re-reads slot positions from the server once names arrive. */
  public static void requestRefetch() {
    refetchRequested = true;
  }

  public static boolean consumeRefetch() {
    boolean r = refetchRequested;
    refetchRequested = false;
    return r;
  }

  /** Called with fresh tab-complete names; pulls missing details once per home. */
  public static void autoFetch(List<String> homes) {
    if (homes == null || armed) {
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
    if (!armed || System.currentTimeMillis() > deadline) {
      armed = false;
      return;
    }
    armed = false;
    Minecraft mc = Minecraft.getInstance();
    if (mc != null) {
      mc.execute(() -> {
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof com.openorigins.client.screen.HomePanelHost)) {
          if (mc.player != null) {
            mc.player.closeContainer();
          }
          mc.gui.setScreen(returnTo.get());
        }
      });
    }
  }
}
