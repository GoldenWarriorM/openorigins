package com.openorigins.client.util;

import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * Experimental dead reckoning for custom-mob bodies: server hitboxes run
 * ahead of what the client renders (no vanilla extrapolation), so fast mobs
 * must be hit "into the future". Tracks per-tick positions of display/stand
 * parts (and their carriers) and shifts their render state forward along
 * their velocity. Visual only: hitboxes and interaction are untouched.
 */
public final class MobExtrapolation {
  private MobExtrapolation() {}

  /** How far ahead to render, in milliseconds. Tunable live in settings. */
  public static final long EXTRA_MS = 150;

  /** Velocity smoothing: 1.0 = raw, lower = smoother but laggier. */
  private static final double SMOOTH = 0.4;

  /** Render damping time constant: applied offset glides to target this fast. */
  private static final double DAMP_TAU_MS = 90.0;
  private static final double MAX_SNAP_LEN = 3.0;

  /** Currently rendered offset per entity: ox, oy, oz, last frame ms. */
  private static final ConcurrentMap<Integer, double[]> APPLIED =
      new ConcurrentHashMap<>();

  private static final ConcurrentMap<Integer, double[]> SMOOTHED_VEL =
      new ConcurrentHashMap<>();

  private static long extraMs() {
    try {
      return com.openorigins.client.OpenOriginsConfig.getInstance().getExtrapMs();
    } catch (Throwable t) {
      return EXTRA_MS;
    }
  }
  private static final int WINDOW = 4;
  private static final double MAX_WINDOW_DIST = 4.0;
  private static final int CAP = 2048;
  private static final double TRACK_DIST_SQ = 96.0 * 96.0;

  private static final ConcurrentMap<Integer, Long> LAST_SEEN =
      new ConcurrentHashMap<>();

  private static final ConcurrentMap<Integer, ArrayDeque<double[]>> HIST =
      new ConcurrentHashMap<>();
  private static volatile long lastStatMs;
  private static volatile int appliedCount;
  /** Custom-mob parts only: item displays showing custom-model items. */
  private static boolean displayAllowed(net.minecraft.world.entity.Entity e) {
    try {
      if (e instanceof net.minecraft.world.entity.Display.ItemDisplay d) {
        net.minecraft.world.item.ItemStack s = d.getSlot(0).get();
        if (s == null || s.isEmpty()) return false;
        String id = displayKey(s);
        java.util.Set<String> only =
            com.openorigins.client.OpenOriginsConfig.getInstance().getExtrapItems();
        if (!only.isEmpty()) {
          return only.contains(id);
        }
        if (CustomItemIds.resolve(s).isPresent()) return true;
        net.minecraft.world.item.component.CustomModelData cmd =
            s.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
        return cmd != null && !cmd.floats().isEmpty();
      }
    } catch (Throwable ignored) {
      return false;
    }
    return true;
  }

  private static boolean sharesMountWithPlayer(Entity e, Entity player) {
    try {
      Entity root = e;
      java.util.Set<Integer> seen = new java.util.HashSet<>();
      while (root != null && root.getVehicle() != null && seen.add(root.getId())) {
        root = root.getVehicle();
      }
      return root != null && (root == player || carriesPlayer(root, player));
    } catch (Throwable ignored) {
      return false;
    }
  }

  private static boolean hasDisplayPassenger(Entity e) {    try {
      for (Entity p : e.getPassengers()) {
        if (p instanceof Display || p instanceof ArmorStand) {
          return true;
        }
      }
    } catch (Throwable ignored) {
    }
    return false;
  }

  /**
   * Render offset with per-frame damping: glides toward the velocity target
   * instead of stepping every tick. Returns null when there is nothing
   * sensible to show.
   */
  public static double[] renderOffset(Entity e) {
    try {
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      if (mc != null && mc.player != null && (carriesPlayer(e, mc.player)
          || sharesMountWithPlayer(e, mc.player) || !typeAllowed(e) || !displayAllowed(e))) {
        APPLIED.remove(e.getId());
        return null;
      }
      double[] t = offset(e);
      if (t == null) {
        APPLIED.remove(e.getId());
        return null;
      }
      double tLen2 = t[0] * t[0] + t[1] * t[1] + t[2] * t[2];
      long nowMs = System.currentTimeMillis();
      double[] cur = APPLIED.get(e.getId());
      if (cur == null || tLen2 > MAX_SNAP_LEN * MAX_SNAP_LEN) {
        double[] snap = {t[0], t[1], t[2], (double) nowMs};
        APPLIED.put(e.getId(), snap);
        return new double[] {t[0], t[1], t[2]};
      }
      double dt = Math.max(0.0, Math.min(500.0, nowMs - cur[3]));
      double f = 1.0 - Math.exp(-dt / DAMP_TAU_MS);
      cur[0] += (t[0] - cur[0]) * f;
      cur[1] += (t[1] - cur[1]) * f;
      cur[2] += (t[2] - cur[2]) * f;
      cur[3] = (double) nowMs;
      return new double[] {cur[0], cur[1], cur[2]};
    } catch (Throwable t) {
      return null;
    }
  }

  /** Velocity target from the smoothed per-tick history, or null. */
  private static double[] offset(Entity e) {
    try {
      double[] sm = SMOOTHED_VEL.get(e.getId());
      if (sm == null) {
        return null;
      }
      double ms = extraMs();
      double ox = sm[0] * ms;
      double oy = sm[1] * ms;
      double oz = sm[2] * ms;
      if (ox * ox + oy * oy + oz * oz < 0.0001) {
        return null;
      }
      return new double[] {ox, oy, oz};
    } catch (Throwable t) {
      return null;
    }
  }
}
