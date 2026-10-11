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
      HomeCreationConfirm.onChat(packet.content().getString());
    }
  }

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
    if (!(text.contains("unknown or incomplete command") || text.contains("unknown command"))) {
      return;
    }
    if (!com.openorigins.client.util.BackgroundMenu.isServerErrorOurs()) {
      return;
    }
    com.openorigins.client.util.BackgroundMenu.noteCommandError();
    ci.cancel();
  }
}
