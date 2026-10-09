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
  private static final java.util.Set<String> loggedAgg =
      java.util.Collections.synchronizedSet(new java.util.HashSet<>());

  private static volatile BlockPos target;
  private static volatile long t0;
  private static volatile String key = "";
  private static volatile String blockId = "";
  private static volatile boolean driven;
  private static volatile long clientDoneMs = -1;

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
          // Drop poisoned air-block entries from the buggy aggregate era.
          if (e.getKey().startsWith("minecraft:air|")) {
            continue;
          }
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
      if (state.isAir()) {
        return;
      }
      target = pos.immutable();
      t0 = System.currentTimeMillis();
      key = stateKey(state) + "|" + toolKey(player);
      try {
        blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK
            .getKey(state.getBlock()).toString();
      } catch (Throwable ignored) {
        blockId = "";
      }
      driven = false;
      clientDoneMs = -1;
    } catch (Throwable ignored) {
    }
  }

  public static void onAbort() {
    // After client-done the target must survive until the server air-ack
    // (the sample!); stop fires on success too. Staleness is swept in increment().
    if (clientDoneMs >= 0) {
      return;
    }
    target = null;
    key = "";
    blockId = "";
    driven = false;
  }

  public static void onClientDone() {
    try {
      if (target != null && clientDoneMs < 0) {
        clientDoneMs = System.currentTimeMillis();
      }
    } catch (Throwable ignored) {
    }
  }

  public static void onServerAir(BlockPos pos, BlockState state) {
    try {
      BlockPos t = target;
      if (t == null || !t.equals(pos) || !state.isAir()) {
        return;
      }
      long now = System.currentTimeMillis();
      long dur = now - t0;
      String k = key;
      boolean wasDriven = driven;
      long doneAt = clientDoneMs;
      String bid = blockId;
      target = null;
      key = "";
      blockId = "";
      driven = false;
      clientDoneMs = -1;
      if (k.isEmpty() || dur < MIN_SAMPLE_MS || dur > MAX_SAMPLE_MS) {
        return;
      }
      // Learn server-driven customs always; otherwise only on real mismatch
      // (client finished far earlier than the server ack: fast-tool rejects).
      // Vanilla-exact breaks (diff ~0) stay out of the map.
      if (!wasDriven && (doneAt < 0 || now - doneAt < 150)) {
        return;
      }
      learn(k, dur);
      // Two-tier keys: exact state first, block+tool aggregate as fallback
      // (noteblock customs cycle states; the aggregate paces unseen states).
      // NOTE: aggregate uses the block captured at break start, NOT the air
      // state seen at ack time.
      if (!bid.isEmpty()) {
        int sep = k.lastIndexOf('|');
        String agg = bid + "|" + (sep < 0 ? "?" : k.substring(sep + 1));
        if (!agg.equals(k)) {
          learn(agg, dur);
        }
      }
      if (learned.size() > 2048) {
        learned.clear();
      }
      save();
    } catch (Throwable ignored) {
    }
  }

  private static String aggKey(BlockState state, String exactKey) {
    try {
      int sep = exactKey.lastIndexOf('|');
      String tool = sep < 0 ? "?" : exactKey.substring(sep + 1);
      String block = net.minecraft.core.registries.BuiltInRegistries.BLOCK
          .getKey(state.getBlock()).toString();
      return block + "|" + tool;
    } catch (Throwable t) {
      return null;
    }
  }

  private static void learn(String k, long dur) {
    try {
      Double old = learned.get(k);
      double w = (old != null && dur < old) ? 0.5 : 0.3;
      if (old == null) {
        learned.put(k, (double) dur);
      } else {
        learned.put(k, old * (1.0 - w) + dur * w);
      }
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
      if (state.isAir()) {
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
      if (target == null || !pos.equals(target)) {
        onStart(pos, state, player);
      } else if (System.currentTimeMillis() - t0 > 15000) {
        // Stale target (air-ack never came): reset instead of pacing forever.
        t0 = System.currentTimeMillis();
        key = stateKey(state) + "|" + toolKey(player);
        driven = serverDriven;
        clientDoneMs = -1;
      }
      if (serverDriven) {
        driven = true;
      }
      Double total = learned.get(key);
      boolean fromAgg = false;
      if (total == null) {
        // Aggregate fallback: unseen noteblock states share block+tool timing.
        // Consulted regardless of the live correction signal, so fast tools
        // pace from tick 0 even before any attribute packet can land.
        String agg = aggKey(state, key);
        total = agg == null ? null : learned.get(agg);
        fromAgg = total != null;
        if (fromAgg && loggedAgg.add(agg)) {
          com.openorigins.client.util.DebugLog.info("[breaksync] pacing {} from aggregate {}ms",
              key, Math.round(total));
        }
      }
      if (total == null) {
        // Unknown combo: freeze only while the server drives, vanilla otherwise.
        // (Fast tools finish before any correction can land; the mismatch
        // sample on air-ack teaches the combo for next time.)
        return serverDriven ? 0.0f : vanilla;
      }
      // Known combo: pace toward the learned server duration from tick 0,
      // with or without a live correction signal.
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
