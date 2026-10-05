package com.openorigins.client.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Temporary lifecycle watch: which display/interaction entities spawn and die near the player. */
@Mixin(ClientPacketListener.class)
public abstract class SpawnWatchMixin {
  private static final Logger LOG = LoggerFactory.getLogger("openorigins");
  private static final java.util.Set<Integer> WATCHED =
      java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

  @Inject(method = "handleAddEntity", at = @At("TAIL"))
  private void openorigins$logSpawn(ClientboundAddEntityPacket packet, CallbackInfo ci) {
    try {
      String type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
          .getKey(packet.getType()).toString();
      if (!type.endsWith("item_display") && !type.endsWith("interaction")
          && !type.endsWith("block_display") && !type.endsWith("armor_stand")) {
        return;
      }
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      if (mc == null || mc.player == null) {
        return;
      }
      double d = Math.sqrt(mc.player.distanceToSqr(packet.getX(), packet.getY(), packet.getZ()));
      if (d > 60) {
        return;
      }
      WATCHED.add(packet.getId());
      LOG.info("[allay-life] +{} {} at [{},{},{}]",
          type + "#" + packet.getId(),
          String.format(java.util.Locale.ROOT, "%.0fm", d),
          String.format(java.util.Locale.ROOT, "%.1f", packet.getX()),
          String.format(java.util.Locale.ROOT, "%.1f", packet.getY()),
          String.format(java.util.Locale.ROOT, "%.1f", packet.getZ()));
    } catch (Throwable ignored) {
    }
  }

  @Inject(method = "handleRemoveEntities", at = @At("TAIL"))
  private void openorigins$logRemove(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
    try {
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      if (mc == null || mc.player == null || mc.level == null) {
        return;
      }
      for (int id : packet.getEntityIds()) {
        if (WATCHED.remove(id)) {
          LOG.info("[allay-life] -#{}", id);
        }
      }
    } catch (Throwable ignored) {
    }
  }
}
