package com.openorigins.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

public final class OpenOriginsConfig {
  public enum HudCorner {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
  }

  private static final java.util.List<String> DEFAULT_SERVERS = java.util.List.of(
      "play.originrealms.com",
      "tcpshield.originrealms.com",
      "tcpshield-iad.originrealms.com",
      "spectrum-tcpshield.originrealms.com",
      "bhs.originrealms.com");

  private static final OpenOriginsConfig INSTANCE = new OpenOriginsConfig();
  private final Map<OriginFeature, Boolean> enabled = new EnumMap<>(OriginFeature.class);
  private String discordClientId = "";
  private HudCorner badgerHudCorner = HudCorner.TOP_LEFT;
  private int homeCapacity = 5;
  private int extrapMs = 150;
  private boolean badgerHudVisible = true;
  private float allayArmsShiftTexels = 12.0f;
  private int scanKeyType = 0;
  private int scanKeyValue = -1;
  private boolean useHandshake = true;
  private final java.util.Set<String> servers =
      new java.util.LinkedHashSet<>(DEFAULT_SERVERS);

  private OpenOriginsConfig() {
    for (OriginFeature f : OriginFeature.values()) enabled.put(f, f.getDefaultValue());
  }

  public static OpenOriginsConfig getInstance() { return INSTANCE; }

  public Path configPath() {
    return FabricLoader.getInstance().getConfigDir().resolve("openorigins.properties");
  }

