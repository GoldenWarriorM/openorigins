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
      com.openorigins.client.util.DebugLog.info("[cmd] /{}", cmd.command());
      if (System.currentTimeMillis()
          - com.openorigins.client.util.BackgroundMenu.lastOwnSendMsForProbe() > 1500) {
        com.openorigins.client.util.BackgroundMenu.noteForeignCommand();
      }
    }
  }
}
