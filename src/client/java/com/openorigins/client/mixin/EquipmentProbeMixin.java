package com.openorigins.client.mixin;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Temporary diagnostics: logs equipment changes, focusing on the head slot. */
@Mixin(ClientPacketListener.class)
public abstract class EquipmentProbeMixin {
  private static final org.slf4j.Logger PROBE_LOG =
      org.slf4j.LoggerFactory.getLogger("openorigins");

  @Inject(method = "handleSetEquipment", at = @At("HEAD"))
  private void openorigins$logEquipment(ClientboundSetEquipmentPacket packet, CallbackInfo ci) {    Minecraft mc = Minecraft.getInstance();
    if (mc == null || mc.level == null) {
      return;
    }
    Entity entity = mc.level.getEntity(packet.getEntity());
    String who = entity == null ? ("id=" + packet.getEntity())
        : (entity == mc.player ? "self" : entity.getName().getString() + "/id=" + entity.getId());
    List<Pair<EquipmentSlot, ItemStack>> slots = packet.getSlots();
    for (Pair<EquipmentSlot, ItemStack> pair : slots) {
      ItemStack stack = pair.getSecond();
      String what = stack.isEmpty() ? "<empty>"
          : stack.getHoverName().getString() + " x" + stack.getCount();
      PROBE_LOG.info("[equip] {} slot={} -> {}", who, pair.getFirst(), what);
    }
  }

  @Inject(method = "handleContainerSetSlot", at = @At("HEAD"))
  private void openorigins$logSlot(
      net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
    if (packet.getContainerId() == 0 && packet.getSlot() >= 5 && packet.getSlot() <= 8) {
      net.minecraft.world.item.ItemStack stack = packet.getItem();
      PROBE_LOG.info("[equip] self armor slot={} -> {}",
          packet.getSlot(), stack.isEmpty() ? "<empty>"
              : stack.getHoverName().getString() + " x" + stack.getCount());
    }
  }

  @Inject(method = "handleSetCursorItem", at = @At("HEAD"))
  private void openorigins$logCursor(
      net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket packet, CallbackInfo ci) {
    net.minecraft.world.item.ItemStack stack = packet.contents();
    PROBE_LOG.info("[equip] self cursor -> {}",
        stack.isEmpty() ? "<empty>" : stack.getHoverName().getString() + " x" + stack.getCount());
  }
}
