package com.openorigins.client.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Temporary measurement for custom-block break sync: records break start
 * (pos, state, tool), client-side completion, server air-ack, and any
 * effect/attribute correction that lands mid-break. All debug-gated.
 */
public final class BreakProbe {
  private BreakProbe() {}

  private static volatile BlockPos target;
  private static volatile long startMs;
  private static volatile long clientDoneMs;
  private static volatile String toolKey = "?";
  private static volatile String blockKey = "?";

  public static void onStart(BlockPos pos, BlockState state, String tool) {
    target = pos.immutable();
    startMs = System.currentTimeMillis();
    clientDoneMs = -1;
    toolKey = tool;
    blockKey = state.toString();
    DebugLog.info("[breakprobe] start {} tool={}", blockKey, toolKey);
  }

  public static void onClientDone() {
    if (target == null || clientDoneMs >= 0) {
      return;
    }
    clientDoneMs = System.currentTimeMillis();
    DebugLog.info("[breakprobe] client-done after {}ms", clientDoneMs - startMs);
  }

  public static void onAbort() {
    if (target == null) {
      return;
    }
    if (clientDoneMs < 0) {
      DebugLog.info("[breakprobe] abort after {}ms", System.currentTimeMillis() - startMs);
    }
    target = null;
  }

  public static void onBlockUpdate(BlockPos pos, BlockState state) {
    BlockPos t = target;
    if (t == null || !t.equals(pos)) {
      return;
    }
    if (state.isAir()) {
      long now = System.currentTimeMillis();
      DebugLog.info("[breakprobe] server-air after {}ms (client-done {}ms)",
          now - startMs, clientDoneMs < 0 ? "never" : ("+" + (now - clientDoneMs)));
      target = null;
    }
  }

  public static void onEffect(boolean local, String desc) {
    if (target == null || !local) {
      return;
    }
    DebugLog.info("[breakprobe] effect {} at +{}ms", desc, System.currentTimeMillis() - startMs);
  }

  public static void onAttributes(boolean local, String desc) {
    if (target == null || !local) {
      return;
    }
    DebugLog.info("[breakprobe] attrs [{}] at +{}ms", desc, System.currentTimeMillis() - startMs);
  }
}
