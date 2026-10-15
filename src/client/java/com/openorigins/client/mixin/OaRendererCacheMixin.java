package com.openorigins.client.mixin;

import com.openorigins.client.util.OaRendererCache;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Skips the per-frame renderer map lookup for already-seen entities. */
@Mixin(EntityRenderDispatcher.class)
public abstract class OaRendererCacheMixin {
  @Inject(method = "getRenderer(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;",
      at = @At("HEAD"), cancellable = true)
  private void openorigins$cachedRenderer(Entity entity,
      CallbackInfoReturnable<EntityRenderer<?, ?>> cir) {
    try {
      EntityRenderer<?, ?> hit = OaRendererCache.get(entity);
      if (hit != null) {
        cir.setReturnValue(hit);
      }
    } catch (Throwable ignored) {
    }
  }

  @Inject(method = "getRenderer(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;",
      at = @At("RETURN"))
  private void openorigins$storeRenderer(Entity entity,
      CallbackInfoReturnable<EntityRenderer<?, ?>> cir) {
    try {
      OaRendererCache.put(entity, cir.getReturnValue());
    } catch (Throwable ignored) {
    }
  }
}
