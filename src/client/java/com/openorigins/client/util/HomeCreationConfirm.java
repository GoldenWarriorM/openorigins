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

  private static volatile String pending;
  private static volatile long pendingAt;

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
      com.openorigins.client.screen.SetHomeScreen.onServerResult(true, name);
      refresh();
      return;
    }
    Matcher fail = CREATE_FAILED.matcher(text);
    if (fail.find()) {
      pending = null;
      com.openorigins.client.screen.SetHomeScreen.onServerResult(false, text.trim());
      refresh();
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
}
