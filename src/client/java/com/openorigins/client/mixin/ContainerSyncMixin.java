package com.openorigins.client.mixin;

import com.openorigins.client.util.HomeInfoCache;
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
  private static void openorigins$recapture(String why) {
    Minecraft mc = Minecraft.getInstance();
    if (mc != null && mc.gui.screen() instanceof AbstractContainerScreen<?> container) {
      int n = HomeInfoCache.captureFrom(container);
      if (n > 0) {
        org.slf4j.LoggerFactory.getLogger("openorigins")
            .info("[homes-lore] captured {} homes on {}", n, why);
        com.openorigins.client.screen.HomesMenuScreen.onCaptured();
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
}
