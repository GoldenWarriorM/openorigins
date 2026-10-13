package com.openorigins.client.screen;

import com.openorigins.client.util.OaEmojiStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * 1:1 port of OriginAddons' emoji picker box (MIT): same texture, positions,
 * flow layout, sounds and tooltip. Reimplemented for the 26.2 pipeline.
 */
public class OaEmojiPanel implements Renderable, GuiEventListener, NarratableEntry {
  public static final Identifier TEX =
      Identifier.fromNamespaceAndPath("originaddons", "textures/gui/emojipicker/emojipicker.png");
  public static final int TEX_W = 158;
  public static final int TEX_H = 75;
  public static final int BOX_W = 122;
  public static final int BOX_H = 75;
  public static final int SCROLL_X = 7;
  public static final int SCROLL_Y = 8;
  public static final int SCROLL_W = 108;
  public static final int SCROLL_H = 61;
  public static final int UNITS_PER_ROW = 9;
  public static final int UNIT = 12;

  private java.util.List<String> ids = java.util.List.of();
  private final EditBox target;
  private final int x;
  private final int y;
  private int scroll;
  private boolean focused;
  private int hoverIdx = -1;

  public OaEmojiPanel(EditBox target, int screenHeight) {
    this.target = target;
    this.x = 4;
    this.y = screenHeight - BOX_H - 34;
    refresh();
  }

  private void refresh() {
    ids = OaEmojiStore.orderedIds();
    scroll = Math.max(0, Math.min(maxScroll(), scroll));
  }

  private int contentHeight() {
    int units = 0;
    for (String id : ids) units += 1;
    int rows = Math.max(1, (units + UNITS_PER_ROW - 1) / UNITS_PER_ROW);
    return rows * UNIT;
  }

  private int maxScroll() {
    return Math.max(0, contentHeight() - SCROLL_H);
  }

  public void scrollBy(double amount) {
    scroll = Math.max(0, Math.min(maxScroll(), scroll + (amount > 0 ? -12 : 12)));
  }

  private int cellIndexAt(double mx, double my) {
    int sy = y + SCROLL_Y - scroll;
    int units = 0;
    int row = 0;
    int col = 0;
    for (int i = 0; i < ids.size(); i++) {
      int cx = x + SCROLL_X + col * UNIT;
      int cy = sy + row * UNIT;
      if (mx >= cx && mx < cx + UNIT && my >= cy && my < cy + UNIT) return i;
      col += 1;
      units += 1;
      if (col >= UNITS_PER_ROW) {
        col = 0;
        row++;
      }
    }
    return -1;
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
    refresh();
    Font font = Minecraft.getInstance().font;
    gfx.blit(RenderPipelines.GUI_TEXTURED, TEX, x, y, 0.0f, 0.0f, BOX_W, BOX_H, TEX_W, TEX_H);
    hoverIdx = -1;
    if (isMouseOver(mouseX, mouseY)) hoverIdx = cellIndexAt(mouseX, mouseY);
    gfx.enableScissor(x + SCROLL_X, y + SCROLL_Y, x + SCROLL_X + SCROLL_W, y + SCROLL_Y + SCROLL_H);
    int sy = y + SCROLL_Y - scroll;
    int col = 0;
    int row = 0;
    for (int i = 0; i < ids.size(); i++) {
      int cx = x + SCROLL_X + col * UNIT;
      int cy = sy + row * UNIT;
      if (cy + UNIT >= y + SCROLL_Y && cy < y + SCROLL_Y + SCROLL_H) {
        float v = (i == hoverIdx) ? 44.0f : 32.0f;
        gfx.blit(RenderPipelines.GUI_TEXTURED, TEX, cx, cy, 122.0f, v, UNIT, UNIT, TEX_W, TEX_H);
        String glyph = OaEmojiStore.displayFor(ids.get(i));
        String e = glyph != null ? glyph : "?";
        gfx.text(font, e, cx + 1, cy + 2, 0xFFFFFFFF);
      }
      col++;
      if (col >= UNITS_PER_ROW) {
        col = 0;
        row++;
      }
    }
    gfx.disableScissor();
    if (hoverIdx >= 0) {
      gfx.setTooltipForNextFrame(
          Component.literal(":" + ids.get(hoverIdx) + ":").withStyle(s -> s.withColor(0xFF808080)),
          mouseX, mouseY);
    }
  }

  @Override
  public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    if (event.button() != 0 && event.button() != 1) return false;
    refresh();
    double mx = event.x();
    double my = event.y();
    if (mx < x || mx >= x + BOX_W || my < y || my >= y + BOX_H) return false;
    try {
      net.minecraft.client.gui.components.AbstractWidget.playButtonClickSound(
          Minecraft.getInstance().getSoundManager());
    } catch (Throwable ignored) {
    }
    int idx = cellIndexAt(mx, my);
    if (idx >= 0) {
      String token = ":" + ids.get(idx) + ":";
      int cursor = target.getCursorPosition();
      String text = target.getValue();
      String before = text.substring(0, Math.min(cursor, text.length()));
      String after = text.substring(Math.min(cursor, text.length()));
      if (!before.isEmpty() && !before.endsWith(" ")) {
        before = before + " ";
        cursor++;
      }
      if (!after.startsWith(" ")) {
        after = " " + after;
        cursor++;
      }
      target.setValue(before + token + after);
      target.setCursorPosition(cursor + token.length());
      target.setFocused(true);
      return true;
    }
    return true;
  }

  @Override
  public boolean isMouseOver(double mx, double my) {
    return mx >= x && mx < x + BOX_W && my >= y && my < y + BOX_H;
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
