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
}
