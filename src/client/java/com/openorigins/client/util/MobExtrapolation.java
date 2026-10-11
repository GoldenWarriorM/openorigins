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

  private static long extraMs() {
    try {
      return com.openorigins.client.OpenOriginsConfig.getInstance().getExtrapMs();
    } catch (Throwable t) {
      return EXTRA_MS;
    }
  }
  private static final int WINDOW = 4;
  private static final double MAX_WINDOW_DIST = 4.0;
  private static final int CAP = 256;

  private static final ConcurrentMap<Integer, ArrayDeque<double[]>> HIST =
      new ConcurrentHashMap<>();

  public static void register() {
    net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT
        .register((handler, client) -> HIST.clear());
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
              if (HIST.size() >= CAP && !HIST.containsKey(e.getId())) {
                continue;
              }
              ArrayDeque<double[]> q = HIST.computeIfAbsent(e.getId(),
                  id -> new ArrayDeque<>(WINDOW));
              synchronized (q) {
                q.addLast(new double[] {e.getX(), e.getY(), e.getZ()});
                while (q.size() > WINDOW) {
                  q.removeFirst();
                }
              }
            }
          } catch (Throwable ignored) {
          }
        });
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
   * Forward offset in blocks for this render frame, or null when unknown,
   * stationary or teleported.
   */
  public static double[] offset(Entity e) {
    try {
      ArrayDeque<double[]> q = HIST.get(e.getId());
      if (q == null || q.size() < 2) {
        return null;
      }
      double[] first;
      double[] last;
      int n;
      synchronized (q) {
        n = q.size();
        first = q.peekFirst();
        last = q.peekLast();
      }
      double dx = last[0] - first[0];
      double dy = last[1] - first[1];
      double dz = last[2] - first[2];
      double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
      if (dist * dist < 0.0001) {
        return null;
      }
      if (dist > MAX_WINDOW_DIST) {
        HIST.remove(e.getId());
        return null;
      }
      double perMs = 1.0 / ((n - 1) * 50.0);
      double k = extraMs() * perMs;
      return new double[] {dx * k, dy * k, dz * k};
    } catch (Throwable t) {
      return null;
    }
  }
}
