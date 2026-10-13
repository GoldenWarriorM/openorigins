package com.openorigins.client.screen;

import com.openorigins.client.util.EmojiCodes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Clickable emoji list shown above the chat input. Rows show the glyph once
 * learned, otherwise the :code:. Own widget, no third-party code.
 */
public class EmojiPanel implements Renderable, GuiEventListener, NarratableEntry {
  public static final int ROWS = 8;
  public static final int ROW_H = 12;
  public static final int HEADER = 14;
  public static final int PAD = 4;
  public static final int W = 150;

  private final java.util.List<String> codes = EmojiCodes.codes();
  private final EditBox target;
  private final int x;
  private final int y;
  private final int w;
  private final int h;
  private int page;
  private boolean focused;

  public EmojiPanel(EditBox target, int x, int y) {
    this.target = target;
    this.x = x;
    this.y = y;
    this.w = W;
    this.h = HEADER + ROWS * ROW_H + PAD;
  }

  public static int panelWidth() {
    return W;
  }

  public static int panelHeight() {
    return HEADER + ROWS * ROW_H + PAD;
  }

  public int pages() {
    return Math.max(1, (codes.size() + ROWS - 1) / ROWS);
  }

  private static String label(String code) {
    String glyph = EmojiCodes.charFor(code);
    if (glyph != null) return glyph + "  :" + code + ":";
    return ":" + code + ":";
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
    Font font = Minecraft.getInstance().font;
    gfx.fill(x, y, x + w, y + h, 0xE0101010);
    gfx.fill(x, y, x + w, y + 1, 0xFF3A3A3A);
    gfx.fill(x, y + h - 1, x + w, y + h, 0xFF3A3A3A);
    gfx.fill(x, y, x + 1, y + h, 0xFF3A3A3A);
    gfx.fill(x + w - 1, y, x + w, y + h, 0xFF3A3A3A);
    gfx.text(font, "<", x + PAD + 1, y + 2, 0xFFFFFFFF);
    String count = (page + 1) + "/" + pages();
    gfx.text(font, count, x + w / 2 - font.width(count) / 2, y + 2, 0xFFAAAAAA);
    gfx.text(font, ">", x + w - PAD - 1 - font.width(">"), y + 2, 0xFFFFFFFF);
    for (int i = 0; i < ROWS; i++) {
      int idx = page * ROWS + i;
      if (idx >= codes.size()) break;
      int ry = y + HEADER + i * ROW_H;
      boolean hover = mouseX >= x && mouseX < x + w && mouseY >= ry && mouseY < ry + ROW_H;
      if (hover) gfx.fill(x + 1, ry, x + w - 1, ry + ROW_H, 0x44FFFFFF);
      gfx.text(font, label(codes.get(idx)), x + PAD + 2, ry + 2, 0xFFFFFFFF);
    }
  }

  private int rowAt(double mx, double my) {
    if (mx < x || mx >= x + w || my < y + HEADER || my >= y + HEADER + ROWS * ROW_H) return -1;
    int row = (int) ((my - y - HEADER) / ROW_H);
    int idx = page * ROWS + row;
    return idx < codes.size() ? idx : -1;
  }

  @Override
  public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    if (event.button() != 0) return false;
    double mx = event.x();
    double my = event.y();
    if (mx < x || mx >= x + w || my < y || my >= y + h) return false;
    if (my < y + HEADER) {
      if (mx < x + w / 2 && page > 0) page--;
      else if (mx >= x + w / 2 && page + 1 < pages()) page++;
      return true;
    }
    int idx = rowAt(mx, my);
    if (idx >= 0) {
      target.insertText(":" + codes.get(idx) + ":");
      target.setFocused(true);
      return true;
    }
    return true;
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
