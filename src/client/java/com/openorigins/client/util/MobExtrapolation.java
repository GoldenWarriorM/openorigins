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

  /** Called from the render mixins when an offset actually shifts a state. */
  public static void noteApplied() {
    appliedCount++;
  }

  /** Reused scan buffers: no allocation while the feature idles. */
  private static final java.util.List<double[]> hooks = new java.util.ArrayList<>(4);
  private static final java.util.List<Entity> candidates = new java.util.ArrayList<>(256);

  public static void register() {
    net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT
        .register((handler, client) -> {
          HIST.clear();
          SMOOTHED_VEL.clear();
          LAST_SEEN.clear();
          APPLIED.clear();
        });
    net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK
        .register(client -> {
          if (client.level == null || client.player == null) {
            return;
          }

          try {
            if (!com.openorigins.client.OpenOriginsConfig.getInstance()
                .isEnabled(com.openorigins.client.OriginFeature.ENTITY_EXTRAPOLATION)) {
              return;
            }
          } catch (Throwable ignored) {
          }
          try {
            // Single pass: collect fishing-rig hooks and candidates, then
            // process candidates once the hook set is complete (a display
            // must not be excluded by a bobber seen later in the iteration).
            candidates.clear();
            hooks.clear();
            for (Entity h : client.level.entitiesForRendering()) {
              if (h instanceof net.minecraft.world.entity.projectile.FishingHook) {
                hooks.add(new double[]{h.getX(), h.getY(), h.getZ()});
                continue;
              }
              if (h == null || h == client.player || h.isRemoved()) {
                continue;
              }
              candidates.add(h);
            }
            for (Entity e : candidates) {
              if (carriesPlayer(e, client.player) || sharesMountWithPlayer(e, client.player)
                  || nearHook(e, hooks)
                  || !typeAllowed(e) || !displayAllowed(e)) {
                HIST.remove(e.getId());
                SMOOTHED_VEL.remove(e.getId());
                APPLIED.remove(e.getId());
                LAST_SEEN.remove(e.getId());
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
        APPLIED.remove(e.getKey());
      }
    } catch (Throwable ignored) {
    }
  }

  /** Never touch anything carrying the local player (mounts break otherwise). */
  private static boolean carriesPlayer(Entity e, Entity player) {
    try {
      for (Entity p : e.getPassengers()) {
        if (p == player || carriesPlayer(p, player)) {
          return true;
        }
      }
    } catch (Throwable ignored) {
    }
    return false;
  }

  /** True if the entity rides (transitively) something carrying the player. */
  private static volatile String cachedTypesRaw;
  private static volatile java.util.Set<String> cachedTypes = java.util.Set.of();

  private static boolean typeAllowed(net.minecraft.world.entity.Entity e) {
    try {
      String raw =
          com.openorigins.client.OpenOriginsConfig.getInstance().getExtrapTypesRaw();
      java.util.Set<String> only = cachedTypes;
      if (raw == null) raw = "";
      if (!raw.equals(cachedTypesRaw)) {
        only = com.openorigins.client.OpenOriginsConfig.parseExtrapTypes(raw);
        cachedTypes = only;
        cachedTypesRaw = raw;
      }
      if (only.isEmpty()) return true;
      String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(
          e.getType()).toString().toLowerCase(java.util.Locale.ROOT);
      return only.contains(id);
    } catch (Throwable t) {
      return true;
    }
  }

  /** Composite display key: resolved/vanilla id plus custom-model floats. */
  public static String displayKey(net.minecraft.world.item.ItemStack s) {
    java.util.Optional<String> resolved = CustomItemIds.resolve(s);
    String id = resolved.isPresent() ? resolved.get()
        : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
    try {
      net.minecraft.world.item.component.CustomModelData cmd =
          s.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
      if (cmd != null && !cmd.floats().isEmpty()) {
        id = id + "|cmd" + cmd.floats();
      }
    } catch (Throwable ignored) {
    }
    return id.toLowerCase(java.util.Locale.ROOT);
  }

  /** Custom-mob parts only: item displays showing custom-model items. */
  private static boolean displayAllowed(net.minecraft.world.entity.Entity e) {
    try {
      if (e instanceof net.minecraft.world.entity.Display.ItemDisplay d) {
        net.minecraft.world.item.ItemStack s = d.getSlot(0).get();
        if (s == null || s.isEmpty()) return false;
        String id = displayKey(s);
        java.util.Set<String> only = extrapItemsCached();
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

  /** extrap.items parsed once per config change, not once per entity. */
  private static java.util.Set<String> extrapItemsCached() {
    String raw = com.openorigins.client.OpenOriginsConfig.getInstance().getExtrapItemsRaw();
    if (!raw.equals(cachedItemsRaw)) {
      cachedItems = com.openorigins.client.OpenOriginsConfig.parseExtrapItems(raw);
      cachedItemsRaw = raw;
    }
    return cachedItems;
  }

  private static volatile String cachedItemsRaw;
  private static volatile java.util.Set<String> cachedItems = java.util.Set.of();

  /** Fishing rigs (bobber splash displays/stands) must never be smoothed. */
  private static boolean nearHook(Entity e, java.util.List<double[]> hooks) {
    if (hooks.isEmpty()) {
      return false;
    }
    try {
      double x = e.getX();
      double y = e.getY();
      double z = e.getZ();
      for (double[] h : hooks) {
        double dx = x - h[0];
        double dy = y - h[1];
        double dz = z - h[2];
        if (dx * dx + dy * dy + dz * dz < 16.0) {
          return true;
        }
      }
    } catch (Throwable ignored) {
    }
    return false;
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
        cur = new double[] {t[0], t[1], t[2], (double) nowMs};
        APPLIED.put(e.getId(), cur);
        return SCRATCH;
      }
      double dt = Math.max(0.0, Math.min(500.0, nowMs - cur[3]));
      double f = 1.0 - Math.exp(-dt / DAMP_TAU_MS);
      cur[0] += (t[0] - cur[0]) * f;
      cur[1] += (t[1] - cur[1]) * f;
      cur[2] += (t[2] - cur[2]) * f;
      cur[3] = (double) nowMs;
      return cur;
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
      SCRATCH[0] = sm[0] * ms;
      SCRATCH[1] = sm[1] * ms;
      SCRATCH[2] = sm[2] * ms;
      if (SCRATCH[0] * SCRATCH[0] + SCRATCH[1] * SCRATCH[1] + SCRATCH[2] * SCRATCH[2] < 0.0001) {
        return null;
      }
      return SCRATCH;
    } catch (Throwable t) {
      return null;
    }
  }

  /** Reused render-path target; callers apply it synchronously and drop it. */
  private static final double[] SCRATCH = new double[3];

  /** Shared apply path for both render-state mixins (Display and ArmorStand
   *  hierarchies share x/y/z and nameTagAttachment on EntityRenderState). */
  public static void apply(net.minecraft.world.entity.Entity e,
      net.minecraft.client.renderer.entity.state.EntityRenderState state) {
    if (!com.openorigins.client.OpenOriginsConfig.getInstance()
        .isEnabled(com.openorigins.client.OriginFeature.ENTITY_EXTRAPOLATION)) {
      return;
    }
    if (!com.openorigins.client.ConnectionUtil.isConnected()) {
      return;
    }
    double[] o;
    try {
      o = renderOffset(e);
    } catch (Throwable t) {
      return;
    }
    if (o == null) {
      return;
    }
    noteApplied();
    state.x += o[0];
    state.y += o[1];
    state.z += o[2];
    if (state.nameTagAttachment != null) {
      state.nameTagAttachment = state.nameTagAttachment.add(o[0], o[1], o[2]);
    }
  }
}
