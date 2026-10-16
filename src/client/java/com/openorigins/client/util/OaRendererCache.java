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
      if (hit != null && isCrossWired(e, hit)) {
        CACHE.remove(e);
        com.openorigins.client.util.DebugLog.info(
            "[oacache] dropped cross-wired renderer {} for {}#{}",
            hit.getClass().getName(), e.getType(), e.getId());
        return null;
      }
      return hit;
    } catch (Throwable t) {
      return null;
    }
  }

  /**
   * Tripwire for the ItemFrame/Display crash: a display renderer must never
   * serve a non-display entity and vice versa. On mismatch the entry is
   * dropped so the dispatcher resolves fresh.
   */
  private static boolean isCrossWired(Entity e, EntityRenderer<?, ?> r) {
    try {
      boolean entityIsDisplay = e instanceof net.minecraft.world.entity.Display;
      boolean rendererIsDisplay =
          r.getClass().getName().contains("DisplayRenderer");
      return entityIsDisplay != rendererIsDisplay;
    } catch (Throwable t) {
      return false;
    }
  }

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
