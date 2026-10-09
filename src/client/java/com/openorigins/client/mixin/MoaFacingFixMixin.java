package com.openorigins.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-side hack for the server's backwards Moa mount model: the invisible
 * mule faces the direction of travel but the body parts were assembled facing
 * the opposite way, so in front-view (F5-2) the rider looks seated backwards.
 * Rotates co-passenger item displays 180 deg at render-state extraction, which
 * is visuals-only (hitboxes and interaction are untouched) and scoped to the
 * local player's own mount (invisible mule carrying the player).
 */
@Mixin(DisplayRenderer.ItemDisplayRenderer.class)
public abstract class MoaFacingFixMixin {
  private static final Logger LOG = LoggerFactory.getLogger("openorigins");
  private static final java.util.Set<Integer> loggedMounts =
      java.util.Collections.synchronizedSet(new java.util.HashSet<>());

  @Inject(
      method = "extractRenderState(Lnet/minecraft/world/entity/Display$ItemDisplay;Lnet/minecraft/client/renderer/entity/state/ItemDisplayEntityRenderState;F)V",
      at = @At("TAIL"))
  private void openorigins$flipMoaPart(Display.ItemDisplay entity,
      net.minecraft.client.renderer.entity.state.ItemDisplayEntityRenderState state,
      float partialTick, CallbackInfo ci) {
    if (!com.openorigins.client.OpenOriginsConfig.getInstance()
        .isEnabled(com.openorigins.client.OriginFeature.MOA_FACING_FIX)) {
      return;
    }
    if (!com.openorigins.client.ConnectionUtil.isConnected()) {
      return;
    }
    Entity vehicle = entity.getVehicle();
    if (vehicle == null || vehicle.isRemoved()) {
      return;
    }
    Minecraft mc = Minecraft.getInstance();
    if (mc.player == null || !vehicle.getPassengers().contains(mc.player)) {
      return;
    }
    if (!vehicle.isInvisible()) {
      return;
    }
    if (!"minecraft:mule".equals(BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType()).toString())) {
      return;
    }
    // VERTICAL billboard takes yaw from the camera and ignores entity yaw, so a yaw
    // offset alone is a no-op. Pin the parts as a rigid body (FIXED) facing travel
    // direction instead: the model is built backwards (visual front is local -Z).
    Display.RenderState rs = state.renderState;
    // Freshly spawned displays have no render state until their first tick;
    // vanilla skips rendering them, so there is nothing to fix yet.
    if (rs == null) {
      return;
    }
    if (rs.billboardConstraints() != Display.BillboardConstraints.FIXED) {
      state.renderState = new Display.RenderState(rs.transformation(),
          Display.BillboardConstraints.FIXED, rs.brightnessOverride(),
          rs.shadowRadius(), rs.shadowStrength(), rs.glowColorOverride());
    }
    state.entityYRot += 180.0f;
    if (loggedMounts.add(vehicle.getId())) {
      LOG.info("[moa] flipping mount #{} parts 180 deg (visual only)", vehicle.getId());
    }
  }
}
