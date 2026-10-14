package com.openorigins.client.mixin;

import java.util.UUID;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** TEMP: logs bossbar name colors via the packet Handler API. */
@Mixin(ClientPacketListener.class)
public abstract class OaBossColorProbeMixin {
  private static void dump(String tag, net.minecraft.network.chat.Component c) {
    StringBuilder sb = new StringBuilder();
    dumpInto(c, sb);
    com.openorigins.client.util.DebugLog.info("[bosscolor] {} '{}'", tag, sb.toString());
  }

  private static void dumpInto(net.minecraft.network.chat.Component c, StringBuilder sb) {
    var col = c.getStyle().getColor();
    sb.append('[').append(c.getContents() instanceof net.minecraft.network.chat.contents.PlainTextContents p
        ? p.text() : c.getString()).append('|')
        .append(col == null ? "-" : col.toString()).append(']');
    for (net.minecraft.network.chat.Component s : c.getSiblings()) {
      dumpInto(s, sb);
    }
  }
  @Inject(method = "handleBossUpdate", at = @At("HEAD"))
  private void openorigins$bossColor(ClientboundBossEventPacket packet, CallbackInfo ci) {
    try {
      packet.dispatch(new ClientboundBossEventPacket.Handler() {
        @Override
        public void add(UUID id, Component name, float progress,
            net.minecraft.world.BossEvent.BossBarColor color,
            net.minecraft.world.BossEvent.BossBarOverlay overlay, boolean darken, boolean music,
            boolean fog) {
          dump("add", name);
        }

        @Override
        public void updateName(UUID id, Component name) {
          if (name.getString().contains("Collected")) {
            dump("rename", name);
          }
        }
      });
    } catch (Throwable ignored) {
    }
  }
}
