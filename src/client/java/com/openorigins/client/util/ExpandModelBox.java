package com.openorigins.client.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * Shared predicate for the "expanded model bounding box" feature: the same
 * entity-eligibility test used to live in three copies (entity culling,
 * display culling, render distance). Covers armor stands, block/item
 * displays and item frames (custom decorations are often item models shown
 * in frames); skips worn/passenger stands; text displays stay vanilla.
 */
public final class ExpandModelBox {
  private ExpandModelBox() {}

  public static boolean eligible(Entity e) {
    if (e instanceof ArmorStand stand) {
      return stand.getVehicle() == null;
    }
    return e instanceof net.minecraft.world.entity.Display.BlockDisplay
        || e instanceof net.minecraft.world.entity.Display.ItemDisplay
        || e instanceof net.minecraft.world.entity.decoration.ItemFrame;
  }

  /** The single home of the magic numbers: culling inflate and size floor. */
  public static final float INFLATE = 6.0f;
  public static final float SIZE_FLOOR = 4.0f;
  /** Models this close are never frustum-culled: the inflated box always
   *  reaches past the camera, so huge builds stop vanishing at screen edges. */
  public static final double NEAR_RADIUS = 24.0;
  public static final float NEAR_INFLATE = 24.0f;

  /** Culling inflation for one entity: much larger near the camera. */
  public static float inflate(Entity e) {
    try {
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      if (mc != null && mc.gameRenderer != null) {
        net.minecraft.client.Camera cam = mc.gameRenderer.mainCamera();
        if (cam != null && cam.position() != null
            && e.position().distanceToSqr(cam.position()) < NEAR_RADIUS * NEAR_RADIUS) {
          return NEAR_INFLATE;
        }
      }
    } catch (Throwable ignored) {
    }
    return INFLATE;
  }
}
