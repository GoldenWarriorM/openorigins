package com.openorigins.client.mixin;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Temporary diagnostics: logs outgoing chat commands. */
@Mixin(Connection.class)
public abstract class CommandProbeMixin {
  @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
  private void openorigins$logCommand(Packet<?> packet, CallbackInfo ci) {
    if (packet instanceof ServerboundChatCommandPacket cmd) {
      if (com.openorigins.client.util.BackgroundMenu.consumeOwnSend()) {
        com.openorigins.client.util.DebugLog.info("[cmd] own /{}", cmd.command());
      } else {
        com.openorigins.client.util.DebugLog.info("[cmd] user /{}", cmd.command());
        // noteForeignCommand() itself ends the fetch only for menu commands:
        // a plain command must not abandon the answer already in flight.
        com.openorigins.client.util.BackgroundMenu.noteForeignCommand(cmd.command());
      }
    }
  }
}
