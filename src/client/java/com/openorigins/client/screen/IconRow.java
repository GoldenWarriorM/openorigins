package com.openorigins.client.screen;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

/**
 * Keeps a horizontal icon row centered after we append our button.
 * Only the contiguous 20x20 cluster containing our button is translated as a
 * whole, so relative order and gaps (including other mods' buttons) are kept.
 */
public final class IconRow {
  private IconRow() {
  }

  public static void addCentered(Screen screen, AbstractWidget ours) {
    try {
      java.util.List<AbstractWidget> row = new java.util.ArrayList<>();
      for (GuiEventListener child : screen.children()) {
        if (child instanceof AbstractWidget widget
            && widget.getWidth() == 20 && widget.getHeight() == 20
            && Math.abs(widget.getY() - ours.getY()) <= 2) {
          row.add(widget);
        }
      }
      if (!row.contains(ours) || row.size() < 2) {
        return;
      }
      row.sort(java.util.Comparator.comparingInt(AbstractWidget::getX));
      int self = row.indexOf(ours);
      int lo = self;
      while (lo > 0
          && row.get(lo).getX() - (row.get(lo - 1).getX() + 20) <= 12) {
        lo--;
      }
      int hi = self;
      while (hi < row.size() - 1
          && row.get(hi + 1).getX() - (row.get(hi).getX() + 20) <= 12) {
        hi++;
      }
      if (hi <= lo) {
        return;
      }
      int minX = row.get(lo).getX();
      int maxR = row.get(hi).getX() + 20;
      int dx = screen.width / 2 - (minX + maxR) / 2;
      if (dx == 0) {
        return;
      }
      for (int i = lo; i <= hi; i++) {
        row.get(i).setX(row.get(i).getX() + dx);
      }
    } catch (Throwable ignored) {
    }
  }
}
