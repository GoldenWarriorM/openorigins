package com.openorigins.client.mixin;

import com.openorigins.client.util.HomeCreationConfirm;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Routes server home confirmations to the creation flow. */
@Mixin(ClientPacketListener.class)
public abstract class ChatConfirmMixin {
  @Inject(method = "handleSystemChat", at = @At("HEAD"))
  private void openorigins$onChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
    if (!packet.overlay()) {
      String text = packet.content().getString();
      HomeCreationConfirm.onChat(text);
      String lower = text.toLowerCase(java.util.Locale.ROOT);
      if (lower.contains("task complete!") || lower.contains("tasks complete!")) {
        com.openorigins.client.util.BadgerCache.requestRefresh();
      }
    }
  }

  private static volatile long lastCannotDoThatMs;

  /**
   * Swallows vanilla "unknown command" errors caused by our own background
   * fetches (boss rooms etc. where the commands don't exist). Never touches
   * errors after manual input.
   */
  @Inject(method = "handleSystemChat", at = @At("HEAD"), cancellable = true)
  private void openorigins$suppressOwnCommandError(ClientboundSystemChatPacket packet,
      CallbackInfo ci) {
    if (packet.overlay()) {
      return;
    }
    String text;
    try {
      text = packet.content().getString().toLowerCase(java.util.Locale.ROOT);
    } catch (Throwable t) {
      return;
    }
    if (text.contains("you cannot do that here")) {
      long now = System.currentTimeMillis();
      if (now - lastCannotDoThatMs < 10000) {
        ci.cancel();
      } else {
        lastCannotDoThatMs = now;
      }
      return;
    }
    if (!(text.contains("unknown or incomplete command") || text.contains("unknown command"))) {
      return;
    }
    if (!com.openorigins.client.util.BackgroundMenu.isServerErrorOurs()) {
      // Brigadier error context ("cmd<--[HERE]") arrives as its own packet
      // right after the main line: swallow it too, but only as the tail of
      // an error we already attributed to ourselves.
      String stripped = text.strip();
      if (stripped.endsWith("<--[HERE]")
          && com.openorigins.client.util.BackgroundMenu.isAfterSuppressedError()) {
        ci.cancel();
      }
      return;
    }
    com.openorigins.client.util.BackgroundMenu.noteCommandError();
    ci.cancel();
  }
}
