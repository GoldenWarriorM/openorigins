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

/** Emoji grid for the OriginAddons-style picker (JSON-driven). */
public class OaEmojiPanel implements Renderable, GuiEventListener, NarratableEntry {
  public static final int COLS = 8;
  public static final int ROWS = 4;
  public static final int CELL = 20;
  public static final int HEADER = 14;
  public static final int PAD = 4;

  private final java.util.List<String> ids = OaEmojiStore.orderedIds();
  private final EditBox target;
  private final int x;
  private final int y;
  private final int w;
  private final int h;
  private int page;
  private boolean focused;
  private int hoverIdx = -1;

  public OaEmojiPanel(EditBox target, int x, int y) {
    this.target = target;
    this.x = x;
    this.y = y;
    this.w = COLS * CELL + PAD * 2;
    this.h = HEADER + ROWS * CELL + PAD;
  }

  public static int panelWidth() {
    return COLS * CELL + PAD * 2;
  }

  public static int panelHeight() {
    return HEADER + ROWS * CELL + PAD;
  }

  public int pages() {
    return Math.max(1, (ids.size() + COLS * ROWS - 1) / (COLS * ROWS));
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
    Font font = Minecraft.getInstance().font;
    gfx.fill(x, y, x + w, y + h, 0xE0101010);
    gfx.fill(x, y, x + w, y + 1, 0xFF3A3A3A);
    gfx.fill(x, y + h - 1, x + w, y + h, 0xFF3A3A3A);
    gfx.fill(x, y, x + 1, y + h, 0xFF3A3A3A);
    gfx.fill(x + w - 1, y, x + w, y + h, 0xFF3A3A3A);
    hoverIdx = cellAt(mouseX, mouseY);
    gfx.text(font, "<", x + PAD + 1, y + 2, 0xFFFFFFFF);
    String mid = hoverIdx >= 0 ? ":" + ids.get(hoverIdx) + ":"
        : (page + 1) + "/" + pages();
    gfx.text(font, mid, x + w / 2 - font.width(mid) / 2, y + 2, 0xFFAAAAAA);
    gfx.text(font, ">", x + w - PAD - 1 - font.width(">"), y + 2, 0xFFFFFFFF);
    int perPage = COLS * ROWS;
    for (int i = 0; i < perPage; i++) {
      int idx = page * perPage + i;
      if (idx >= ids.size()) break;
      int cx = x + PAD + (i % COLS) * CELL;
      int cy = y + HEADER + (i / COLS) * CELL;
      if (idx == hoverIdx) gfx.fill(cx, cy, cx + CELL, cy + CELL, 0x44FFFFFF);
      String glyph = OaEmojiStore.displayFor(ids.get(idx));
      String e = glyph != null ? glyph : "?";
      gfx.text(font, e, cx + (CELL - font.width(e)) / 2, cy + (CELL - 9) / 2, 0xFFFFFFFF);
    }
  }

  private int cellAt(double mx, double my) {
    int perPage = COLS * ROWS;
    for (int i = 0; i < perPage; i++) {
      int idx = page * perPage + i;
      if (idx >= ids.size()) break;
      int cx = x + PAD + (i % COLS) * CELL;
      int cy = y + HEADER + (i / COLS) * CELL;
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
    if (my < y + HEADER) {
      if (mx < x + w / 2 && page > 0) page--;
      else if (mx >= x + w / 2 && page + 1 < pages()) page++;
      return true;
    }
    int idx = cellAt(mx, my);
    if (idx >= 0) {
      target.insertText(":" + ids.get(idx) + ":");
      target.setFocused(true);
      return true;
    }
    return true;
  }

  @Override
  public boolean isMouseOver(double mx, double my) {
    return mx >= x && mx < x + w && my >= y && my < y + h;
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
