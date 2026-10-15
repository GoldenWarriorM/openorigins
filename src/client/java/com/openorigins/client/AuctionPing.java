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
      "(?i)(auction.*sold|sold.*auction|bought your|auction.*claimed)");
  private static volatile String lastPingText;
  private static volatile long lastPingMs;

  private AuctionPing() {}

  public static void register() {
    ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
      if (overlay) return;
      if (!OpenOriginsConfig.getInstance().isEnabled(OriginFeature.AUCTION_PING)) return;
      if (!ConnectionUtil.isConnected()) return;
      String text = message.getString().toLowerCase(Locale.ROOT);
      if (SOLD_PATTERN.matcher(text).find()) {
        // The server re-sends the same sale line a few times; ping once.
        long now = System.currentTimeMillis();
        if (text.equals(lastPingText) && now - lastPingMs < 10000) {
          return;
        }
        lastPingText = text;
        lastPingMs = now;
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
