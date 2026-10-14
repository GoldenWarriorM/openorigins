package com.openorigins.client.mixin;

import com.mojang.brigadier.context.CommandContext;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** TEMP: logs outgoing suggestion requests. */
@Mixin(net.minecraft.client.multiplayer.ClientSuggestionProvider.class)
public abstract class OaSugProbeMixin {
  @Inject(method = "customSuggestion", at = @At("HEAD"))
  private void openorigins$logReq(CommandContext<?> context,
      CallbackInfoReturnable<CompletableFuture<?>> cir) {
    try {
      com.openorigins.client.util.DebugLog.info("[emoji] sug request: '{}'", context.getInput());
    } catch (Throwable ignored) {
    }
  }
}
