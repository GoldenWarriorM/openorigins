package com.openorigins.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;

/** 16x16 texture toggle for the emoji box (same texture adhere as original). */
public class OaEmojiToggle implements Renderable, GuiEventListener, NarratableEntry {
  private final Runnable onPress;
  private final int x;
  private final int y;
  private boolean focused;

  public OaEmojiToggle(int screenHeight, Runnable onPress) {
    this.x = 4;
    this.y = screenHeight - 16 - 15;
    this.onPress = onPress;
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
    gfx.blit(RenderPipelines.GUI_TEXTURED, OaEmojiPanel.TEX, x, y,
        122.0f, 0.0f, 16, 16, OaEmojiPanel.TEX_W, OaEmojiPanel.TEX_H);
  }

  @Override
  public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    if (event.button() != 0 && event.button() != 1) return false;
    double mx = event.x();
    double my = event.y();
    if (mx < x || mx >= x + 16 || my < y || my >= y + 16) return false;
    try {
      net.minecraft.client.gui.components.AbstractWidget.playButtonClickSound(
          Minecraft.getInstance().getSoundManager());
    } catch (Throwable ignored) {
    }
    onPress.run();
    return true;
  }

  @Override
  public boolean isMouseOver(double mx, double my) {
    return mx >= x && mx < x + 16 && my >= y && my < y + 16;
  }

  @Override
  public void setFocused(boolean focused) {
    this.focused = focused;
  }

  @Override
  public boolean isFocused() {
    return focused;
  }

  @Override
  public NarratableEntry.NarrationPriority narrationPriority() {
    return NarratableEntry.NarrationPriority.HOVERED;
  }

  @Override
  public void updateNarration(NarrationElementOutput output) {
  }
}
