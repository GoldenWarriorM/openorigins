package com.openorigins.client.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Learns real break durations per custom block state + tool.
 * Server is authoritative; lag only inflates samples, so the minimum wins.
 */
public final class BlockHardnessCache {
  private BlockHardnessCache() {}

  private static final Logger LOG = LoggerFactory.getLogger("openorigins");
  private static final int MAX_SAMPLES = 8;

  private static final Map<String, List<Long>> SAMPLES = new ConcurrentHashMap<>();
  private static final Map<String, Long> ALL_MIN = new ConcurrentHashMap<>();

  private static volatile BlockPos diggingPos;
  private static volatile long diggingStart;
  private static volatile String diggingState;
  private static volatile String diggingTool;

  private record Pending(String key, String state, String tool, long breakMs, BlockPos pos, long observedAt) {}
  private static final List<Pending> PENDING =
      java.util.Collections.synchronizedList(new ArrayList<>());
  private static final long CONFIRM_DELAY_MS = 5000;

  public static String stateKey(BlockState state) {
    StringBuilder sb = new StringBuilder(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
    List<Property<?>> props = new ArrayList<>(state.getProperties());
    props.sort(Comparator.comparing(Property::getName));
    for (Property<?> p : props) {
      sb.append(',').append(p.getName()).append('=').append(state.getValue(p));
    }
    return sb.toString();
  }

  public static String mapKey(String stateKey, String toolId) {
    return stateKey + " | " + toolId;
  }

  private static String currentToolId() {
    Minecraft mc = Minecraft.getInstance();
    if (mc == null || mc.player == null) {
      return "hand";
    }
    return BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem()).toString();
  }

  /** Predicted millis (all-time min), or -1 if unknown. */
  public static long predictMs(String stateKey, String toolId) {
    Long min = ALL_MIN.get(mapKey(stateKey, toolId));
    return min == null ? -1 : min;
  }

  public static void onDigStart(BlockPos pos) {
    Minecraft mc = Minecraft.getInstance();
    if (mc == null || mc.level == null || mc.player == null) {
      return;
    }
    if (mc.player.isCreative()) {
      return;
    }
    BlockState state = mc.level.getBlockState(pos);
    if (state.isAir()) {
      return;
    }
    String tool = currentToolId();
    long now = System.currentTimeMillis();
    if (diggingPos != null && diggingPos.equals(pos) && tool.equals(diggingTool)
        && now - diggingStart < 120000) {
      return;
    }
    diggingPos = pos.immutable();
    diggingStart = now;
    diggingState = stateKey(state);
    diggingTool = tool;
    long pred = predictMs(diggingState, diggingTool);
    LOG.info("[hardness] dig start {} tool={} state={} predicted={}ms",
        pos.toShortString(), diggingTool, diggingState,
        pred < 0 ? "unknown" : Long.toString(pred));
  }

  public static void onDigAbort() {
    if (diggingPos != null) {
      LOG.info("[hardness] dig abort {}", diggingPos.toShortString());
    }
    diggingPos = null;
  }

  public static void onBlockChange(BlockPos pos, BlockState newState) {
    synchronized (PENDING) {
      PENDING.removeIf(p -> {
        if (p.pos().equals(pos) && !newState.isAir()) {
          LOG.info("[hardness] rollback, discarded {}ms state={}", p.breakMs(), p.state());
          return true;
        }
        return false;
      });
    }
    BlockPos target = diggingPos;
    if (target == null || !target.equals(pos)) {
      return;
    }
    if (!newState.isAir()) {
      return;
    }
    diggingPos = null;
    long dur = System.currentTimeMillis() - diggingStart;
    String toolNow = currentToolId();
    if (!toolNow.equals(diggingTool)) {
      LOG.info("[hardness] discarded (tool changed {} -> {}) {}ms", diggingTool, toolNow, dur);
      return;
    }
    if (dur < 100 || dur > 120000) {
      LOG.info("[hardness] discarded (out of range) {}ms state={}", dur, diggingState);
      return;
    }
    synchronized (PENDING) {
      if (PENDING.size() > 16) {
        PENDING.remove(0);
      }
      PENDING.add(new Pending(mapKey(diggingState, diggingTool), diggingState, diggingTool,
          dur, pos.immutable(), System.currentTimeMillis()));
    }
    LOG.info("[hardness] break observed {}ms tool={} state={} (confirming in {}s)",
        dur, diggingTool, diggingState, CONFIRM_DELAY_MS / 1000);
  }

