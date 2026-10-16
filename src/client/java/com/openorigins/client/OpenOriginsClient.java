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
    try {
      String jar = OpenOriginsClient.class.getProtectionDomain().getCodeSource()
          .getLocation().toURI().getPath();
      java.io.File f = new java.io.File(jar);
      com.openorigins.client.util.DebugLog.info("[OpenOrigins] build mtime={} size={}", f.lastModified(), f.length());
    } catch (Throwable ignored) {
    }
    OpenOriginsConfig.getInstance().load();
    com.openorigins.client.util.HomeInfoCache.load();
    com.openorigins.client.util.BadgerCache.load();
    com.openorigins.client.util.CosmeticCatalogCache.load();
    com.openorigins.client.util.AuctionPriceDb.load();
    com.openorigins.client.util.HelmetCosmeticTracker.loadHelmet();
    OENetwork.setup();
    OriginStateTracker.INSTANCE.setup();
    RemoteRegistry.get().fetchAsync();
    FeatureRegistry.registerAll();
    com.openorigins.client.util.DebugLog.info("[OpenOrigins] initialized: dev={}, connected={}", net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment(), ConnectionUtil.isConnected());
    if (Minecraft.getInstance() == null) com.openorigins.client.util.DebugLog.info("[OpenOrigins] pre-window init OK");
  }
}
