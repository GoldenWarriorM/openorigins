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
 * vehicle controls their visibility, so inflating the box (6) is pointless.
 * Text displays are vanilla's own business.
 *
 * 26.2 and 26.3 differ: 26.3 added a partial-tick parameter, so the old
 * one-arg descriptor no longer exists there. Both are injected with
 * require = 0; exactly one matches per version.
 */
@Mixin(net.minecraft.client.renderer.entity.EntityRenderer.class)
public abstract class EntityCullingMixin {
  @ModifyReturnValue(
      method = "getBoundingBoxForCulling(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/AABB;",
      at = @At("RETURN"), require = 0)
  private AABB openorigins$expandCullingBox(AABB original, Entity entity) {
    return openorigins$expand(original, entity);
  }

  @ModifyReturnValue(
      method = "getBoundingBoxForCulling(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/world/phys/AABB;",
      at = @At("RETURN"), require = 0)
  private AABB openorigins$expandCullingBoxTick(AABB original, Entity entity, float partialTick) {
    return openorigins$expand(original, entity);
  }

  private static AABB openorigins$expand(AABB original, Entity entity) {
    if (!com.openorigins.client.util.ExpandModelBox.eligible(entity)) {
      return original;
    }
    if (!OpenOriginsConfig.getInstance().isEnabled(OriginFeature.EXPAND_MODEL_BOUNDING_BOX)) return original;
    if (!ConnectionUtil.isConnected()) return original;
    return original.inflate(com.openorigins.client.util.ExpandModelBox.inflate(entity));
  }
}
