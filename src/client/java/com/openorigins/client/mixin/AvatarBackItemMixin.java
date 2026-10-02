package com.openorigins.client.mixin;

import com.openorigins.client.ConnectionUtil;
import com.openorigins.client.OpenOriginsConfig;
import com.openorigins.client.OriginFeature;
import com.openorigins.client.render.BackSwagLayer;
import com.openorigins.client.server.EntityData;
import com.openorigins.client.util.BackItemHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** addLayer/extract live on LivingEntityRenderer — mixin targets it, filters to avatars inside. */
@Mixin(LivingEntityRenderer.class)
public abstract class AvatarBackItemMixin {
  @Shadow
  protected abstract boolean addLayer(RenderLayer<?, ?> layer);

  @Inject(method = "<init>", at = @At("TAIL"))
  private void openorigins$addBackSwagLayer(CallbackInfo ci) {
    if ((Object) this instanceof AvatarRenderer avatarRenderer) {
      this.addLayer(new BackSwagLayer(avatarRenderer));
    }
  }

  @Inject(method = "extractRenderState", at = @At("TAIL"))
  private void openorigins$extractBackItem(LivingEntity entity, LivingEntityRenderState state,
      float partialTick, CallbackInfo ci) {
    if (!(entity instanceof Avatar avatar) || !(state instanceof AvatarRenderState avatarState)) {
      return;
    }
    BackItemHolder holder = (BackItemHolder) avatarState;
    if (!OpenOriginsConfig.getInstance().isEnabled(OriginFeature.BACK_SWAG_SLOT)
        || !ConnectionUtil.isConnected()) {
      holder.openorigins$setBackItem(null);
      return;
    }
    ItemStack stack = EntityData.getBackItem(avatar.getId());
    if (stack == null || stack.isEmpty()) {
      holder.openorigins$setBackItem(null);
      return;
    }
    ItemStackRenderState itemState = new ItemStackRenderState();
    Minecraft.getInstance().getItemModelResolver()
        .updateForLiving(itemState, stack, ItemDisplayContext.HEAD, avatar);
    holder.openorigins$setBackItem(itemState);
  }
}
