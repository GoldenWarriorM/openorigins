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
 * Clickable emoji grid shown above the chat input. Own widget, no relation
 * to any third-party picker.
 */
public class EmojiPanel implements Renderable, GuiEventListener, NarratableEntry {
  public static final int COLS = 8;
  public static final int ROWS = 4;
  public static final int CELL = 18;
  public static final int HEADER = 16;
  public static final int PAD = 4;

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
    this.w = COLS * CELL + PAD * 2;
    this.h = HEADER + ROWS * CELL + PAD * 2;
  }

  public int pages() {
    return Math.max(1, (codes.size() + COLS * ROWS - 1) / (COLS * ROWS));
  }

  private static String label(String code) {
    String glyph = EmojiCodes.charFor(code);
    return glyph != null ? glyph : ":" + code + ":";
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
    Font font = Minecraft.getInstance().font;
    gfx.fill(x, y, x + w, y + h, 0xE0101010);
    gfx.fill(x, y, x + w, y + 1, 0xFF3A3A3A);
    gfx.fill(x, y + h - 1, x + w, y + h, 0xFF3A3A3A);
    gfx.fill(x, y, x + 1, y + h, 0xFF3A3A3A);
    gfx.fill(x + w - 1, y, x + w, y + h, 0xFF3A3A3A);
    String label = page > 0 ? "<" : " ";
    gfx.text(font, label, x + PAD + 2, y + PAD + 2, 0xFFFFFFFF);
    String count = (page + 1) + "/" + pages();
    gfx.text(font, count, x + w / 2 - font.width(count) / 2, y + PAD + 2, 0xFFAAAAAA);
    String right = page + 1 < pages() ? ">" : " ";
    gfx.text(font, right, x + w - PAD - 2 - font.width(right), y + PAD + 2, 0xFFFFFFFF);
    int perPage = COLS * ROWS;
    for (int i = 0; i < perPage; i++) {
      int idx = page * perPage + i;
      if (idx >= codes.size()) break;
      int cx = x + PAD + (i % COLS) * CELL;
      int cy = y + PAD + HEADER + (i / COLS) * CELL;
      boolean hover = mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL;
      if (hover) gfx.fill(cx, cy, cx + CELL, cy + CELL, 0x44FFFFFF);
      String e = label(codes.get(idx));
      gfx.text(font, e, cx + (CELL - font.width(e)) / 2, cy + (CELL - 8) / 2, 0xFFFFFFFF);
    }
  }

  private int cellAt(double mx, double my) {
    int perPage = COLS * ROWS;
    for (int i = 0; i < perPage; i++) {
      int idx = page * perPage + i;
      if (idx >= codes.size()) break;
      int cx = x + PAD + (i % COLS) * CELL;
      int cy = y + PAD + HEADER + (i / COLS) * CELL;
      if (mx >= cx && mx < cx + CELL && my >= cy && my < cy + CELL) return idx;
    }
    return -1;
  }

  @Override
  public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    if (event.button() != 0) return false;
    double mx = event.x();
    double my = event.y();
    if (mx < x || mx >= x + w || my < y || my >= y + h) return false;
    if (my < y + PAD + HEADER) {
      if (mx < x + w / 2 && page > 0) page--;
      else if (mx >= x + w / 2 && page + 1 < pages()) page++;
      return true;
    }
    int idx = cellAt(mx, my);
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
