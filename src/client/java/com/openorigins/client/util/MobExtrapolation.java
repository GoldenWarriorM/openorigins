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

  /** Called from the render mixins when an offset actually shifts a state. */
  public static void noteApplied() {
    appliedCount++;
  }

  public static void register() {
    net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT
        .register((handler, client) -> {
          HIST.clear();
          SMOOTHED_VEL.clear();
          LAST_SEEN.clear();
        });
    net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK
        .register(client -> {
          if (client.level == null || client.player == null) {
            return;
          }
          try {
            for (Entity e : client.level.entitiesForRendering()) {
              if (e == null || e == client.player || e.isRemoved()) {
                continue;
              }
              if (!(e instanceof Display) && !(e instanceof ArmorStand)
                  && !hasDisplayPassenger(e)) {
                continue;
              }
              try {
                if (e.distanceToSqr(client.player) > TRACK_DIST_SQ) {
                  continue;
                }
              } catch (Throwable ignored) {
              }
              long tickNow;
              try {
                tickNow = client.level.getGameTime();
              } catch (Throwable t) {
                tickNow = System.currentTimeMillis() / 50L;
              }
              LAST_SEEN.put(e.getId(), tickNow);
              if (HIST.size() >= CAP && !HIST.containsKey(e.getId())) {
                evictOldest(tickNow);
                if (HIST.size() >= CAP && !HIST.containsKey(e.getId())) {
                  continue;
                }
              }
              ArrayDeque<double[]> q = HIST.computeIfAbsent(e.getId(),
                  id -> new ArrayDeque<>(WINDOW));
              synchronized (q) {
                q.addLast(new double[] {e.getX(), e.getY(), e.getZ()});
                while (q.size() > WINDOW) {
                  q.removeFirst();
                }
                if (q.size() >= 2) {
                  double[] first = q.peekFirst();
                  double[] last = q.peekLast();
                  double dx = last[0] - first[0];
                  double dy = last[1] - first[1];
                  double dz = last[2] - first[2];
                  double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                  if (dist > MAX_WINDOW_DIST) {
                    // Teleport, not motion: restart from the new spot.
                    q.clear();
                    q.addLast(new double[] {e.getX(), e.getY(), e.getZ()});
                    SMOOTHED_VEL.remove(e.getId());
                  } else {
                    double perMs = 1.0 / ((q.size() - 1) * 50.0);
                    double[] raw = {dx * perMs, dy * perMs, dz * perMs};
                    double[] sm = SMOOTHED_VEL.get(e.getId());
                    if (sm == null) {
                      SMOOTHED_VEL.put(e.getId(), raw);
                    } else {
                      sm[0] += (raw[0] - sm[0]) * SMOOTH;
                      sm[1] += (raw[1] - sm[1]) * SMOOTH;
                      sm[2] += (raw[2] - sm[2]) * SMOOTH;
                    }
                  }
                }
              }
            }
          } catch (Throwable ignored) {
          }
          long nowMs = System.currentTimeMillis();
          if (nowMs - lastStatMs > 5000) {
            lastStatMs = nowMs;
            try {
              com.openorigins.client.util.DebugLog.info("[extrap] tracked={} applied={}",
                  HIST.size(), appliedCount);
            } catch (Throwable ignored) {
            }
            appliedCount = 0;
          }
        });
  }

  private static void evictOldest(long tickNow) {
    try {
      int target = CAP * 3 / 4;
      if (HIST.size() < target) {
        return;
      }
      java.util.ArrayList<java.util.Map.Entry<Integer, Long>> entries =
          new java.util.ArrayList<>(LAST_SEEN.entrySet());
      entries.sort(java.util.Comparator.comparingLong(java.util.Map.Entry::getValue));
      for (java.util.Map.Entry<Integer, Long> e : entries) {
        if (HIST.size() < target) {
          break;
        }
        HIST.remove(e.getKey());
        SMOOTHED_VEL.remove(e.getKey());
        LAST_SEEN.remove(e.getKey());
      }
    } catch (Throwable ignored) {
    }
  }

  private static boolean hasDisplayPassenger(Entity e) {
    try {
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
   * Forward offset in blocks for this render frame, or null when unknown or
   * stationary. Uses the smoothed velocity, so packet quantization jitter
   * does not shake the model.
   */
  public static double[] offset(Entity e) {
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
