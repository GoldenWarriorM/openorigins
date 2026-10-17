package com.openorigins.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.openorigins.client.ConnectionUtil;
import com.openorigins.client.OpenOriginsConfig;
import com.openorigins.client.OriginFeature;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Locks FOV to 70 during cutscenes (spectator mode) for fine tuning. */
@Mixin(Camera.class)
public abstract class CutsceneFovMixin {
  @ModifyReturnValue(method = "getFov", at = @At("RETURN"), require = 0)
  private float openorigins$cutsceneFov(float original) {
    if (!OpenOriginsConfig.getInstance().isEnabled(OriginFeature.CUTSCENE_ENHANCEMENTS)) {
      return original;
    }
    if (!ConnectionUtil.isConnected()) {
      return original;
    }
    Minecraft mc = Minecraft.getInstance();
    if (mc != null && mc.player != null && mc.player.isSpectator()) {
      return 70.0F;
    }
    return original;
  }
}
