package com.openorigins.client.mixin;

import com.openorigins.client.ConnectionUtil;
import com.openorigins.client.OpenOriginsConfig;
import com.openorigins.client.OriginFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freezes mouse look during cutscenes (spectator mode). */
@Mixin(MouseHandler.class)
public abstract class CutsceneMouseMixin {
  @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"), cancellable = true, require = 0)
  private void openorigins$freezeMouse(CallbackInfo ci) {
    if (!OpenOriginsConfig.getInstance().isEnabled(OriginFeature.CUTSCENE_ENHANCEMENTS)) {
      return;
    }
    if (!ConnectionUtil.isConnected()) {
      return;
    }
    Minecraft mc = Minecraft.getInstance();
    if (mc != null && mc.player != null && mc.player.isSpectator()) {
      ci.cancel();
    }
  }
}
