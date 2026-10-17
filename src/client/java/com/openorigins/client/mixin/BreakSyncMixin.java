package com.openorigins.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Break-sync prediction: replaces the vanilla per-tick progress increment
 * with the learned server-timed one while the server drives the break
 * (BLOCK_BREAK_SPEED attribute off 1.0). Visual only, capped below 1.0.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class BreakSyncMixin {
  @Redirect(
      method = "continueDestroyBlock",
      at = @At(
          value = "INVOKE",
          target = "Lnet/minecraft/world/level/block/state/BlockState;getDestroyProgress(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"), require = 0)
  private float openorigins$breakIncrement(BlockState state, Player player, BlockGetter level,
      BlockPos pos) {
    float vanilla = state.getDestroyProgress(player, level, pos);
    return com.openorigins.client.util.BreakSync.increment(
        (MultiPlayerGameMode) (Object) this, state, player, level, pos, vanilla);
  }

  @Inject(method = "startDestroyBlock", at = @At("HEAD"), require = 0)
  private void openorigins$breakTarget(BlockPos pos, Direction direction,
      CallbackInfoReturnable<Boolean> cir) {
    try {
      Minecraft mc = Minecraft.getInstance();
      if (mc == null || mc.level == null || mc.player == null) {
        return;
      }
      com.openorigins.client.util.BreakSync.onStart(pos, mc.level.getBlockState(pos), mc.player);
    } catch (Throwable ignored) {
    }
  }

  @Inject(method = "stopDestroyBlock", at = @At("HEAD"), require = 0)
  private void openorigins$breakAbort(CallbackInfo ci) {
    try {
      com.openorigins.client.util.BreakSync.onAbort();
    } catch (Throwable ignored) {
    }
  }

  @Inject(method = "destroyBlock", at = @At("HEAD"), require = 0)
  private void openorigins$breakClientDone(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
    try {
      com.openorigins.client.util.BreakSync.onClientDone();
    } catch (Throwable ignored) {
    }
  }
}
