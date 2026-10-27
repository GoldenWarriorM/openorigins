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
  /** Full rescans are idempotent; one per burst is enough (menus change slow). */
  private static final long RECAPTURE_COOLDOWN_MS = 100;
  private static volatile long lastRecaptureMs;

  private static void openorigins$recapture(String why) {
    if (com.openorigins.client.util.BackgroundMenu.isArmed()) {
      com.openorigins.client.util.BackgroundMenu.poll();
      return;
    }
    long now = System.currentTimeMillis();
    if (now - lastRecaptureMs < RECAPTURE_COOLDOWN_MS) {
      return;
    }
    lastRecaptureMs = now;
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
      com.openorigins.client.util.AuctionPriceDb.scanMenu(container.getMenu(),
          container.getMenu().containerId);
      com.openorigins.client.util.CosmeticCatalogCache.scan(container);
      com.openorigins.client.util.CosmeticCatalogCache.profilePoll(container);
    }
  }

  @Inject(method = "handleContainerContent", at = @At("TAIL"))
  private void openorigins$onContent(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
    com.openorigins.client.util.BackgroundMenu.routeContent(
        packet.containerId(), packet.stateId(), packet.items(), packet.carriedItem());
    openorigins$recapture("content");
    if (packet.containerId() == 0) {
      com.openorigins.client.BadgerHud.noteInventoryChanged();
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
    com.openorigins.client.util.BackgroundMenu.routeSlot(
        packet.getContainerId(), packet.getSlot(), packet.getStateId(), packet.getItem());
    openorigins$recapture("slot");
    if (packet.getContainerId() == 0) {
      com.openorigins.client.BadgerHud.noteInventoryChanged();
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

  @Inject(method = "handleSetPlayerInventory", at = @At("TAIL"))
  private void openorigins$onPlayerInventory(
      net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket packet,
      CallbackInfo ci) {
    com.openorigins.client.BadgerHud.noteInventoryChanged();
  }

  /**
   * Login and respawn swap in a new LocalPlayer with an empty inventory; the
   * server's container-content packet fills it back in a moment later. Tell
   * the HUD to keep the last known counts until then instead of flashing 0.
   */
  @Inject(method = "handleLogin", at = @At("TAIL"))
  private void openorigins$onLogin(
      net.minecraft.network.protocol.game.ClientboundLoginPacket packet, CallbackInfo ci) {
    com.openorigins.client.BadgerHud.notePlayerReplaced();
  }

  @Inject(method = "handleRespawn", at = @At("TAIL"))
  private void openorigins$onRespawn(
      net.minecraft.network.protocol.game.ClientboundRespawnPacket packet, CallbackInfo ci) {
    com.openorigins.client.BadgerHud.notePlayerReplaced();
  }

  /** Break-sync: server air-ack completes a predicted custom break. */
  @Inject(method = "handleBlockUpdate", at = @At("TAIL"))
  private void openorigins$onBlockUpdate(
      net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
    try {
      com.openorigins.client.util.BreakSync.onServerAir(packet.getPos(), packet.getBlockState());
    } catch (Throwable ignored) {
    }
  }
}
