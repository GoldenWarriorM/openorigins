package com.openorigins.client.mixin;

import com.openorigins.client.util.HomeFetch;
import com.openorigins.client.util.HomeInfoCache;
import com.openorigins.client.util.OpenWorldCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Re-captures home lore when container contents arrive after opening. */
@Mixin(ClientPacketListener.class)
public abstract class ContainerSyncMixin {
  private static String lastTopSig = "";

  private static void openorigins$recapture(String why) {
    Minecraft mc = Minecraft.getInstance();
    if (mc != null && mc.gui.screen() instanceof AbstractContainerScreen<?> container) {
      int n = HomeInfoCache.captureFrom(container);
      if (n > 0) {
        org.slf4j.LoggerFactory.getLogger("openorigins")
            .info("[homes-lore] captured {} homes on {}", n, why);
        HomeFetch.onCaptured();
      }
      OpenWorldCache.scan(container);
      com.openorigins.client.util.BadgerCache.scan(container);
      try {
        Object inv = mc.player != null ? mc.player.getInventory() : null;
        StringBuilder sig = new StringBuilder();
        StringBuilder dump = new StringBuilder();
        int idx = 0;
        for (net.minecraft.world.inventory.Slot slot : container.getMenu().slots) {
          if (inv == null || slot.container != inv) {
            if (slot.hasItem()) {
              net.minecraft.world.item.ItemStack stack = slot.getItem();
              String nm = stack.getHoverName().getString();
              sig.append(idx).append('=').append(nm).append('x').append(stack.getCount()).append(';');
              net.minecraft.world.item.component.ItemLore lore =
                  stack.get(net.minecraft.core.component.DataComponents.LORE);
              String first = "";
              if (lore != null && !lore.lines().isEmpty()) {
                first = lore.lines().get(0).getString();
                if (first.length() > 50) {
                  first = first.substring(0, 50);
                }
              }
              if (dump.length() > 0) {
                dump.append(" | ");
              }
              dump.append(idx).append(':').append(nm).append(" x").append(stack.getCount())
                  .append(" [").append(first).append(']');
            }
          }
          idx++;
        }
        String s = sig.toString();
        if (!s.isEmpty() && !s.equals(lastTopSig)) {
          lastTopSig = s;
          org.slf4j.LoggerFactory.getLogger("openorigins").info("[menu-top] {}", dump);
        }
      } catch (Throwable ignored) {
      }
    }
  }

  @Inject(method = "handleContainerContent", at = @At("TAIL"))
  private void openorigins$onContent(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
    openorigins$recapture("content");
  }

  @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
  private void openorigins$onSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
    openorigins$recapture("slot");
  }

  @Inject(method = "handleBlockUpdate", at = @At("TAIL"))
  private void openorigins$onBlockUpdate(
      net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
    com.openorigins.client.util.BlockHardnessCache.onBlockChange(packet.getPos(), packet.getBlockState());
  }
}
