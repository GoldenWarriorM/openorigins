package com.openorigins.client.mixin;

import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Experimental render-ahead for custom-mob display parts (visual only). */
@Mixin(DisplayRenderer.class)
public abstract class DisplayExtrapolationMixin {
  @Inject(
      method = "extractRenderState(Lnet/minecraft/world/entity/Display;Lnet/minecraft/client/renderer/entity/state/DisplayEntityRenderState;F)V",
      at = @At("TAIL"), require = 0)
  private void openorigins$extrapolate(Display entity,
      net.minecraft.client.renderer.entity.state.DisplayEntityRenderState state,
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
      o = com.openorigins.client.util.MobExtrapolation.renderOffset(entity);
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