  /** Called every client tick; confirms breaks that stuck. */
  public static void tick() {
    Minecraft mc = Minecraft.getInstance();
    if (mc == null || mc.level == null) {
      return;
    }
    long now = System.currentTimeMillis();
    synchronized (PENDING) {
      PENDING.removeIf(p -> {
        if (now - p.observedAt() < CONFIRM_DELAY_MS) {
          return false;
        }
        BlockState current = mc.level.getBlockState(p.pos());
        if (!current.isAir()) {
          LOG.info("[hardness] rollback on confirm, discarded {}ms state={}", p.breakMs(), p.state());
          return true;
        }
        String key = p.key();
        List<Long> s = SAMPLES.computeIfAbsent(key, k -> new ArrayList<>());
        synchronized (s) {
          s.add(p.breakMs());
          while (s.size() > MAX_SAMPLES) {
            s.remove(0);
          }
        }
        ALL_MIN.merge(key, p.breakMs(), Math::min);
        save();
        LOG.info("[hardness] confirmed {}ms tool={} state={} samples={} min={}ms",
            p.breakMs(), p.tool(), p.state(), s, predictMs(p.state(), p.tool()));
        return true;
      });
    }
  }

  private static Path file() {
    return FabricLoader.getInstance().getConfigDir().resolve("openorigins_hardness.json");
  }

  public static synchronized void save() {
    try {
      JsonObject root = new JsonObject();
      JsonObject samples = new JsonObject();
      for (Map.Entry<String, List<Long>> e : SAMPLES.entrySet()) {
        JsonArray arr = new JsonArray();
        synchronized (e.getValue()) {
          for (long v : e.getValue()) {
            arr.add(v);
          }
        }
        samples.add(e.getKey(), arr);
      }
      root.add("samples", samples);
      JsonObject mins = new JsonObject();
      for (Map.Entry<String, Long> e : ALL_MIN.entrySet()) {
        mins.addProperty(e.getKey(), e.getValue());
      }
      root.add("min", mins);
      Files.writeString(file(), root.toString(), StandardCharsets.UTF_8);
    } catch (Exception ex) {
      LOG.warn("[hardness] save failed: {}", ex.toString());
    }
  }

  public static synchronized void load() {
    try {
      Path f = file();
      if (!Files.isRegularFile(f)) {
        return;
      }
      JsonObject root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
      int n = 0;
      if (root.has("min")) {
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("min").entrySet()) {
          ALL_MIN.put(e.getKey(), e.getValue().getAsLong());
          n++;
        }
      }
      if (root.has("samples")) {
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("samples").entrySet()) {
          List<Long> s = new ArrayList<>();
          for (JsonElement v : e.getValue().getAsJsonArray()) {
            s.add(v.getAsLong());
          }
          if (!s.isEmpty()) {
            SAMPLES.put(e.getKey(), s);
          }
        }
      } else {
        for (Map.Entry<String, JsonElement> e : root.entrySet()) {
          List<Long> s = new ArrayList<>();
          for (JsonElement v : e.getValue().getAsJsonArray()) {
            s.add(v.getAsLong());
          }
          if (!s.isEmpty()) {
            SAMPLES.put(e.getKey(), s);
            long min = Long.MAX_VALUE;
            for (long v : s) {
              min = Math.min(min, v);
            }
            ALL_MIN.put(e.getKey(), min);
          }
        }
      }
      LOG.info("[hardness] loaded {} entries from disk", n > 0 ? n : SAMPLES.size());
    } catch (Exception ex) {
      LOG.warn("[hardness] load failed: {}", ex.toString());
    }
  }
}
