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

  private static final OpenOriginsConfig INSTANCE = new OpenOriginsConfig();
  private final Map<OriginFeature, Boolean> enabled = new EnumMap<>(OriginFeature.class);
  private String discordClientId = "";
  private HudCorner badgerHudCorner = HudCorner.TOP_LEFT;
  private int homeCapacity = 5;
  private boolean badgerHudVisible = true;
  private float allayArmsShiftTexels = -12.0f;

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
    props.setProperty("allay.arms.x", Float.toString(allayArmsShiftTexels));
    try {
      Files.createDirectories(configPath().getParent());
      try (OutputStream out = Files.newOutputStream(configPath())) {
        props.store(out, "OpenOrigins 26.2 from scratch config");
      }
    } catch (IOException ignored) {
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
  public void setAllayArmsShiftTexels(float v) {
    allayArmsShiftTexels = Math.max(-32.0f, Math.min(32.0f, v));
    save();
  }
  public void setHomeCapacity(int v) {
    homeCapacity = Math.max(0, Math.min(15, v));
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
}
