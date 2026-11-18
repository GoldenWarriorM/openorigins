package com.openorigins.client.util;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

/** The back item rotation must actually reach the pose stack on both versions. */
public class VersionCompatTest {
  @Test
  void rotateTouchesThePose() {
    PoseStack poseStack = new PoseStack();
    Matrix4f before = new Matrix4f(poseStack.last().pose());
    VersionCompat.rotate(poseStack, Axis.YP.rotationDegrees(180.0F));
    assertNotEquals(before, new Matrix4f(poseStack.last().pose()));
  }

}
