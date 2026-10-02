package com.openorigins.client.mixin;

import com.openorigins.client.ConnectionUtil;
import com.openorigins.client.OpenOriginsConfig;
import com.openorigins.client.OriginFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Freezes client-side TripWire shape/placement so custom blocks built on it don't flicker. */
@Mixin(TripWireBlock.class)
public abstract class TripWireFixMixin {
  @Inject(method = "getStateForPlacement", at = @At("HEAD"), cancellable = true)
  private void openorigins$keepPlacement(BlockPlaceContext ctx, CallbackInfoReturnable<BlockState> cir) {
    if (OpenOriginsConfig.getInstance().isEnabled(OriginFeature.BLOCK_ENHANCEMENTS) && ConnectionUtil.isConnected()) {
      cir.setReturnValue(((TripWireBlock) (Object) this).defaultBlockState());
    }
  }

  @Inject(method = "updateShape", at = @At("HEAD"), cancellable = true)
  private void openorigins$freezeShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
      BlockPos pos, Direction dir, BlockPos otherPos, BlockState otherState, RandomSource random,
      CallbackInfoReturnable<BlockState> cir) {
    if (OpenOriginsConfig.getInstance().isEnabled(OriginFeature.BLOCK_ENHANCEMENTS) && ConnectionUtil.isConnected()) {
      cir.setReturnValue(state);
    }
  }
}
