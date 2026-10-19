package com.openorigins.client.util;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;

/**
 * Caches dispatcher renderer lookups per entity. The renderer for an entity
 * never changes (except avatar skins, which are excluded), so the per-frame
 * map lookups in EntityRenderDispatcher.getRenderer are skipped on hits.
 * Render-thread use; entries vanish with their entities.
 */
public final class OaRendererCache {
  private static final Map<Entity, EntityRenderer<?, ?>> CACHE = new WeakHashMap<>();

  private OaRendererCache() {}

  public static synchronized EntityRenderer<?, ?> get(Entity e) {
    try {
      if (e instanceof net.minecraft.world.entity.Avatar) {
        return null;
      }
      EntityRenderer<?, ?> hit = CACHE.get(e);
      if (hit != null && !sane(e, hit)) {
        CACHE.remove(e);
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
      if (CACHE.size() > 4096) {
        CACHE.clear();
      }
      CACHE.put(e, r);
    } catch (Throwable ignored) {
    }
  }

  public static synchronized void clear() {
    CACHE.clear();
  }
}
