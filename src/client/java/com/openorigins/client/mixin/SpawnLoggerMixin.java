package com.openorigins.client.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Temporary diagnostic: logs every spawned entity (type + id). Remove after debugging. */
@Mixin(ClientPacketListener.class)
public abstract class SpawnLoggerMixin {
  private static final Logger SPAWN_LOG = LoggerFactory.getLogger("openorigins");

  @Inject(method = "handleAddEntity", at = @At("HEAD"))
  private void openorigins$logSpawn(ClientboundAddEntityPacket packet, CallbackInfo ci) {
    SPAWN_LOG.info("[OpenOrigins spawn] {} id={}",
        net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(packet.getType()),
        packet.getId());
  }
}
