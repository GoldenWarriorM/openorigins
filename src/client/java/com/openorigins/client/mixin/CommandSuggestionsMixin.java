package com.openorigins.client.mixin;

import com.openorigins.client.util.HomeListFetcher;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Steals our own tab-complete responses for the homes menu. */
@Mixin(ClientPacketListener.class)
public abstract class CommandSuggestionsMixin {
  @Inject(method = "handleCommandSuggestions", at = @At("HEAD"), cancellable = true)
  private void openorigins$onSuggestions(ClientboundCommandSuggestionsPacket packet, CallbackInfo ci) {
    try {
      java.util.List<String> texts = new java.util.ArrayList<>();
      StringBuilder tips = new StringBuilder();
      for (ClientboundCommandSuggestionsPacket.Entry e : packet.suggestions()) {
        texts.add(e.text());
        if (e.text().trim().startsWith(":") && e.tooltip().isPresent()) {
          try {
            String tip = e.tooltip().get().getString();
            if (tips.length() < 200) {
              if (tips.length() > 0) tips.append(' ');
              tips.append(e.text().trim()).append('=').append(tip);
            }
          } catch (Throwable ignored) {
          }
        }
      }
      com.openorigins.client.util.EmojiCodes.harvestTexts(texts);
      if (tips.length() > 0) {
        com.openorigins.client.util.DebugLog.info("[emoji] tips: " + tips);
      }
    } catch (Throwable ignored) {
    }
    if (HomeListFetcher.handle(packet)) {
      ci.cancel();
    }
  }
}
