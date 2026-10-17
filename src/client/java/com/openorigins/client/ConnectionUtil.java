package com.openorigins.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/**
 * Features are active in the dev environment, on handshake-verified servers,
 * or on any remote server allowed by the server allowlist; singleplayer and
 * offline states stay vanilla. A stale handshake flag alone (e.g. after a
 * cancelled connection) never counts without a live remote connection.
 */
public final class ConnectionUtil {
  private ConnectionUtil() {}

  /** Cached set copy: the allowlist rarely changes but was rebuilt per call. */
  private static volatile java.util.Set<String> cachedAllowed =
      OpenOriginsConfig.getInstance().getServers();

  /** Called on config reload so the cached allowlist copy is rebuilt. */
  public static void invalidateServerCache() {
    cachedAllowed = OpenOriginsConfig.getInstance().getServers();
  }

  public static boolean isConnected() {
    if (FabricLoader.getInstance().isDevelopmentEnvironment()) return true;
    Minecraft mc = Minecraft.getInstance();
    if (mc == null || mc.getConnection() == null) return false;
    if (mc.isLocalServer()) return false;
    if (HandshakeState.isHandshakeComplete()
        && OpenOriginsConfig.getInstance().isUseHandshake()) return true;
    java.util.Set<String> allowed = cachedAllowed;
    if (allowed.isEmpty()) return true;
    net.minecraft.client.multiplayer.ServerData server = mc.getCurrentServer();
    return server != null && allowed.contains(OpenOriginsConfig.normalizeServerId(server.ip));
  }

  /** Human-readable id of the current server, or "" when none. */
  public static String currentServerId() {
    try {
      Minecraft mc = Minecraft.getInstance();
      if (mc == null) return "";
      net.minecraft.client.multiplayer.ServerData server = mc.getCurrentServer();
      if (server == null || server.ip == null) return "";
      return OpenOriginsConfig.normalizeServerId(server.ip);
    } catch (Throwable t) {
      return "";
    }
  }
}
