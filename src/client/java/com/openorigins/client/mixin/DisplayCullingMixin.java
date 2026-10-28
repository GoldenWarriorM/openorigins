package com.openorigins.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.openorigins.client.ConnectionUtil;
import com.openorigins.client.OpenOriginsConfig;
import com.openorigins.client.OriginFeature;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * DisplayRenderer overrides getBoundingBoxForCulling (the base EntityRenderer
 * mixin never sees Displays), so model parts need their own expansion.
 * Block/item displays only; text displays are vanilla's own business.
 *
 * 26.2 and 26.3 differ: 26.3 added a partial-tick parameter, so both
 * descriptors are injected with require = 0.
 */
@Mixin(net.minecraft.client.renderer.entity.DisplayRenderer.class)
public abstract class DisplayCullingMixin {
  @ModifyReturnValue(
      method = "getBoundingBoxForCulling(Lnet/minecraft/world/entity/Display;)Lnet/minecraft/world/phys/AABB;",
      at = @At("RETURN"), require = 0)
  private AABB openorigins$expandDisplayCullingBox(AABB original, Display display) {
    return openorigins$expand(original, display);
  }

  @ModifyReturnValue(
      method = "getBoundingBoxForCulling(Lnet/minecraft/world/entity/Display;F)Lnet/minecraft/world/phys/AABB;",
      at = @At("RETURN"), require = 0)
  private AABB openorigins$expandDisplayCullingBoxTick(AABB original, Display display,
      float partialTick) {
    return openorigins$expand(original, display);
  }

  private static AABB openorigins$expand(AABB original, Display display) {
    if (!com.openorigins.client.util.ExpandModelBox.eligible(display)) {
      return original;
    }
    if (!OpenOriginsConfig.getInstance().isEnabled(OriginFeature.EXPAND_MODEL_BOUNDING_BOX)) {
      return original;
    }
    if (!ConnectionUtil.isConnected()) {
      return original;
    }
    return original.inflate(com.openorigins.client.util.ExpandModelBox.inflate(display));
  }
}
