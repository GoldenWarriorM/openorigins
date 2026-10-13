package com.openorigins.client.mixin;

import com.openorigins.client.util.EmojiCodes;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feeds the emoji self-learner from outgoing and incoming chat. */
@Mixin(ClientPacketListener.class)
public abstract class EmojiLearnMixin {
  @Inject(method = "sendChat", at = @At("HEAD"))
  private void openorigins$noteSent(String message, CallbackInfo ci) {
    EmojiCodes.noteSent(message);
  }

  @Inject(method = "handleSystemChat", at = @At("TAIL"))
  private void openorigins$learnSystem(ClientboundSystemChatPacket packet, CallbackInfo ci) {
    if (!packet.overlay()) {
      try {
        EmojiCodes.learnFromIncoming(packet.content().getString());
      } catch (Throwable ignored) {
      }
    }
  }

  @Inject(method = "handlePlayerChat", at = @At("TAIL"))
  private void openorigins$learnPlayer(ClientboundPlayerChatPacket packet, CallbackInfo ci) {
    try {
      EmojiCodes.learnFromIncoming(packet.body().content());
    } catch (Throwable ignored) {
    }
  }
}
