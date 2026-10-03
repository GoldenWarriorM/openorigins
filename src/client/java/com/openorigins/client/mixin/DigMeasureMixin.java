package com.openorigins.client.mixin;

import com.openorigins.client.util.BlockHardnessCache;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Measures real break durations for the hardness learner. */
@Mixin(MultiPlayerGameMode.class)
public abstract class DigMeasureMixin {
  @Inject(method = "startDestroyBlock", at = @At("HEAD"))
  private void openorigins$digStart(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
    BlockHardnessCache.onDigStart(pos);
  }

  @Inject(method = "stopDestroyBlock", at = @At("HEAD"))
  private void openorigins$digStop(CallbackInfo ci) {
    BlockHardnessCache.onDigAbort();
  }
}
