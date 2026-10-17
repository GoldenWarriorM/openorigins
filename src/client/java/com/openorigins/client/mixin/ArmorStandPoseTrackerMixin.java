package com.openorigins.client.mixin;

import com.openorigins.client.ConnectionUtil;
import com.openorigins.client.OpenOriginsConfig;
import com.openorigins.client.OriginFeature;
import com.openorigins.client.util.SmoothArmorStand;
import net.minecraft.core.Rotations;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Snapshots the pose before each change for render interpolation.
 * (ArmorStand in 26.2 has no own tick — hook the pose setters
 * driven by server entity-data sync.)
 */
@Mixin(ArmorStand.class)
public abstract class ArmorStandPoseTrackerMixin implements SmoothArmorStand {
  @Unique private Rotations openorigins$prevHead;
  @Unique private Rotations openorigins$prevBody;
  @Unique private Rotations openorigins$prevLeftArm;
  @Unique private Rotations openorigins$prevRightArm;
  @Unique private Rotations openorigins$prevLeftLeg;
  @Unique private Rotations openorigins$prevRightLeg;

  @Unique
  private boolean openorigins$shouldTrack() {
    return OpenOriginsConfig.getInstance().isEnabled(OriginFeature.SMOOTH_ANIMATIONS)
        && ConnectionUtil.isConnected();
  }

  @Inject(method = "setHeadPose", at = @At("HEAD"), require = 0)
  private void openorigins$trackHead(Rotations pose, CallbackInfo ci) {
    if (openorigins$shouldTrack()) openorigins$prevHead = ((ArmorStand) (Object) this).getHeadPose();
  }

  @Inject(method = "setBodyPose", at = @At("HEAD"), require = 0)
  private void openorigins$trackBody(Rotations pose, CallbackInfo ci) {
    if (openorigins$shouldTrack()) openorigins$prevBody = ((ArmorStand) (Object) this).getBodyPose();
  }

  @Inject(method = "setLeftArmPose", at = @At("HEAD"), require = 0)
  private void openorigins$trackLeftArm(Rotations pose, CallbackInfo ci) {
    if (openorigins$shouldTrack()) openorigins$prevLeftArm = ((ArmorStand) (Object) this).getLeftArmPose();
  }

  @Inject(method = "setRightArmPose", at = @At("HEAD"), require = 0)
  private void openorigins$trackRightArm(Rotations pose, CallbackInfo ci) {
    if (openorigins$shouldTrack()) openorigins$prevRightArm = ((ArmorStand) (Object) this).getRightArmPose();
  }

  @Inject(method = "setLeftLegPose", at = @At("HEAD"), require = 0)
  private void openorigins$trackLeftLeg(Rotations pose, CallbackInfo ci) {
    if (openorigins$shouldTrack()) openorigins$prevLeftLeg = ((ArmorStand) (Object) this).getLeftLegPose();
  }

  @Inject(method = "setRightLegPose", at = @At("HEAD"), require = 0)
  private void openorigins$trackRightLeg(Rotations pose, CallbackInfo ci) {
    if (openorigins$shouldTrack()) openorigins$prevRightLeg = ((ArmorStand) (Object) this).getRightLegPose();
  }

  @Override public Rotations openorigins$getPrevHeadPose() { return openorigins$prevHead; }
  @Override public Rotations openorigins$getPrevBodyPose() { return openorigins$prevBody; }
  @Override public Rotations openorigins$getPrevLeftArmPose() { return openorigins$prevLeftArm; }
  @Override public Rotations openorigins$getPrevRightArmPose() { return openorigins$prevRightArm; }
  @Override public Rotations openorigins$getPrevLeftLegPose() { return openorigins$prevLeftLeg; }
  @Override public Rotations openorigins$getPrevRightLegPose() { return openorigins$prevRightLeg; }
}
