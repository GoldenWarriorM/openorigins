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
      return CACHE.get(e);
    } catch (Throwable t) {
      return null;
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
