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
      state.nameTag = null;
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
    if (OpenOriginsConfig.getInstance().isEnabled(OriginFeature.HIDE_NAMES)
        && ConnectionUtil.isConnected()
        && isHudHidden()) {
      ci.cancel();
      return;
    }
    Vec3 base = state.nameTagAttachment != null ? state.nameTagAttachment
        : new Vec3(state.x, state.y + state.boundingBoxHeight + 0.5, state.z);
    for (int i = 0; i < lines.size(); i++) {
      Vec3 pos = base.add(0.0, tag.offset() + LINE_SPACING * i, 0.0);
      collector.submitNameTag(poseStack, pos, order, lines.get(i), !state.isDiscrete, state.lightCoords, camera);
    }
    // TEMP DIAG: marker to split "nodes not drawn" from "text draws empty".
    collector.submitNameTag(poseStack, base.add(0.0, tag.offset() + LINE_SPACING * lines.size(), 0.0),
        order, net.minecraft.network.chat.Component.literal("TEST"), !state.isDiscrete,
        state.lightCoords, camera);
    // TEMP DIAG: anchor marker at the local player head (guaranteed on-screen
    // reference to tell a bad base pos from eaten submits).
    try {
      net.minecraft.client.Minecraft mc2 = net.minecraft.client.Minecraft.getInstance();
      if (mc2 != null && mc2.player != null) {
        net.minecraft.world.phys.Vec3 pp = mc2.player.position();
        collector.submitNameTag(poseStack,
            new Vec3(pp.x, pp.y + 2.2, pp.z), order,
            net.minecraft.network.chat.Component.literal("ANCHOR"), !state.isDiscrete,
            state.lightCoords, camera);
      }
    } catch (Throwable ignored) {
    }
    ci.cancel();
  }

  private static boolean isHudHidden() {
    Minecraft mc = Minecraft.getInstance();
    return mc.gui != null && mc.gui.hud != null && mc.gui.hud.isHidden();
  }
}
