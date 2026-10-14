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
  @Inject(method = "handleBossUpdate", at = @At("HEAD"))
  private void openorigins$bossColor(ClientboundBossEventPacket packet, CallbackInfo ci) {
    try {
      packet.dispatch(new ClientboundBossEventPacket.Handler() {
        @Override
        public void add(UUID id, Component name, float progress,
            net.minecraft.world.BossEvent.BossBarColor color,
            net.minecraft.world.BossEvent.BossBarOverlay overlay, boolean darken, boolean music,
            boolean fog) {
          var c = name.getStyle().getColor();
          com.openorigins.client.util.DebugLog.info("[bosscolor] add '{}' color={} barcolor={}",
              name.getString(), c == null ? "none" : c.toString(), color);
        }

        @Override
        public void updateName(UUID id, Component name) {
          var c = name.getStyle().getColor();
          com.openorigins.client.util.DebugLog.info("[bosscolor] rename '{}' color={}",
              name.getString(), c == null ? "none" : c.toString());
        }
      });
    } catch (Throwable ignored) {
    }
  }
}
