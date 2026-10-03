package com.openorigins.client;

import java.util.Locale;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.sounds.SoundEvents;

/**
 * Sound plus toast notification when one of the player's auction lots sells.
 * Heuristic over system-message text; gated on ConnectionUtil.isConnected().
 */
public final class AuctionPing {
  private static final Pattern SOLD_PATTERN = Pattern.compile(
      "(?i)(auction.*sold|sold.*auction|auction.*claimed)");

  private AuctionPing() {}

  public static void register() {
    ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
      if (overlay) return;
      if (!OpenOriginsConfig.getInstance().isEnabled(OriginFeature.AUCTION_PING)) return;
      if (!ConnectionUtil.isConnected()) return;
      String text = message.getString().toLowerCase(Locale.ROOT);
      if (SOLD_PATTERN.matcher(text).find()) {
        var client = net.minecraft.client.Minecraft.getInstance();
        if (client.player != null) {
          client.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
          com.openorigins.client.screen.ReconnectToast.show(client.gui.toastManager(),
              net.minecraft.network.chat.Component.literal("Auction"), message.copy(),
              com.openorigins.client.screen.ReconnectToast.Mode.HEADER);
        }
      }
    });
  }
}
