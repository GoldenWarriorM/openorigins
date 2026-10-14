package com.openorigins.client.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** TEMP: logs bossbar name colors. */
@Mixin(ClientPacketListener.class)
public abstract class OaBossColorProbeMixin {
  @Inject(method = "handleBossUpdate", at = @At("HEAD"))
  private void openorigins$bossColor(ClientboundBossEventPacket packet, CallbackInfo ci) {
    try {
      java.lang.reflect.Field f =
          ClientboundBossEventPacket.class.getDeclaredField("operation");
      f.setAccessible(true);
      Object op = f.get(packet);
      String cls = op.getClass().getSimpleName();
      if (cls.contains("Add") || cls.contains("Name")) {
        Object name = op.getClass().getMethod("name").invoke(op);
        if (name instanceof net.minecraft.network.chat.Component c) {
          var col = c.getStyle().getColor();
          com.openorigins.client.util.DebugLog.info("[bosscolor] {} name='{}' color={}",
              cls, c.getString(), col == null ? "none" : col.toString());
        }
      }
    } catch (Throwable ignored) {
    }
  }
}
