package com.openorigins.client.util;

import net.minecraft.core.Rotations;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Spherical interpolation of armor-stand Euler poses. */
public final class PoseLerp {
  private PoseLerp() {}

  public static Rotations lerp(Rotations from, Rotations to, float partialTick) {
    if (from.equals(to) || partialTick <= 0.0f) return from;
    if (partialTick >= 1.0f) return to;
    float rad = (float) (Math.PI / 180.0);
    Quaternionf qFrom = new Quaternionf().rotateXYZ(from.x() * rad, from.y() * rad, from.z() * rad);
    Quaternionf qTo = new Quaternionf().rotateXYZ(to.x() * rad, to.y() * rad, to.z() * rad);
    Vector3f euler = qFrom.slerp(qTo, partialTick, new Quaternionf()).getEulerAnglesXYZ(new Vector3f());
    float deg = (float) (180.0 / Math.PI);
    return new Rotations(euler.x() * deg, euler.y() * deg, euler.z() * deg);
  }
}
