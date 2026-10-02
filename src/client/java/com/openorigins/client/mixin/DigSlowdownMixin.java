package com.openorigins.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.openorigins.client.ConnectionUtil;
import com.openorigins.client.OpenOriginsConfig;
import com.openorigins.client.OriginFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Custom blocks sit on Note/TripWire and only the server knows their real hardness.
 * The client breaks at vanilla hardness faster than the server, causing ghost breaks
 * with rollback. Slow client prediction for these blocks so the server breaks/confirms
 * first. Full desync removal is impossible client-side — server is authoritative.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class DigSlowdownMixin {
  private static final float CUSTOM_BLOCK_PROGRESS_SCALE = 0.2F;

  @ModifyReturnValue(method = "getDestroyProgress", at = @At("RETURN"))
  private float openorigins$slowCustomDig(float original, Player player, BlockGetter level, BlockPos pos) {
    if (original <= 0.0F) return original;
    if (!OpenOriginsConfig.getInstance().isEnabled(OriginFeature.GHOST_BLOCK_FIX)) return original;
    if (!ConnectionUtil.isConnected()) return original;
    BlockState state = level.getBlockState(pos);
    if (state.is(Blocks.NOTE_BLOCK) || state.is(Blocks.TRIPWIRE)) {
      return original * CUSTOM_BLOCK_PROGRESS_SCALE;
    }
    return original;
  }
}
