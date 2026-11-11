package com.openorigins.client.mixin;

import com.openorigins.client.util.BackgroundMenu;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets silent background menu fetches swallow their GUI open. */
@Mixin(ClientPacketListener.class)
public abstract class OpenScreenMixin {
  @Unique private boolean openorigins$swallowed;

  @Inject(method = "handleOpenScreen", at = @At("HEAD"), cancellable = true)
  private void openorigins$backgroundOpen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
    openorigins$swallowed = BackgroundMenu.handleOpen(packet);
    if (openorigins$swallowed) {
      ci.cancel();
    }
  }

  @Inject(method = "handleOpenScreen", at = @At("TAIL"))
  private void openorigins$noteShownMenu(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
    // Swallowed opens never reach the screen: nothing manual happened.
    // A shown server window is user territory: back any fetch off it.
    // It is also the visible answer to a recent command, if any.
    if (!openorigins$swallowed) {
      BackgroundMenu.noteShownWindow(packet.getContainerId());
      BackgroundMenu.noteManualContainer();
      com.openorigins.client.util.OutboxStats.ack(label ->
          label.startsWith("cmd /") || label.startsWith("key /") || label.startsWith("menu /"));
    }
    openorigins$swallowed = false;
  }
}
