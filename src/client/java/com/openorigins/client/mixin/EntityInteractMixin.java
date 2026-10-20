package com.openorigins.client.mixin;

import com.openorigins.client.util.BackgroundMenu;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Records player right-clicks on entities. NPC menus arrive as generic,
 * glyph-titled containers — indistinguishable from our silent fetch answers —
 * so the interaction is the only reliable signal that the next open belongs
 * to the user and must not be swallowed.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class EntityInteractMixin {
  @Inject(
      method = "interact(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/EntityHitResult;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",
      at = @At("HEAD"), require = 0)
  private void openorigins$noteEntityInteraction(Player player, Entity entity,
      EntityHitResult hitResult, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
    try {
      BackgroundMenu.noteEntityInteraction(entity);
    } catch (Throwable ignored) {
    }
  }
}
