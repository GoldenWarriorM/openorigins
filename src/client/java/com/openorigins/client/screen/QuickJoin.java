package com.openorigins.client.screen;

import com.openorigins.client.OpenOriginsConfig;

/** One-click join to the first allowlisted server (Origin Realms by default). */
public final class QuickJoin {
  private QuickJoin() {
  }

  public static String address() {
    try {
      java.util.Iterator<String> it = OpenOriginsConfig.getInstance().getServers().iterator();
      if (it.hasNext()) {
        String s = it.next();
        if (s != null && !s.isEmpty()) {
          return s;
        }
      }
    } catch (Throwable ignored) {
    }
    return "play.originrealms.com";
  }

  public static void join(net.minecraft.client.gui.screens.Screen parent) {
    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
    if (mc == null) {
      return;
    }
    try {
      String ip = address();
      net.minecraft.client.multiplayer.ServerData data =
          new net.minecraft.client.multiplayer.ServerData("Origin Realms", ip,
              net.minecraft.client.multiplayer.ServerData.Type.OTHER);
      net.minecraft.client.gui.screens.ConnectScreen.startConnecting(parent, mc,
          net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(ip),
          data, false, null);
    } catch (Throwable ignored) {
    }
  }
}
