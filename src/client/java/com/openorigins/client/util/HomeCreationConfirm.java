package com.openorigins.client.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Gates newly created homes on the server's chat confirmation
 * ("You have created a new home, X!").
 */
public final class HomeCreationConfirm {
  private HomeCreationConfirm() {}

  private static final Pattern CREATED =
      Pattern.compile("You have created a new home,\\s*(.+?)!\\s*$");
  private static final Pattern CREATE_FAILED =
      Pattern.compile("You cannot create a home here!");
  private static final Pattern NO_HOME =
      Pattern.compile("You don't have a home with the name (.+?)!\\s*$");
  private static final Pattern DELETED =
      Pattern.compile("You've deleted your home,\\s*(.+?)!\\s*$");

  private static volatile String pending;
  private static volatile long pendingAt;
  private static final java.util.Set<String> BLOCKED =
      java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

  private static String norm(String name) {
    return name == null ? "" : name.strip().toLowerCase(java.util.Locale.ROOT);
  }

  /** Names the server disowned: never display until it confirms them. */
  public static boolean isBlocked(String name) {
    return BLOCKED.contains(norm(name));
  }

  public static void expect(String name) {
    pending = name;
    pendingAt = System.currentTimeMillis();
  }

  public static void dropPending() {
    pending = null;
  }

  /** @return true if the name is still awaiting confirmation (hide it). */
  public static boolean isPending(String name) {    String p = pending;
    if (p == null || !p.equalsIgnoreCase(name)) {
      return false;
    }
    if (System.currentTimeMillis() - pendingAt > 15000) {
      pending = null;
      return false;
    }
    return true;
  }

  public static void onChat(String text) {
    if (text == null) {
      return;
    }
    Matcher ok = CREATED.matcher(text);
    if (ok.find()) {
      String name = ok.group(1).trim();
      if (pending != null && pending.equalsIgnoreCase(name)) {
        pending = null;
      }
      BLOCKED.remove(norm(name));
      com.openorigins.client.util.HomeInfoCache.untomb(name);
      if (com.openorigins.client.util.HomeInfoCache.addName(name)) {
        refreshAdd(name);
      } else {
        refresh();
      }
      com.openorigins.client.screen.SetHomeScreen.onServerResult(true, name);
      return;
    }
    Matcher fail = CREATE_FAILED.matcher(text);
    if (fail.find()) {
      if (pending != null) {
        BLOCKED.add(norm(pending));
        pending = null;
      }
      com.openorigins.client.screen.SetHomeScreen.onServerResult(false, text.trim());
      refresh();
      return;
    }
    Matcher no = NO_HOME.matcher(text);
    if (no.find()) {
      BLOCKED.add(norm(no.group(1).trim()));
      refresh();
      return;
    }
    Matcher del = DELETED.matcher(text);
    if (del.find()) {
      com.openorigins.client.util.HomeInfoCache.removeName(del.group(1).trim());
      refreshAfterDelete(del.group(1).trim());
    }
  }

  private static void refresh() {
    Minecraft mc = Minecraft.getInstance();
    if (mc != null) {
      mc.execute(() -> {
        if (mc.gui.screen() instanceof com.openorigins.client.screen.HomePanelHost host) {
          host.rebuildHomeWidgets();
        }
      });
    }
  }

  private static void refreshAfterDelete(String name) {
    Minecraft mc = Minecraft.getInstance();
    if (mc != null) {
      mc.execute(() -> {
        if (mc.gui.screen() instanceof com.openorigins.client.screen.HomePanelHost host) {
          host.forgetHome(name);
        }
      });
    }
  }

  private static void refreshAdd(String name) {
    Minecraft mc = Minecraft.getInstance();
    if (mc != null) {
      mc.execute(() -> {
        if (mc.gui.screen() instanceof com.openorigins.client.screen.HomePanelHost host) {
          host.addHome(name);
        }
      });
    }
  }
}
