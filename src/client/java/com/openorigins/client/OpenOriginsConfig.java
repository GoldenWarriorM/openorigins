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
  private static final OpenOriginsConfig INSTANCE = new OpenOriginsConfig();
  private final Map<OriginFeature, Boolean> enabled = new EnumMap<>(OriginFeature.class);

  private OpenOriginsConfig() {
    for (OriginFeature f : OriginFeature.values()) enabled.put(f, true);
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
    } catch (IOException ignored) {
    }
  }

  public synchronized void save() {
    Properties props = new Properties();
    for (Map.Entry<OriginFeature, Boolean> e : enabled.entrySet()) {
      props.setProperty("feature." + e.getKey().name().toLowerCase(), Boolean.toString(e.getValue()));
    }
    try {
      Files.createDirectories(configPath().getParent());
      try (OutputStream out = Files.newOutputStream(configPath())) {
        props.store(out, "OpenOrigins 26.2 from scratch config");
      }
    } catch (IOException ignored) {
    }
  }

  public boolean isEnabled(OriginFeature f) { return enabled.getOrDefault(f, true); }
  public void setEnabled(OriginFeature f, boolean v) {
    enabled.put(f, v);
    save();
  }
}
