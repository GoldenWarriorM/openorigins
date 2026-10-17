package com.openorigins.client.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * Shared predicate for the "expanded model bounding box" feature: the same
 * entity-eligibility test used to live in three copies (entity culling,
 * display culling, render distance). Skips worn/passenger stands and
 * non-block/item displays; text displays stay vanilla.
 */
public final class ExpandModelBox {
  private ExpandModelBox() {}

  public static boolean eligible(Entity e) {
    if (e instanceof ArmorStand stand) {
      return stand.getVehicle() == null;
    }
    return e instanceof net.minecraft.world.entity.Display.BlockDisplay
        || e instanceof net.minecraft.world.entity.Display.ItemDisplay;
  }

  /** The single home of the magic numbers: culling inflate and size floor. */
  public static final float INFLATE = 4.5f;
  public static final float SIZE_FLOOR = 4.0f;
}
