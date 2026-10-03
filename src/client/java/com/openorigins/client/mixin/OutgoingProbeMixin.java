package com.openorigins.client.mixin;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Temporary diagnostics: logs sent commands and container clicks. */
@Mixin(Connection.class)
public abstract class OutgoingProbeMixin {
  private static final org.slf4j.Logger PROBE_LOG =
      org.slf4j.LoggerFactory.getLogger("openorigins");

  @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
  private void openorigins$logPacket(Packet<?> packet, CallbackInfo ci) {
    if (packet instanceof ServerboundChatCommandPacket cmd) {
      PROBE_LOG.info("[probe] command: /{}", cmd.command());
    } else if (packet instanceof ServerboundContainerClickPacket click) {
      PROBE_LOG.info("[probe] click: window={} slot={} button={}",
          click.containerId(), click.slotNum(), click.buttonNum());
    }
  }
}
