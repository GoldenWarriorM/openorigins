package com.openorigins.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.openorigins.client.ConnectionUtil;
import com.openorigins.client.OpenOriginsConfig;
import com.openorigins.client.OriginFeature;
import com.openorigins.client.server.EntityData;
import com.openorigins.client.util.TagHolder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Custom multi-line entity tags: lines stacked with 0.3105 spacing,
 * vanilla tag suppressed. Hidden in F1 with the rest of the HUD.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityNameTagMixin {
  private static final double LINE_SPACING = 0.3105;

  @Inject(method = "extractRenderState", at = @At("TAIL"))
  private void openorigins$storeTag(Entity entity, EntityRenderState state, float partialTick,
      CallbackInfo ci) {
    EntityData.NameTag tag = EntityData.getTag(entity.getId());
    TagHolder holder = (TagHolder) state;
    if (tag != null && !tag.lines().isEmpty()) {
      holder.openorigins$setTag(tag);
      // TEMP EXP: no vanilla suppression (vanilla draws nothing here anyway).
      // Anchor like vanilla does: straight from the entity, independent of
      // team-hidden render state attachments.
      try {
        holder.openorigins$setBase(entity.getAttachments().getNullable(
            net.minecraft.world.entity.EntityAttachment.NAME_TAG, 0, entity.getYRot(partialTick)));
      } catch (Throwable ignored) {
        holder.openorigins$setBase(null);
      }
      openorigins$logTagOnce(entity, state, tag);
    } else {
      holder.openorigins$setTag(null);
    }
  }

  private static final java.util.Map<Integer, String> loggedTagState =
      java.util.Collections.synchronizedMap(new java.util.HashMap<>());
  private static volatile String loggedSubmitSig;

  private static void openorigins$logTagOnce(Entity entity, EntityRenderState state,
      EntityData.NameTag tag) {
    try {
      List<Component> lines;
      synchronized (tag.lines()) {
        lines = new ArrayList<>(tag.lines());
      }
      String first = lines.isEmpty() ? "-" : lines.get(0).getString();
      if (first.length() > 40) {
        first = first.substring(0, 40);
      }
      String sig = lines.size() + "|" + tag.offset() + "|" + first.hashCode();
      if (sig.equals(loggedTagState.get(entity.getId()))) {
        return;
      }
      loggedTagState.put(entity.getId(), sig);
      Vec3 base = state.nameTagAttachment != null ? state.nameTagAttachment
          : new Vec3(state.x, state.y + state.boundingBoxHeight + 0.5, state.z);
      com.openorigins.client.util.DebugLog.info(
          "[nametag] {}#{} lines={} offset={} attach={} baseY={} first='{}'",
          net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()),
          entity.getId(), lines.size(), tag.offset(),
          state.nameTagAttachment != null, String.format("%.1f", base.y), first);
    } catch (Throwable ignored) {
    }
  }

  // NOTE: hooked on the int overload (the funnel every renderer calls, including
  // AvatarRenderer's override which bypasses the no-int wrapper for players).
  @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
      at = @At("HEAD"), cancellable = true)
  private void openorigins$renderTag(EntityRenderState state, PoseStack poseStack,
      SubmitNodeCollector collector, CameraRenderState camera, int order, CallbackInfo ci) {
    EntityData.NameTag tag = ((TagHolder) state).openorigins$getTag();
    if (tag == null) return;
    // TEMP EXP: pure addition, no cancel and no vanilla suppression. Vanilla has
    // nothing to draw here anyway (its nameTag stays null: teams hide names).
    List<Component> lines;
    synchronized (tag.lines()) {
      lines = new ArrayList<>(tag.lines());
    }
    try {
      String sig = "S" + lines.size() + "|" + tag.offset() + "|" + order + "|" + state.isDiscrete;
      if (!sig.equals(loggedSubmitSig)) {
        loggedSubmitSig = sig;
        net.minecraft.network.chat.Style st = lines.isEmpty()
            ? net.minecraft.network.chat.Style.EMPTY
            : lines.get(0).getStyle();
        com.openorigins.client.util.DebugLog.info(
            "[nametag-submit] order={} discrete={} light={} lines={} offset={} dist={} attach={} color={} font={} obf={} pos={}",
            order, state.isDiscrete, state.lightCoords, lines.size(), tag.offset(),
            String.format("%.1f", Math.sqrt(state.distanceToCameraSq)),
            state.nameTagAttachment != null, st.getColor(), st.getFont(),
            st.isObfuscated(),
            (state.nameTagAttachment != null ? state.nameTagAttachment
                : new Vec3(state.x, state.y + state.boundingBoxHeight + 0.5, state.z)).toString());
      }
    } catch (Throwable ignored) {
    }
    // TEMP EXP: do not cancel here; vanilla submits nothing (nameTag null).
    if (false && OpenOriginsConfig.getInstance().isEnabled(OriginFeature.HIDE_NAMES)
        && ConnectionUtil.isConnected()
        && isHudHidden()) {
      ci.cancel();
      return;
    }
    Vec3 base = ((TagHolder) state).openorigins$getBase();
    if (base == null) {
      base = state.nameTagAttachment != null ? state.nameTagAttachment
          : new Vec3(state.x, state.y + state.boundingBoxHeight + 0.5, state.z);
    }
    for (int i = 0; i < lines.size(); i++) {
      Vec3 pos = base.add(0.0, tag.offset() + LINE_SPACING * i, 0.0);
      collector.submitNameTag(poseStack, pos, order, lines.get(i), !state.isDiscrete, state.lightCoords, camera);
    }
    // TEMP DIAG: bisection markers.
    // HIGH: 5 above head (rules out occlusion of the head anchor).
    collector.submitNameTag(poseStack, base.add(0.0, 5.0, 0.0),
        order, net.minecraft.network.chat.Component.literal("HIGH"), !state.isDiscrete,
        state.lightCoords, camera);
    // CAM: 3 blocks along the camera look vector (guaranteed dead-center).
    try {
      net.minecraft.client.Camera cam = net.minecraft.client.Minecraft.getInstance()
          .gameRenderer.mainCamera();
      net.minecraft.world.phys.Vec3 cp = cam.position();
      float yaw = cam.yRot() * (float) Math.PI / 180.0f;
      float pitch = cam.xRot() * (float) Math.PI / 180.0f;
      double lx = -Math.sin(yaw) * Math.cos(pitch);
      double ly = -Math.sin(pitch);
      double lz = Math.cos(yaw) * Math.cos(pitch);
      collector.submitNameTag(poseStack,
          new Vec3(cp.x + lx * 3.0, cp.y + ly * 3.0, cp.z + lz * 3.0), order,
          net.minecraft.network.chat.Component.literal("CAM"), true,
          15728880, camera);
    } catch (Throwable ignored) {
    }
    // TEMP EXP: no cancel (see above).
  }

  // TEMP EXP: hook the main submit instead: is THIS context alive?
  @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
      at = @At("HEAD"))
  private void openorigins$probeMainSubmit(EntityRenderState state, PoseStack poseStack,
      SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
    try {
      EntityData.NameTag tag = ((TagHolder) state).openorigins$getTag();
      if (tag == null) {
        return;
      }
      net.minecraft.client.Camera cam = net.minecraft.client.Minecraft.getInstance()
          .gameRenderer.mainCamera();
      net.minecraft.world.phys.Vec3 cp = cam.position();
      float yaw = cam.yRot() * (float) Math.PI / 180.0f;
      float pitch = cam.xRot() * (float) Math.PI / 180.0f;
      collector.submitNameTag(poseStack,
          new Vec3(cp.x - Math.sin(yaw) * Math.cos(pitch) * 3.0,
              cp.y - Math.sin(pitch) * 3.0, cp.z + Math.cos(yaw) * Math.cos(pitch) * 3.0),
          0, net.minecraft.network.chat.Component.literal("MAINSUBMIT"), true,
          15728880, camera);
      String sig = "M-probe";
      if (!sig.equals(loggedSubmitSig)) {
        loggedSubmitSig = sig;
        com.openorigins.client.util.DebugLog.info("[nametag-mainsubmit] probed");
      }
    } catch (Throwable ignored) {
    }
  }

  private static boolean isHudHidden() {
    Minecraft mc = Minecraft.getInstance();
    return mc.gui != null && mc.gui.hud != null && mc.gui.hud.isHidden();
  }
}
