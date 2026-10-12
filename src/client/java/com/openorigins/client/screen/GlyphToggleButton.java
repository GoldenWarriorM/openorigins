package com.openorigins.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Borderless +/- glyph used to expand/collapse dependent subgroups.
 * Just the glyph itself is the button; no backing texture.
 */
public class GlyphToggleButton extends AbstractWidget {
  private final Runnable onPressAction;

  public GlyphToggleButton(Runnable onPressAction, Component tooltip) {
    super(0, 0, 16, 20, Component.empty());
    this.onPressAction = onPressAction;
    if (tooltip != null) {
      this.setTooltip(Tooltip.create(tooltip));
    }
  }

  @Override
  public void onClick(MouseButtonEvent event, boolean doubleClick) {
    try {
      playButtonClickSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());
    } catch (Throwable ignored) {
    }
    if (this.onPressAction != null && this.active) {
      this.onPressAction.run();
    }
  }

  @Override
  protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
      float partialTick) {
    int color = !this.active ? 0xFFA0A0A0
        : this.isHovered() ? 0xFFFFFF55 : 0xFFFFFFFF;
    net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
    String s = this.getMessage().getString();
    int w = font.width(s);
    graphics.text(font, s, this.getX() + (this.getWidth() - w) / 2,
        this.getY() + (this.getHeight() - 9) / 2, color);
  }

  @Override
  protected void updateWidgetNarration(NarrationElementOutput output) {
    output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, this.getMessage());
  }
}
