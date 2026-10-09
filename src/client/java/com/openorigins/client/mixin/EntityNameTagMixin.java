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
 *
 * The anchor is computed straight from the entity (like vanilla does),
 * independent of render-state attachments: the server hides vanilla names
 * via teams, so the state attachment is usually null.
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
      try {
        holder.openorigins$setBase(entity.getAttachments().getNullable(
            net.minecraft.world.entity.EntityAttachment.NAME_TAG, 0, entity.getYRot(partialTick)));
      } catch (Throwable ignored) {
        holder.openorigins$setBase(null);
      }
      openorigins$logTagOnce(entity, tag);
    } else {
      holder.openorigins$setTag(null);
    }
  }

  private static final java.util.Map<Integer, String> loggedTagState =
      java.util.Collections.synchronizedMap(new java.util.HashMap<>());

  /** One-time tag content log (debug toggle). */
  private static void openorigins$logTagOnce(Entity entity, EntityData.NameTag tag) {
    try {
      List<Component> lines;
      synchronized (tag.lines()) {
        lines = new ArrayList<>(tag.lines());
      }
      StringBuilder sb = new StringBuilder();
      for (Component c : lines) {
        if (sb.length() > 0) {
          sb.append(" // ");
        }
        String s = c.getString();
        sb.append(s.length() > 30 ? s.substring(0, 30) : s);
      }
      String sig = lines.size() + "|" + sb.toString().hashCode();
      if (sig.equals(loggedTagState.get(entity.getId()))) {
        return;
      }
      loggedTagState.put(entity.getId(), sig);
      com.openorigins.client.util.DebugLog.info("[nametag] {}#{} lines={} [{}]",
          net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()),
          entity.getId(), lines.size(), sb);
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
    if (OpenOriginsConfig.getInstance().isEnabled(OriginFeature.HIDE_NAMES)
        && ConnectionUtil.isConnected()
        && isHudHidden()) {
      ci.cancel();
      return;
    }
    List<Component> lines;
    synchronized (tag.lines()) {
      lines = new ArrayList<>(tag.lines());
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
    ci.cancel();
  }

  private static boolean isHudHidden() {
    Minecraft mc = Minecraft.getInstance();
    return mc.gui != null && mc.gui.hud != null && mc.gui.hud.isHidden();
  }
}
