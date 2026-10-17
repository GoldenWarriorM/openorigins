package com.openorigins.client.mixin;

import com.openorigins.client.ConnectionUtil;
import com.openorigins.client.OpenOriginsConfig;
import com.openorigins.client.OriginFeature;
import com.openorigins.client.util.PoseLerp;
import com.openorigins.client.util.SmoothArmorStand;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ArmorStandRenderer.class)
public abstract class ArmorStandRenderMixin {
  @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/decoration/ArmorStand;Lnet/minecraft/client/renderer/entity/state/ArmorStandRenderState;F)V",
      at = @At("TAIL"), require = 0)
  private void openorigins$interpolatePoses(ArmorStand stand, ArmorStandRenderState state, float partialTick,
      CallbackInfo ci) {
    if (!OpenOriginsConfig.getInstance().isEnabled(OriginFeature.SMOOTH_ANIMATIONS)) return;
    if (!ConnectionUtil.isConnected()) return;
    SmoothArmorStand ext = (SmoothArmorStand) stand;
    state.headPose = PoseLerp.lerp(orCurrent(ext.openorigins$getPrevHeadPose(), stand.getHeadPose()), stand.getHeadPose(), partialTick);
    state.bodyPose = PoseLerp.lerp(orCurrent(ext.openorigins$getPrevBodyPose(), stand.getBodyPose()), stand.getBodyPose(), partialTick);
    state.leftArmPose = PoseLerp.lerp(orCurrent(ext.openorigins$getPrevLeftArmPose(), stand.getLeftArmPose()), stand.getLeftArmPose(), partialTick);
    state.rightArmPose = PoseLerp.lerp(orCurrent(ext.openorigins$getPrevRightArmPose(), stand.getRightArmPose()), stand.getRightArmPose(), partialTick);
    state.leftLegPose = PoseLerp.lerp(orCurrent(ext.openorigins$getPrevLeftLegPose(), stand.getLeftLegPose()), stand.getLeftLegPose(), partialTick);
    state.rightLegPose = PoseLerp.lerp(orCurrent(ext.openorigins$getPrevRightLegPose(), stand.getRightLegPose()), stand.getRightLegPose(), partialTick);
  }

  private static net.minecraft.core.Rotations orCurrent(net.minecraft.core.Rotations prev,
      net.minecraft.core.Rotations current) {
    return prev != null ? prev : current;
  }

  @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/decoration/ArmorStand;D)Z",
      at = @At("HEAD"), cancellable = true, require = 0)
  private void openorigins$hideNameInF1(ArmorStand stand, double distance, CallbackInfoReturnable<Boolean> cir) {
    if (!OpenOriginsConfig.getInstance().isEnabled(OriginFeature.HIDE_NAMES)) return;
    if (!ConnectionUtil.isConnected()) return;
    Minecraft mc = Minecraft.getInstance();
    if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.isHidden()) {
      cir.setReturnValue(false);
    }
  }
}
