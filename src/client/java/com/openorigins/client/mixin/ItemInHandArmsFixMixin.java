package com.openorigins.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.openorigins.client.ConnectionUtil;
import com.openorigins.client.util.AllayArmsFix;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Render-time nudge for allay arm models held by armor stands (third-person hand path). */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandArmsFixMixin {
  @Inject(method = "submitArmWithItem",
      at = @At(value = "INVOKE",
          target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
  private void openorigins$shiftArms(ArmedEntityRenderState state, ItemStackRenderState item,
      ItemStack itemStack, HumanoidArm arm, PoseStack poseStack,
      SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
    if (!ConnectionUtil.isConnected()) {
      return;
    }
    if (!AllayArmsFix.isArmsItem(itemStack)) {
      return;
    }
    AllayArmsFix.markFlagged();
    if (!com.openorigins.client.OpenOriginsConfig.getInstance()
        .isEnabled(com.openorigins.client.OriginFeature.ALLAY_ARMS_FIX)) {
      return;
    }
    float dx = AllayArmsFix.shiftBlocks();
    if (dx != 0.0f) {
      poseStack.translate(dx, 0.0f, 0.0f);
    }
  }
}