  public synchronized void load() {
    Path p = configPath();
    if (!Files.exists(p)) {
      save();
      return;
    }
    Properties props = new Properties();
    try (InputStream in = Files.newInputStream(p)) {
      props.load(in);
      for (OriginFeature f : OriginFeature.values()) {
        String v = props.getProperty("feature." + f.name().toLowerCase());
        if (v != null) enabled.put(f, Boolean.parseBoolean(v));
      }
      String clientId = props.getProperty("discord.client.id");
      if (clientId != null) {
        discordClientId = clientId.trim();
      } else {
        save();
      }
      String corner = props.getProperty("badger.hud.corner");
      if (corner != null) {
        try {
          badgerHudCorner = HudCorner.valueOf(corner.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
        }
      }
      String vis = props.getProperty("badger.hud.visible");
      if (vis != null) {
        badgerHudVisible = Boolean.parseBoolean(vis.trim());
      }
      String cap = props.getProperty("homes.capacity");
      if (cap != null) {
        try {
          int v = Integer.parseInt(cap.trim());
          if (v >= 0 && v <= 15) {
            homeCapacity = v;
          }
        } catch (NumberFormatException ignored) {
        }
      }
      String exms = props.getProperty("extrap.ms");
      if (exms != null) {
        try {
          int v = Integer.parseInt(exms.trim());
          if (v >= 0 && v <= 1000) {
            extrapMs = v;
          }
        } catch (NumberFormatException ignored) {
        }
      }
      String scanKey = props.getProperty("scan.key");
      if (scanKey != null) {
        try {
          String[] parts = scanKey.trim().split(":");
          if (parts.length == 2) {
            scanKeyType = Math.max(0, Math.min(2, Integer.parseInt(parts[0])));
            scanKeyValue = Integer.parseInt(parts[1]);
          }
        } catch (NumberFormatException ignored) {
        }
      }
      String arms = props.getProperty("allay.arms.x");
      if (arms != null) {
        try {
          float v = Float.parseFloat(arms.trim());
          if (v >= -32.0f && v <= 32.0f) {
            allayArmsShiftTexels = v;
          }
        } catch (NumberFormatException ignored) {
        }
      }
      String servers = props.getProperty("servers");
      if (servers != null) {
        setServersText(servers);
      }
      String bypass = props.getProperty("use.handshake");
      if (bypass != null) {
        useHandshake = Boolean.parseBoolean(bypass.trim());
      }
    } catch (IOException ignored) {
    }
  }

  public synchronized void save() {
    Properties props = new Properties();
    for (Map.Entry<OriginFeature, Boolean> e : enabled.entrySet()) {
      props.setProperty("feature." + e.getKey().name().toLowerCase(), Boolean.toString(e.getValue()));
    }
    props.setProperty("discord.client.id", discordClientId);
    props.setProperty("badger.hud.corner", badgerHudCorner.name());
    props.setProperty("badger.hud.visible", Boolean.toString(badgerHudVisible));
    props.setProperty("homes.capacity", Integer.toString(homeCapacity));
    props.setProperty("extrap.ms", Integer.toString(extrapMs));
    props.setProperty("allay.arms.x", Float.toString(allayArmsShiftTexels));
    props.setProperty("scan.key", scanKeyType + ":" + scanKeyValue);
    props.setProperty("servers", getServersText());
    props.setProperty("use.handshake", Boolean.toString(useHandshake));
    try {
      Files.createDirectories(configPath().getParent());
      try (OutputStream out = Files.newOutputStream(configPath())) {
        props.store(out, "OpenOrigins 26.2 from scratch config");
      }
    } catch (IOException ignored) {
    }
  }

  public static String normalizeServerId(String s) {
    return s == null ? "" : s.trim().toLowerCase(java.util.Locale.ROOT);
  }

  public synchronized java.util.Set<String> getServers() {
    return new java.util.LinkedHashSet<>(servers);
  }

  public synchronized String getServersText() {
    return String.join(",", servers);
  }

  public synchronized void setServersText(String text) {
    servers.clear();
    if (text != null) {
      for (String part : text.split(",")) {
        String norm = normalizeServerId(part);
        if (!norm.isEmpty()) {
          servers.add(norm);
        }
      }
    }
    save();
  }

  public synchronized boolean addServer(String raw) {
    String norm = normalizeServerId(raw);
    if (norm.isEmpty() || servers.contains(norm)) {
      return false;
    }
    servers.add(norm);
    save();
    return true;
  }

  public synchronized boolean isUseHandshake() {
    return useHandshake;
  }

  public synchronized void setUseHandshake(boolean v) {
    useHandshake = v;
    save();
  }

  public synchronized void removeServer(String raw) {
    if (servers.remove(normalizeServerId(raw))) {
      save();
    }
  }

  public boolean isEnabled(OriginFeature f) { return enabled.getOrDefault(f, true); }
  public String getDiscordClientId() { return discordClientId; }
  public HudCorner getBadgerHudCorner() { return badgerHudCorner; }
  public boolean isBadgerHudVisible() { return badgerHudVisible; }
  public void toggleBadgerHudVisible() {
    badgerHudVisible = !badgerHudVisible;
    save();
  }
  public int getHomeCapacity() { return homeCapacity; }
  public float getAllayArmsShiftTexels() { return allayArmsShiftTexels; }
  public com.mojang.blaze3d.platform.InputConstants.Key getScanKey() {
    com.mojang.blaze3d.platform.InputConstants.Type type;
    switch (scanKeyType) {
      case 1 -> type = com.mojang.blaze3d.platform.InputConstants.Type.MOUSE;
      case 2 -> type = com.mojang.blaze3d.platform.InputConstants.Type.SCANCODE;
      default -> type = com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM;
    }
    try {
      return type.getOrCreate(scanKeyValue);
    } catch (Throwable t) {
      return com.mojang.blaze3d.platform.InputConstants.UNKNOWN;
    }
  }
  public void setScanKey(com.mojang.blaze3d.platform.InputConstants.Key key) {
    if (key == null) {
      scanKeyType = 0;
      scanKeyValue = -1;
    } else if (key.getType() == com.mojang.blaze3d.platform.InputConstants.Type.MOUSE) {
      scanKeyType = 1;
      scanKeyValue = key.getValue();
    } else if (key.getType() == com.mojang.blaze3d.platform.InputConstants.Type.SCANCODE) {
      scanKeyType = 2;
      scanKeyValue = key.getValue();
    } else {
      scanKeyType = 0;
      scanKeyValue = key.getValue();
    }
    save();
  }
  public void setAllayArmsShiftTexels(float v) {
    allayArmsShiftTexels = Math.max(-32.0f, Math.min(32.0f, v));
    save();
  }
  public void setHomeCapacity(int v) {
    homeCapacity = Math.max(0, Math.min(15, v));
    save();
  }
  public int getExtrapMs() { return extrapMs; }
  public void setExtrapMs(int v) {
    extrapMs = Math.max(0, Math.min(1000, v));
    save();
  }
  public void cycleBadgerHudCorner() {
    HudCorner[] values = HudCorner.values();
    badgerHudCorner = values[(badgerHudCorner.ordinal() + 1) % values.length];
    save();
  }
  public void setEnabled(OriginFeature f, boolean v) {
    enabled.put(f, v);
    save();
  }

  /** Restores every setting to its default. */
  public synchronized void resetAll() {
    for (OriginFeature f : OriginFeature.values()) enabled.put(f, f.getDefaultValue());
    discordClientId = "";
    badgerHudCorner = HudCorner.TOP_LEFT;
    homeCapacity = 5;
    badgerHudVisible = true;
    allayArmsShiftTexels = 12.0f;
    scanKeyType = 0;
    scanKeyValue = -1;
    servers.clear();
    servers.addAll(DEFAULT_SERVERS);
    useHandshake = true;
    save();
  }
}
