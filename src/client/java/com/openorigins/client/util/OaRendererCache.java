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
   * Family sanity for the intermittent cross-wired renderer table (crash
   * family: Interaction rendered by ItemFrameRenderer, ItemFrame by Display/
   * LivingEntity renderers). A renderer must serve its own entity family —
   * otherwise the typed cast inside extract/culling throws CCE. The MARKER
   * NoopRenderer is the safe fallback: nothing draws, nothing casts.
   */
  public static boolean sane(Entity e, EntityRenderer<?, ?> r) {
    if (e instanceof net.minecraft.world.entity.decoration.ItemFrame) {
      return r instanceof net.minecraft.client.renderer.entity.ItemFrameRenderer;
    }
    boolean display = e instanceof net.minecraft.world.entity.Display;
    boolean living = e instanceof net.minecraft.world.entity.LivingEntity;
    if (r instanceof net.minecraft.client.renderer.entity.DisplayRenderer) {
      return display;
    }
    if (r instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer) {
      return living;
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
