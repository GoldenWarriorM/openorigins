package com.openorigins.client.util;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Caches dispatcher renderer lookups per entity type. The vanilla dispatcher
 * resolves every non-avatar renderer purely from {@code entity.getType()}, so
 * a type key is exact — and unlike an entity key it does not collide.
 *
 * <p>An earlier revision keyed this cache by {@code Entity}. {@code Entity}
 * overrides {@code equals}/{@code hashCode} to compare numeric entity ids, and
 * every synthetic display entity (spawner and trial spawner mobs) is created
 * with id {@code -1}. All of them therefore hash to one slot: the first spawner
 * mob cached its renderer, and the next (different) mob was handed that foreign
 * renderer, crashing in the typed cast inside extract
 * (blaze rendered by AbstractSkeletonRenderer, 2026-10-19).
 *
 * <p>Cleared on resource reload, which rebuilds the dispatcher renderer table.
 * Render-thread use.
 */
public final class OaRendererCache {
  private static final Map<EntityType<?>, EntityRenderer<?, ?>> CACHE = new HashMap<>();

  private OaRendererCache() {}

  public static synchronized EntityRenderer<?, ?> get(Entity e) {
    try {
      if (e instanceof net.minecraft.world.entity.Avatar) {
        return null;
      }
      EntityRenderer<?, ?> hit = CACHE.get(e.getType());
      if (hit != null && !sane(e, hit)) {
        CACHE.remove(e.getType());
        noteMispair(e, hit);
        return null;
      }
      return hit;
    } catch (Throwable t) {
      return null;
    }
  }

  /**
   * Family sanity for the intermittently cross-wired renderer table (crash
   * family: Interaction rendered by ItemFrameRenderer, ItemFrame by
   * Display/Living renderers, ItemDisplay by ItemFrameRenderer). A renderer
   * from a typed family may only serve its matching entity family — the
   * typed cast inside extract/culling otherwise throws CCE. Renderers
   * outside the three families (NullRenderer etc.) are safe for anything.
   * The MARKER NoopRenderer is the fallback: nothing draws, nothing casts.
   */
  public static boolean sane(Entity e, EntityRenderer<?, ?> r) {
    if (r instanceof net.minecraft.client.renderer.entity.ItemFrameRenderer) {
      return e instanceof net.minecraft.world.entity.decoration.ItemFrame;
    }
    if (r instanceof net.minecraft.client.renderer.entity.DisplayRenderer) {
      return e instanceof net.minecraft.world.entity.Display;
    }
    if (r instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer) {
      return e instanceof net.minecraft.world.entity.LivingEntity;
    }
    return true;
  }

  /** State-side family check: a typed renderer may only serve a matching
   *  state class. Catches the submit path after a heal (state extracted by
   *  the Noop fallback is a base EntityRenderState; a later DisplayRenderer
   *  submit untangling it threw CCE — crash 2026-10-19). */
  public static boolean stateMatches(
      net.minecraft.client.renderer.entity.state.EntityRenderState s,
      EntityRenderer<?, ?> r) {
    if (r instanceof net.minecraft.client.renderer.entity.ItemFrameRenderer) {
      return s instanceof net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
    }
    if (r instanceof net.minecraft.client.renderer.entity.DisplayRenderer) {
      return s instanceof net.minecraft.client.renderer.entity.state.DisplayEntityRenderState;
    }
    if (r instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer) {
      return s instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
    }
    return true;
  }

  /** Rate-limited mispair logging (one line per second max). */
  public static synchronized void noteMispair(Entity e, EntityRenderer<?, ?> r) {
    long now = System.currentTimeMillis();
    if (now - lastMispairLog < 1000L) {
      return;
    }
    lastMispairLog = now;
    com.openorigins.client.util.DebugLog.warn(
        "[oacache] mispaired renderer {} for {}#{}, replaced/dropped",
        r.getClass().getName(), e.getType(), e.getId());
  }

  private static volatile long lastMispairLog;

  /** Submit-path variant: the state stands in for the entity in logs. */
  public static synchronized void noteMispair(
      net.minecraft.client.renderer.entity.state.EntityRenderState s,
      EntityRenderer<?, ?> r) {
    long now = System.currentTimeMillis();
    if (now - lastMispairLog < 1000L) {
      return;
    }
    lastMispairLog = now;
    com.openorigins.client.util.DebugLog.warn(
        "[oacache] mispaired renderer {} for state {}/{}, replaced/dropped",
        r.getClass().getName(), s.entityType, s.getClass().getSimpleName());
  }

  /** Logs that the vanilla type table itself is mispaired (registry
   *  corruption upstream, not a cache artefact); 10s rate limit. */
  public static synchronized void noteVanillaMapMispair(Entity e, EntityRenderer<?, ?> r) {
    long now = System.currentTimeMillis();
    if (now - lastVanillaMapLog < 10_000L) {
      return;
    }
    lastVanillaMapLog = now;
    com.openorigins.client.util.DebugLog.warn(
        "[oacache] vanilla renderer table mispaired for {} -> {}",
        e.getType(), r.getClass().getName());
  }

  private static volatile long lastVanillaMapLog;

  public static synchronized void put(Entity e, EntityRenderer<?, ?> r) {
    try {
      if (e == null || r == null) {
        return;
      }
      if (e instanceof net.minecraft.world.entity.Avatar) {
        return;
      }
      CACHE.put(e.getType(), r);
    } catch (Throwable ignored) {
    }
  }

  public static synchronized void clear() {
    CACHE.clear();
  }
}
