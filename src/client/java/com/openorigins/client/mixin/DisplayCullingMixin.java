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
 */
@Mixin(net.minecraft.client.renderer.entity.DisplayRenderer.class)
public abstract class DisplayCullingMixin {
  @ModifyReturnValue(method = "getBoundingBoxForCulling", at = @At("RETURN"), require = 0)
  private AABB openorigins$expandDisplayCullingBox(AABB original, Display display) {
    if (!com.openorigins.client.util.ExpandModelBox.eligible(display)) {
      return original;
    }
    if (!OpenOriginsConfig.getInstance().isEnabled(OriginFeature.EXPAND_MODEL_BOUNDING_BOX)) {
      return original;
    }
    if (!ConnectionUtil.isConnected()) {
      return original;
    }
    return original.inflate(com.openorigins.client.util.ExpandModelBox.INFLATE);
  }
}
