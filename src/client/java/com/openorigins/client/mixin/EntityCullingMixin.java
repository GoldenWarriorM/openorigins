package com.openorigins.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.openorigins.client.ConnectionUtil;
import com.openorigins.client.OpenOriginsConfig;
import com.openorigins.client.OriginFeature;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * getBoundingBoxForCulling is declared on EntityRenderer (superclass),
 * so the mixin targets it with filters. ArmorStands and model Display parts
 * (block/item); skip passenger stands (worn cosmetics on players) — the
 * vehicle controls their visibility, so inflating the box (4.5) is pointless.
 * Text displays are vanilla's own business.
 */
@Mixin(net.minecraft.client.renderer.entity.EntityRenderer.class)
public abstract class EntityCullingMixin {
  @ModifyReturnValue(method = "getBoundingBoxForCulling", at = @At("RETURN"), require = 0)
  private AABB openorigins$expandCullingBox(AABB original, Entity entity) {
    if (!com.openorigins.client.util.ExpandModelBox.eligible(entity)) {
      return original;
    }
    if (!OpenOriginsConfig.getInstance().isEnabled(OriginFeature.EXPAND_MODEL_BOUNDING_BOX)) return original;
    if (!ConnectionUtil.isConnected()) return original;
    return original.inflate(com.openorigins.client.util.ExpandModelBox.inflate(entity));
  }
}
