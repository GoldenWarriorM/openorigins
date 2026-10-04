package com.openorigins.client.mixin;

import com.openorigins.client.util.BackgroundMenu;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets silent background menu fetches swallow their GUI open. */
@Mixin(ClientPacketListener.class)
public abstract class OpenScreenMixin {
  @Inject(method = "handleOpenScreen", at = @At("HEAD"), cancellable = true)
  private void openorigins$backgroundOpen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
    if (BackgroundMenu.handleOpen(packet)) {
      ci.cancel();
    }
  }
}
