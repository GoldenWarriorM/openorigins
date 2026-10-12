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

  private static String normIp(String ip) {
    if (ip == null) {
      return "";
    }
    String s = ip.trim().toLowerCase(java.util.Locale.ROOT);
    if (s.endsWith(":25565")) {
      s = s.substring(0, s.length() - 7);
    }
    return s;
  }

  /**
   * Reuses the stored ServerData from the vanilla server list when the address
   * is there, so the remembered resource-pack choice is respected instead of
   * prompting on every join.
   */
  private static net.minecraft.client.multiplayer.ServerData findStored(String ip) {
    try {
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      if (mc == null) {
        return null;
      }
      String want = normIp(ip);
      net.minecraft.client.multiplayer.ServerList list =
          new net.minecraft.client.multiplayer.ServerList(mc);
      list.load();
      for (int i = 0; i < list.size(); i++) {
        net.minecraft.client.multiplayer.ServerData d;
        try {
          d = list.get(i);
        } catch (Throwable ignored) {
          continue;
        }
        if (d != null && normIp(d.ip).equals(want)) {
          return d;
        }
      }
    } catch (Throwable ignored) {
    }
    return null;
  }

  public static void join(net.minecraft.client.gui.screens.Screen parent) {
    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
    if (mc == null) {
      return;
    }
    try {
      String ip = address();
      net.minecraft.client.multiplayer.ServerData data = findStored(ip);
      if (data == null) {
        data = new net.minecraft.client.multiplayer.ServerData("Origin Realms", ip,
            net.minecraft.client.multiplayer.ServerData.Type.OTHER);
      }
      net.minecraft.client.gui.screens.ConnectScreen.startConnecting(parent, mc,
          net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(ip),
          data, false, null);
    } catch (Throwable ignored) {
    }
  }
}
