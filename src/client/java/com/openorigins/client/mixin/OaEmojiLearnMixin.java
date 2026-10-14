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
      if (!texts.isEmpty()) {
        StringBuilder sb = new StringBuilder();
        for (String t : texts) {
          if (sb.length() > 300) break;
          if (sb.length() > 0) sb.append(' ');
          sb.append('[').append(t).append(']');
        }
        com.openorigins.client.util.DebugLog.info("[emoji] sug batch: " + sb);
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

  @Inject(method = "handlePlayerInfoUpdate",
      at = @At("HEAD"))
  private void openorigins$unlockEmoji(
      net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket packet,
      CallbackInfo ci) {
    if (!openorigins$emojiOn()) return;
    try {
      if (!packet.actions().contains(
          net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER)) {
        return;
      }
      int seen = 0;
      int colon = 0;
      for (net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry e
          : packet.entries()) {
        String name;
        try {
          name = e.profile().name();
        } catch (Throwable t) {
          continue;
        }
        seen++;
        if (name != null && name.startsWith(":")) {
          colon++;
          String id = name.substring(1);
          if (id.endsWith(":") && id.length() > 1) id = id.substring(0, id.length() - 1);
          OaEmojiStore.setUnlocked(id);
        }
      }
      if (colon > 0) {
        com.openorigins.client.util.DebugLog.info("[emoji] tablist add: {} entries, {} colon",
            seen, colon);
      }
    } catch (Throwable ignored) {
    }
  }
}
