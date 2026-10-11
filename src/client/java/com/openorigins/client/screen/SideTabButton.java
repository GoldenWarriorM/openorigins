package com.openorigins.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Flat sidebar tab row: label plus a gold accent bar when selected. */
public class SideTabButton extends AbstractWidget {
  private final Runnable onPressAction;
  private boolean selected;

  public SideTabButton(int x, int y, int width, int height, Component label, Runnable onPressAction) {
    super(x, y, width, height, label);
    this.onPressAction = onPressAction;
  }

  public void setSelected(boolean v) {
    this.selected = v;
  }

  @Override
  public void onClick(MouseButtonEvent event, boolean doubleClick) {
    try {
      playButtonClickSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());
    } catch (Throwable ignored) {
    }
    if (this.onPressAction != null) {
      this.onPressAction.run();
    }
  }

  @Override
  protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
      float partialTick) {
    boolean hovered = this.isHovered();
    if (this.selected) {
      graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height,
          0x30FFFFFF);
      graphics.fill(this.getX(), this.getY() + 3, this.getX() + 2, this.getY() + this.height - 3,
          0xFFFFAA00);
    } else {
      graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height,
          hovered ? 0x18FFFFFF : 0x20000000);
    }
    net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
    graphics.text(font, this.getMessage().getString(), this.getX() + 10,
        this.getY() + (this.height - 9) / 2,
        this.selected ? 0xFFFFFFFF : 0xFFCCCCCC);
  }

  @Override
  protected void updateWidgetNarration(NarrationElementOutput output) {
    output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, this.getMessage());
  }
}
