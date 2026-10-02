package com.openorigins.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import java.util.List;

/** Custom toast on toasts.png (256x256). */
public class ReconnectToast implements Toast {
  private static final Identifier TEXTURE =
      Identifier.fromNamespaceAndPath("openorigins", "textures/gui/toasts.png");
  private static final long VISIBLE_MS = 5000L;

  private final Component title;
  private final Component message;
  private List<FormattedCharSequence> lines = List.of();
  private long firstSeen = -1L;

  public ReconnectToast(Component title, Component message) {
    this.title = title;
    this.message = message;
  }

  public static void show(ToastManager toasts, Component title, Component message) {
    toasts.addToast(new ReconnectToast(title, message));
  }

  @Override
  public int width() {
    return 160;
  }

  @Override
  public int height() {
    return 64;
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
      this.lines = font.split(this.message, 120);
    }
    graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, 0, 0, 0.0F, 0.0F, 160, 64, 256, 256);
    graphics.text(font, this.title, 30, 7, 0xFFFFFFFF, false);
    for (int i = 0; i < Math.min(2, this.lines.size()); i++) {
      graphics.text(font, this.lines.get(i), 30, 36 + i * 12, 0xFF222222, false);
    }
  }

}
