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
      at = @At("TAIL"))
  private void openorigins$extrapolate(ArmorStand entity,
      net.minecraft.client.renderer.entity.state.ArmorStandRenderState state,
      float partialTick, CallbackInfo ci) {
    if (!com.openorigins.client.OpenOriginsConfig.getInstance()
        .isEnabled(com.openorigins.client.OriginFeature.ENTITY_EXTRAPOLATION)) {
      return;
    }
    if (!com.openorigins.client.ConnectionUtil.isConnected()) {
      return;
    }
    double[] o;
    try {
      o = com.openorigins.client.util.MobExtrapolation.offset(entity);
    } catch (Throwable t) {
      return;
    }
    if (o == null) {
      return;
    }
    com.openorigins.client.util.MobExtrapolation.noteApplied();
    state.x += o[0];
    state.y += o[1];
    state.z += o[2];
    if (state.nameTagAttachment != null) {
      state.nameTagAttachment = state.nameTagAttachment.add(o[0], o[1], o[2]);
    }
  }
}
