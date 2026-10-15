package com.openorigins.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Square pack-icon button with optional player-face mode, label below,
 * left/right click actions.
 */
public class HomeIconButton extends AbstractButton {
  private final Identifier textureId;
  private final int texW;
  private final int texH;
  private final boolean face;
  private final String homeName;
  private final String subLabel;
  private final int subColor;
  private final Runnable leftAction;
  private final Runnable rightAction;

  public HomeIconButton(int x, int y, int size, Component message, Identifier textureId,
      int texW, int texH, boolean face, String homeName, Component tooltip,
      Runnable leftAction, Runnable rightAction) {
    this(x, y, size, message, textureId, texW, texH, face, homeName, null, 0xFFAAAAAA,
        tooltip, leftAction, rightAction);
  }

  public HomeIconButton(int x, int y, int size, Component message, Identifier textureId,
      int texW, int texH, boolean face, String homeName, String subLabel, int subColor,
      Component tooltip, Runnable leftAction, Runnable rightAction) {
    super(x, y, size, size, message);
    this.textureId = textureId;
    this.texW = texW;
    this.texH = texH;
    this.face = face;
    this.homeName = homeName;
    this.subLabel = subLabel;
    this.subColor = subColor;
    this.leftAction = leftAction;
    this.rightAction = rightAction;
    this.setTooltip(Tooltip.create(tooltip));
  }

  @Override
  public void onPress(InputWithModifiers input) {
    this.leftAction.run();
  }

  @Override
  public void onClick(MouseButtonEvent event, boolean doubleClick) {
    if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
      this.rightAction.run();
    } else {
      super.onClick(event, doubleClick);
    }
  }

  @Override
  protected boolean isValidClickButton(MouseButtonInfo buttonInfo) {
    return buttonInfo.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT || buttonInfo.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT;
  }

  @Override
  protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
    this.defaultButtonNarrationText(output);
  }

  @Override
  protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    int dw = this.getWidth();
    int dh = this.getHeight();
    if (this.face) {
      int dx = this.getX();
      int dy = this.getY();
      graphics.blit(RenderPipelines.GUI_TEXTURED, this.textureId,
          dx, dy, 8.0F, 8.0F, dw, dh, 8, 8, 64, 64);
      graphics.blit(RenderPipelines.GUI_TEXTURED, this.textureId,
          dx, dy, 40.0F, 8.0F, dw, dh, 8, 8, 64, 64);
    } else {
      float aspect = this.texW / (float) this.texH;
      if (aspect >= 1.0F) {
        dh = Math.max(1, Math.round(dw / aspect));
      } else {
        dw = Math.max(1, Math.round(dh * aspect));
      }
      int dx = this.getX() + (this.getWidth() - dw) / 2;
      int dy = this.getY() + (this.getHeight() - dh) / 2;
      graphics.blit(RenderPipelines.GUI_TEXTURED, this.textureId,
          dx, dy, 0.0F, 0.0F, dw, dh, this.texW, this.texH, this.texW, this.texH);
    }
    if (this.isHoveredOrFocused()) {
      int x0 = this.getX() - 1;
      int y0 = this.getY() - 1;
      int x1 = this.getX() + this.getWidth() + 1;
      int y1 = this.getY() + this.getHeight() + 1;
      graphics.fill(x0, y0, x1, y0 + 1, 0xFFFFFFFF);
      graphics.fill(x0, y1 - 1, x1, y1, 0xFFFFFFFF);
      graphics.fill(x0, y0, x0 + 1, y1, 0xFFFFFFFF);
      graphics.fill(x1 - 1, y0, x1, y1, 0xFFFFFFFF);
    }
    Minecraft mc = Minecraft.getInstance();
    if (mc != null) {
      String s = this.homeName;
      int maxW = this.getWidth() + 16;
      if (mc.font.width(s) > maxW) {
        s = mc.font.plainSubstrByWidth(s, maxW - 3) + "...";
      }
      int sw = mc.font.width(s);
      graphics.text(mc.font, s, this.getX() + this.getWidth() / 2 - sw / 2,
          this.getY() + this.getHeight() + 2, 0xFFFFFFFF);
      if (this.subLabel != null && !this.subLabel.isEmpty()) {
        int subW = mc.font.width(this.subLabel);
        graphics.text(mc.font, this.subLabel, this.getX() + this.getWidth() / 2 - subW / 2,
            this.getY() + this.getHeight() + 11, this.subColor);
      }
    }
  }
}
