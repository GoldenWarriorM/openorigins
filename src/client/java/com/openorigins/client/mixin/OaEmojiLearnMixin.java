package com.openorigins.client.mixin;

import com.openorigins.client.OpenOriginsConfig;
import com.openorigins.client.OriginFeature;
import com.openorigins.client.util.OaEmojiStore;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Harvests emoji codes from server suggestions and learns glyphs from echo. */
@Mixin(ClientPacketListener.class)
public abstract class OaEmojiLearnMixin {
  private boolean openorigins$emojiOn() {
    try {
      return OpenOriginsConfig.getInstance().isEnabled(OriginFeature.OA_EMOJI_PICKER);
    } catch (Throwable t) {
      return false;
    }
  }

  @Inject(method = "handleCommandSuggestions", at = @At("HEAD"))
  private void openorigins$harvestEmoji(ClientboundCommandSuggestionsPacket packet, CallbackInfo ci) {
    if (!openorigins$emojiOn()) return;
    try {
      java.util.List<String> texts = new java.util.ArrayList<>();
      for (ClientboundCommandSuggestionsPacket.Entry e : packet.suggestions()) {
        texts.add(e.text());
      }
      OaEmojiStore.harvestTexts(texts);
    } catch (Throwable ignored) {
    }
  }

  @Inject(method = "sendChat", at = @At("HEAD"))
  private void openorigins$noteSent(String message, CallbackInfo ci) {
    if (!openorigins$emojiOn()) return;
    OaEmojiStore.noteSent(message);
  }

  @Inject(method = "handleSystemChat", at = @At("TAIL"))
  private void openorigins$learnSystem(ClientboundSystemChatPacket packet, CallbackInfo ci) {
    if (!openorigins$emojiOn() || packet.overlay()) return;
    try {
      OaEmojiStore.learnFromIncoming(packet.content().getString());
    } catch (Throwable ignored) {
    }
  }

  @Inject(method = "handlePlayerChat", at = @At("TAIL"))
  private void openorigins$learnPlayer(ClientboundPlayerChatPacket packet, CallbackInfo ci) {
    if (!openorigins$emojiOn()) return;
    try {
      OaEmojiStore.learnFromIncoming(packet.body().content());
    } catch (Throwable ignored) {
    }
  }
}
