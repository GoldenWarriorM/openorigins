package com.openorigins.client.mixin;

import com.openorigins.client.ConnectionUtil;
import com.openorigins.client.util.AllayArmsFix;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.ItemDisplayEntityRenderState;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.entity.DisplayRenderer$ItemDisplayRenderer")
public abstract class ItemDisplayArmsFixMixin {
  private static final Set<ItemDisplayEntityRenderState> OPENORIGINS$ARMS =
      Collections.newSetFromMap(new WeakHashMap<>());
  private static final java.util.Map<ItemDisplayEntityRenderState, Float> OPENORIGINS$PARTS =
      Collections.synchronizedMap(new WeakHashMap<>());
  private static final Set<Float> OPENORIGINS$LOGGED_PARTS =
      Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
  private static boolean OPENORIGINS$LOGGED;

  @Inject(method = "extractRenderState", at = @At("TAIL"))
  private void openorigins$flagArms(Display.ItemDisplay entity, ItemDisplayEntityRenderState state,
      float partialTick, CallbackInfo ci) {
    if (!ConnectionUtil.isConnected()) {
      return;
    }
    try {
      net.minecraft.world.item.ItemStack shown = null;
      try {
        net.minecraft.world.entity.SlotAccess slot = entity.getSlot(0);
        shown = slot == null ? null : slot.get();
      } catch (Throwable ignored) {
      }
      Float part = AllayArmsFix.partCmd(shown);
      if (part != null) {
        OPENORIGINS$PARTS.put(state, part);
      }
      if (AllayArmsFix.isArmsItem(shown)) {
        OPENORIGINS$ARMS.add(state);
        AllayArmsFix.markFlagged();
        if (!OPENORIGINS$LOGGED) {
          OPENORIGINS$LOGGED = true;
          org.slf4j.LoggerFactory.getLogger("openorigins")
              .info("[allay-arms] fix engaged on {}", AllayArmsFix.describe(shown));
        }
      }
    } catch (Throwable ignored) {
    }
  }

  @Inject(method = "submitInner",
      at = @At(value = "INVOKE",
          target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
  private void openorigins$shiftArms(ItemDisplayEntityRenderState state, PoseStack poseStack,
      SubmitNodeCollector submitNodeCollector, int lightCoords, float interpolationProgress,
      CallbackInfo ci) {
    Float part = OPENORIGINS$PARTS.remove(state);
    if (part != null && OPENORIGINS$LOGGED_PARTS.add(part)) {
      org.slf4j.LoggerFactory.getLogger("openorigins").info(
          "[allay-render] part cmd={} at [{},{},{}]", part,
          String.format(java.util.Locale.ROOT, "%.2f", state.x),
          String.format(java.util.Locale.ROOT, "%.2f", state.y),
          String.format(java.util.Locale.ROOT, "%.2f", state.z));
    }
    if (!OPENORIGINS$ARMS.remove(state)) {
      return;
    }
    float dx = AllayArmsFix.shiftBlocks();
    if (dx != 0.0f) {
      poseStack.translate(dx, 0.0f, 0.0f);
    }
  }
}
