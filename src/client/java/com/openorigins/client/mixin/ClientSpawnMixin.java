package com.openorigins.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.openorigins.client.ConnectionUtil;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 26.x drops entity spawns when canSpawn is false — on peaceful this cuts
 * slimes used as rig anchors (capes, NPCs). Client-side creation is visual
 * only and the server is authoritative, so always allow (network only).
 */
@Mixin(EntityType.class)
public abstract class ClientSpawnMixin {
  @ModifyReturnValue(method = "canSpawn", at = @At("RETURN"))
  private boolean openorigins$allowClientSpawn(boolean original, Level level) {
    if (original) return true;
    if (!level.isClientSide()) return original;
    if (!ConnectionUtil.isConnected()) return original;
    return true;
  }
}
