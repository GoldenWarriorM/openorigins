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
    if (com.openorigins.client.util.BackgroundMenu.isArmed()) {
      com.openorigins.client.util.BackgroundMenu.poll();
      return;
    }
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
      com.openorigins.client.util.CosmeticCatalogCache.scan(container);
      com.openorigins.client.util.CosmeticCatalogCache.profilePoll(container);
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
          com.openorigins.client.util.DebugLog.info("[menu-top] {}", dump);
        }
      } catch (Throwable ignored) {
      }
    }
  }

  @Inject(method = "handleContainerContent", at = @At("TAIL"))
  private void openorigins$onContent(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
    com.openorigins.client.util.DebugLog.info("[pkts] content window={} items={}",
        packet.containerId(), packet.items().size());
    com.openorigins.client.util.BackgroundMenu.routeContent(
        packet.containerId(), packet.stateId(), packet.items(), packet.carriedItem());
    openorigins$recapture("content");
    if (packet.containerId() == 0) {
      java.util.List<net.minecraft.world.item.ItemStack> items = packet.items();
      for (int i = 9; i <= 44 && i < items.size(); i++) {
        com.openorigins.client.util.HelmetCosmeticTracker.noteInvSlot(i, items.get(i));
      }
    }
  }

  @Inject(method = "handleContainerClose", at = @At("HEAD"))
  private void openorigins$onServerClose(
      net.minecraft.network.protocol.game.ClientboundContainerClosePacket packet, CallbackInfo ci) {
    com.openorigins.client.util.BadgerCache.noteServerClose(packet.getContainerId());
    com.openorigins.client.util.BackgroundMenu.noteServerClose(packet.getContainerId());
  }

  @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
  private void openorigins$onSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
    if (packet.getContainerId() != 0) {
      com.openorigins.client.util.DebugLog.info("[pkts] slot window={} slot={}",
          packet.getContainerId(), packet.getSlot());
    }
    com.openorigins.client.util.BackgroundMenu.routeSlot(
        packet.getContainerId(), packet.getSlot(), packet.getStateId(), packet.getItem());
    openorigins$recapture("slot");
    if (packet.getContainerId() == 0) {
      com.openorigins.client.util.HelmetCosmeticTracker.noteInvSlot(packet.getSlot(), packet.getItem());
      if (packet.getSlot() == 5) {
        noteArmor(packet.getItem());
      }
    }
  }

  private static void noteArmor(net.minecraft.world.item.ItemStack stack) {
    com.openorigins.client.util.HelmetCosmeticTracker.noteArmor(stack);
    if (stack.isEmpty()) {
      if (com.openorigins.client.util.HelmetCosmeticTracker.hasSeenArmor()) {
        com.openorigins.client.util.HelmetCosmeticTracker.clear();
      }
    } else {
      com.openorigins.client.util.HelmetCosmeticTracker.offerReal(stack);
    }
  }

  @Inject(method = "handleSetCursorItem", at = @At("TAIL"))
  private void openorigins$onCursor(
      net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket packet, CallbackInfo ci) {
    com.openorigins.client.util.HelmetCosmeticTracker.offerReal(packet.contents(), true);
    com.openorigins.client.util.HelmetCosmeticTracker.noteCursor(packet.contents());
  }

  /** Temporary break-sync lab: server air-ack for the tracked break target. */
  @Inject(method = "handleBlockUpdate", at = @At("TAIL"))
  private void openorigins$onBlockUpdate(
      net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
    try {
      com.openorigins.client.util.BreakProbe.onBlockUpdate(packet.getPos(), packet.getBlockState());
    } catch (Throwable ignored) {
    }
  }

  /** Temporary break-sync lab: effect corrections landing mid-break. */
  @Inject(method = "handleUpdateMobEffect", at = @At("TAIL"))
  private void openorigins$onEffect(
      net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket packet, CallbackInfo ci) {
    try {
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      boolean local = mc != null && mc.player != null && packet.getEntityId() == mc.player.getId();
      String id = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
          .getKey(packet.getEffect().value()).toString();
      com.openorigins.client.util.BreakProbe.onEffect(local, id + " x" + packet.getEffectAmplifier()
          + " for " + packet.getEffectDurationTicks() + "t"
          + (packet.effectShowsIcon() ? "" : " noicon")
          + (packet.isEffectVisible() ? "" : " nopart"));
    } catch (Throwable ignored) {
    }
  }

  /** Temporary break-sync lab: effect removal mid-break. */
  @Inject(method = "handleRemoveMobEffect", at = @At("TAIL"))
  private void openorigins$onEffectRemove(
      net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket packet, CallbackInfo ci) {
    try {
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      boolean local = mc != null && mc.player != null && packet.entityId() == mc.player.getId();
      String id = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
          .getKey(packet.effect().value()).toString();
      com.openorigins.client.util.BreakProbe.onEffect(local, id + " REMOVED");
    } catch (Throwable ignored) {
    }
  }

  /** Temporary break-sync lab: attribute corrections landing mid-break. */
  @Inject(method = "handleUpdateAttributes", at = @At("TAIL"))
  private void openorigins$onAttributes(
      net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket packet, CallbackInfo ci) {
    try {
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      boolean local = mc != null && mc.player != null && packet.getEntityId() == mc.player.getId();
      if (!local) {
        return;
      }
      StringBuilder sb = new StringBuilder();
      for (var snap : packet.getValues()) {
        if (sb.length() > 0) {
          sb.append(", ");
        }
        sb.append(net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE
            .getKey(snap.attribute().value())).append('=').append(snap.base());
      }
      com.openorigins.client.util.BreakProbe.onAttributes(true, sb.toString());
    } catch (Throwable ignored) {
    }
  }
}
