package com.openorigins.client;

import com.openorigins.client.util.OutboxStats;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Debug outbox monitor (bottom-left): recent requests our mod sent to the
 * server plus 1m / 30m / session rates. Orange = waiting for an answer,
 * red = no answer within timeout, green = answered (+round-trip).
 * Visible briefly after activity, only with Debug Logging on.
 */
public final class ServerOutboxHud implements net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement {
  public static final Identifier ID = Identifier.fromNamespaceAndPath("openorigins", "outbox");
  private static final long VISIBLE_MS = 8000;

  private record Row(String text, int color) {}

  @Override
  public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
    Minecraft mc = Minecraft.getInstance();
    if (mc == null || mc.player == null || mc.gui.hud.isHidden()) {
      return;
    }
    if (!OpenOriginsConfig.getInstance().isEnabled(OriginFeature.DEBUG_LOGGING)) {
      return;
    }
    if (!ConnectionUtil.isConnected()) {
      return;
    }
    long now = System.currentTimeMillis();
    if (now - OutboxStats.lastMs() > VISIBLE_MS) {
      return;
    }
    Font font = mc.font;
    java.util.ArrayList<Row> rows = new java.util.ArrayList<>();
    java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("HH:mm:ss");
    for (OutboxStats.Entry e : OutboxStats.recent(4)) {
      String text = fmt.format(new java.util.Date(e.ms())) + " " + e.label();
      int color;
      if (e.acked()) {
        text += " +" + (e.ackMs() - e.ms()) + "ms";
        color = 0xFF55FF55;
      } else if (now - e.ms() > e.timeoutMs()) {
        color = 0xFFFF5555;
      } else {
        color = 0xFFFFAA00;
      }
      rows.add(new Row(text, color));
    }
    rows.add(new Row("1m " + OutboxStats.lastMinute() + "  30m " + OutboxStats.lastHalfHour()
        + "  ses " + OutboxStats.sessionTotal(), 0xFFFFFFFF));
    int maxW = 0;
    for (Row r : rows) {
      maxW = Math.max(maxW, font.width(r.text()));
    }
    int totalH = rows.size() * 10 + 2;
    int sh = mc.getWindow().getGuiScaledHeight();
    int x = 4;
    int y = sh - 4 - totalH;
    for (Row r : rows) {
      graphics.text(font, r.text(), x, y + 1, r.color());
      y += 10;
    }
  }
}
