package com.openorigins.client.mixin;

import com.openorigins.client.ConnectionUtil;
import com.openorigins.client.OpenOriginsConfig;
import com.openorigins.client.OriginFeature;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Frustum box alone is not enough: vanilla drops entities beyond
 * bbox_size * 64 blocks before frustum is even tested. Mirrors the
 * 4.5 culling expansion with a size floor so distant large models
 * (windmills, NPC rigs) stay rendered. Worn (passenger) stands skipped.
 */
@Mixin(Entity.class)
public abstract class EntityDistanceMixin {
  @Inject(method = "shouldRenderAtSqrDistance", at = @At("HEAD"), cancellable = true)
  private void openorigins$expandRenderDistance(double distance, CallbackInfoReturnable<Boolean> cir) {
    Entity self = (Entity) (Object) this;
    if (self instanceof ArmorStand stand) {
      if (stand.getVehicle() != null) {
        return;
      }
    } else if (!(self instanceof net.minecraft.world.entity.Display.BlockDisplay)
        && !(self instanceof net.minecraft.world.entity.Display.ItemDisplay)) {
      return;
    }
    if (!OpenOriginsConfig.getInstance().isEnabled(OriginFeature.EXPAND_MODEL_BOUNDING_BOX)
        || !ConnectionUtil.isConnected()) {
      return;
    }
    double size = self.getBoundingBox().getSize();
    if (Double.isNaN(size)) {
      size = 1.0;
    }
    size = Math.max(size, 4.0);
    size *= 64.0 * Entity.getViewScale();
    cir.setReturnValue(distance < size * size);
  }
}
