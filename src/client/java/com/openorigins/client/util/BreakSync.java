package com.openorigins.client.util;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Client-side break-sync prediction for custom blocks.
 *
 * The server freezes vanilla client progress mid-break by setting the
 * BLOCK_BREAK_SPEED attribute to a huge negative value and breaks the block
 * on its own timer. Without prediction the crack jumps early then freezes
 * until the server pops the block. BreakSync learns server-acknowledged
 * durations per (blockstate, tool) and animates destroyProgress toward 1.0
 * so the crack lands exactly on the server pop. Visual only: progress is
 * capped below 1.0, the client never completes early, so no rejects.
 */
public final class BreakSync {
  private BreakSync() {}

  private static final float PROGRESS_CAP = 0.99f;
  private static final long MIN_SAMPLE_MS = 200L;
  private static final long MAX_SAMPLE_MS = 300000L;
  private static final long SAVE_EVERY_MS = 15000L;

  private static final Map<String, Double> learned = new ConcurrentHashMap<>();
  private static volatile long lastSaveMs;

  private static volatile BlockPos target;
  private static volatile long t0;
  private static volatile String key = "";
  private static volatile boolean driven;

  static {
    load();
  }

  private static java.nio.file.Path file() {
    try {
      return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
          .resolve("openorigins_break.json");
    } catch (Throwable t) {
      return null;
    }
  }

  private static void load() {
    try {
      java.nio.file.Path f = file();
      if (f == null || !java.nio.file.Files.isRegularFile(f)) {
        return;
      }
      com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(
          java.nio.file.Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
      for (Map.Entry<String, com.google.gson.JsonElement> e : root.entrySet()) {
        try {
          double ms = e.getValue().getAsDouble();
          if (ms >= MIN_SAMPLE_MS && ms <= MAX_SAMPLE_MS) {
            learned.put(e.getKey(), ms);
          }
        } catch (Throwable ignored) {
        }
      }
    } catch (Throwable ignored) {
    }
  }

  private static void save() {
    try {
      long now = System.currentTimeMillis();
      if (now - lastSaveMs < SAVE_EVERY_MS) {
        return;
      }
      lastSaveMs = now;
      java.nio.file.Path f = file();
      if (f == null) {
        return;
      }
      com.google.gson.JsonObject root = new com.google.gson.JsonObject();
      for (Map.Entry<String, Double> e : learned.entrySet()) {
        root.addProperty(e.getKey(), e.getValue());
      }
      java.nio.file.Files.writeString(f, root.toString(), StandardCharsets.UTF_8);
    } catch (Throwable ignored) {
    }
  }

  private static String stateKey(BlockState state) {
    try {
      return state.toString();
    } catch (Throwable t) {
      return "?";
    }
  }

  public static String toolKey(net.minecraft.world.entity.player.Player player) {
    try {
      return net.minecraft.core.registries.BuiltInRegistries.ITEM
          .getKey(player.getMainHandItem().getItem()).toString();
    } catch (Throwable t) {
      return "?";
    }
  }

  public static void onStart(BlockPos pos, BlockState state,
      net.minecraft.world.entity.player.Player player) {
    try {
      if (pos.equals(target)) {
        return;
      }
      target = pos.immutable();
      t0 = System.currentTimeMillis();
      key = stateKey(state) + "|" + toolKey(player);
      driven = false;
    } catch (Throwable ignored) {
    }
  }

  public static void onAbort() {
    target = null;
    key = "";
    driven = false;
  }

  public static void onServerAir(BlockPos pos, BlockState state) {
    try {
      BlockPos t = target;
      if (t == null || !t.equals(pos) || !state.isAir()) {
        return;
      }
      long dur = System.currentTimeMillis() - t0;
      String k = key;
      boolean wasDriven = driven;
      target = null;
      key = "";
      driven = false;
      if (!wasDriven || k.isEmpty() || dur < MIN_SAMPLE_MS || dur > MAX_SAMPLE_MS) {
        return;
      }
      Double old = learned.get(k);
      learned.put(k, old == null ? (double) dur : old * 0.6 + dur * 0.4);
      save();
    } catch (Throwable ignored) {
    }
  }

  /**
   * Replacement for the vanilla per-tick progress increment. Returns the
   * increment vanilla should add this tick.
   */
  public static float increment(net.minecraft.client.multiplayer.MultiPlayerGameMode mode,
      BlockState state, net.minecraft.world.entity.player.Player player,
      net.minecraft.world.level.BlockGetter level, BlockPos pos, float vanilla) {
    try {
      if (!com.openorigins.client.OpenOriginsConfig.getInstance()
          .isEnabled(com.openorigins.client.OriginFeature.BREAK_SYNC)) {
        return vanilla;
      }
      if (player.getAbilities().instabuild) {
        return vanilla;
      }
      double attr;
      try {
        attr = player.getAttributeValue(
            net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_BREAK_SPEED);
      } catch (Throwable t) {
        return vanilla;
      }
      boolean serverDriven = Math.abs(attr - 1.0) > 0.001;
      if (!serverDriven) {
        return vanilla;
      }
      if (target == null || !pos.equals(target)) {
        onStart(pos, state, player);
      }
      driven = true;
      Double total = learned.get(key);
      if (total == null) {
        return 0.0f;
      }
      float current;
      try {
        current = ((com.openorigins.client.mixin.BreakSyncAccessor) mode)
            .openorigins$getDestroyProgress();
      } catch (Throwable t) {
        return 0.0f;
      }
      if (current >= PROGRESS_CAP) {
        return 0.0f;
      }
      long remainMs = (long) (total - (System.currentTimeMillis() - t0));
      float inc;
      if (remainMs <= 0) {
        inc = PROGRESS_CAP - current;
      } else {
        inc = (1.0f - current) / Math.max(1.0f, remainMs / 50.0f);
      }
      if (inc < 0.0f) {
        return 0.0f;
      }
      float room = PROGRESS_CAP - current;
      return Math.min(inc, room);
    } catch (Throwable t) {
      return vanilla;
    }
  }
}
