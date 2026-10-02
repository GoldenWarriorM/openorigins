package com.openorigins.client;

import com.openorigins.client.net.OENetwork;
import com.openorigins.client.server.OriginStateTracker;
import com.openorigins.client.server.RemoteRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenOriginsClient implements ClientModInitializer {
  public static final String MOD_ID = "openorigins";
  private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

  @Override
  public void onInitializeClient() {
    OpenOriginsConfig.getInstance().load();
    OENetwork.setup();
    OriginStateTracker.INSTANCE.setup();
    RemoteRegistry.get().fetchAsync();
    FeatureRegistry.registerAll();
    LOGGER.info("[OpenOrigins] initialized: dev={}, connected={}", net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment(), ConnectionUtil.isConnected());
    if (Minecraft.getInstance() == null) LOGGER.info("[OpenOrigins] pre-window init OK");
  }
}
