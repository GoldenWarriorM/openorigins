package com.openorigins.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Temporary measurement of client break start/done/abort for break-sync lab. */
@Mixin(MultiPlayerGameMode.class)
public abstract class BreakProbeMixin {
  @Inject(method = "startDestroyBlock", at = @At("HEAD"))
  private void openorigins$breakStart(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
    try {
      Minecraft mc = Minecraft.getInstance();
      if (mc == null || mc.level == null || mc.player == null) {
        return;
      }
      net.minecraft.world.level.block.state.BlockState state = mc.level.getBlockState(pos);
      String tool = net.minecraft.core.registries.BuiltInRegistries.ITEM
          .getKey(mc.player.getMainHandItem().getItem()).toString();
      com.openorigins.client.util.BreakProbe.onStart(pos, state, tool);
    } catch (Throwable ignored) {
    }
  }

  @Inject(method = "destroyBlock", at = @At("HEAD"))
  private void openorigins$breakDone(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
    try {
      com.openorigins.client.util.BreakProbe.onClientDone();
    } catch (Throwable ignored) {
    }
  }

  @Inject(method = "stopDestroyBlock", at = @At("HEAD"))
  private void openorigins$breakAbort(CallbackInfo ci) {
    try {
      com.openorigins.client.util.BreakProbe.onAbort();
    } catch (Throwable ignored) {
    }
  }
}
