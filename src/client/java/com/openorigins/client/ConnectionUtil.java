package com.openorigins.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/**
 * Features are active in the dev environment, after a completed handshake,
 * or on any remote server; singleplayer stays vanilla.
 */
public final class ConnectionUtil {
  private ConnectionUtil() {}

  public static boolean isConnected() {
    if (FabricLoader.getInstance().isDevelopmentEnvironment()) return true;
    if (HandshakeState.isHandshakeComplete()) return true;
    Minecraft mc = Minecraft.getInstance();
    if (mc == null || mc.getConnection() == null) return false;
    return !mc.isLocalServer();
  }
}
