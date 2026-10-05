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

  @Inject(method = "extractRenderState", at = @At("TAIL"))
  private void openorigins$flagArms(Display.ItemDisplay entity, ItemDisplayEntityRenderState state,
      float partialTick, CallbackInfo ci) {
    if (!ConnectionUtil.isConnected()) {
      return;
    }
    try {
      Display.ItemDisplay.ItemRenderState rs = entity.itemRenderState();
      if (rs != null && AllayArmsFix.isArmsItem(rs.itemStack())) {
        OPENORIGINS$ARMS.add(state);
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
    if (!OPENORIGINS$ARMS.remove(state)) {
      return;
    }
    float dx = AllayArmsFix.shiftBlocks();
    if (dx != 0.0f) {
      poseStack.translate(dx, 0.0f, 0.0f);
    }
  }
}
