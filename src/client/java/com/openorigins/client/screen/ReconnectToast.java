package com.openorigins.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import java.util.List;

/**
 * Compact toast on toasts.png (content 160px wide):
 * - BODY: cream band rows 34-54, message only, for system notes.
 * - HEADER: dark red rows 0-31, title + one message line, for auction sales.
 */
public class ReconnectToast implements Toast {
  private static final Identifier TEXTURE =
      Identifier.fromNamespaceAndPath("openorigins", "textures/gui/toasts.png");
  private static final long VISIBLE_MS = 5000L;
  private static final int WIDTH = 200;
  private static final int LEFT_W = 24;
  private static final int RIGHT_SRC_X = 100;
  private static final int RIGHT_W = 60;

  public enum Mode {
    BODY,
    HEADER
  }

  private final Component title;
  private final Component message;
  private final Mode mode;
  private List<FormattedCharSequence> lines = List.of();
  private long firstSeen = -1L;

  public ReconnectToast(Component title, Component message, Mode mode) {
    this.title = title;
    this.message = message;
    this.mode = mode;
  }

  public static void show(ToastManager toasts, Component title, Component message) {
    show(toasts, title, message, Mode.BODY);
  }

  public static void show(ToastManager toasts, Component title, Component message, Mode mode) {
    toasts.addToast(new ReconnectToast(title, message, mode));
  }

  @Override
  public int width() {
    return WIDTH;
  }

  @Override
  public int height() {
    return 32;
  }

  @Override
  public Visibility getWantedVisibility() {
    return this.firstSeen < 0 || System.currentTimeMillis() - this.firstSeen < VISIBLE_MS
        ? Visibility.SHOW
        : Visibility.HIDE;
  }

  @Override
  public void update(ToastManager manager, long time) {
    if (this.firstSeen < 0) {
      this.firstSeen = System.currentTimeMillis();
    }
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long time) {
    if (this.lines.isEmpty() && this.message != null) {
      this.lines = font.split(this.message, WIDTH - 64);
    }
    int w = this.width();
    int h = this.height();
    if (this.mode == Mode.HEADER) {
      this.band(graphics, 0, 0, 32, w);
      graphics.text(font, this.title, 30, 6, 0xFFFFFFFF, false);
      if (!this.lines.isEmpty()) {
        graphics.text(font, this.lines.get(0), 30, 19, 0xFFFFFFFF, false);
      }
      return;
    }
    this.band(graphics, 32, 0, 28, w);
    this.band(graphics, 60, 28, 4, w);
    for (int i = 0; i < Math.min(2, this.lines.size()); i++) {
      graphics.text(font, this.lines.get(i), 30, 6 + i * 12, 0xFF222222, false);
    }
  }

  private void band(GuiGraphicsExtractor graphics, int v, int dy, int hh, int w) {
    blit(graphics, 0, dy, 0, v, LEFT_W, hh);
    int x = LEFT_W;
    while (x < w - RIGHT_W) {
      int step = Math.min(64, w - RIGHT_W - x);
      blit(graphics, x, dy, 32, v, step, hh);
      x += step;
    }
    blit(graphics, w - RIGHT_W, dy, RIGHT_SRC_X, v, RIGHT_W, hh);
  }

  private static void blit(GuiGraphicsExtractor graphics, int x, int y, int u, int v, int w, int h) {
    com.openorigins.client.util.VersionCompat.blit(graphics, TEXTURE, x, y, (float) u, (float) v, w, h, 256, 256);
  }
}
