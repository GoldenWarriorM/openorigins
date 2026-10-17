package com.openorigins.client.mixin;

import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Experimental render-ahead for armor-stand rig parts (visual only). */
@Mixin(ArmorStandRenderer.class)
public abstract class StandExtrapolationMixin {
  @Inject(
      method = "extractRenderState(Lnet/minecraft/world/entity/decoration/ArmorStand;Lnet/minecraft/client/renderer/entity/state/ArmorStandRenderState;F)V",
      at = @At("TAIL"), require = 0)
  private void openorigins$extrapolate(ArmorStand entity,
      net.minecraft.client.renderer.entity.state.ArmorStandRenderState state,
      float partialTick, CallbackInfo ci) {
    com.openorigins.client.util.MobExtrapolation.apply(entity, state);
  }
}
