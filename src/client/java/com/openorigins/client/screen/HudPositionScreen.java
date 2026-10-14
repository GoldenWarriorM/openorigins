package com.openorigins.client.screen;

import com.openorigins.client.BadgerHud;
import com.openorigins.client.OpenOriginsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import java.util.List;

/**
 * Mouse-only task-HUD placement: drag the live preview, the nearest corner
 * becomes the anchor (dot on the edge, dot on the box, line between).
 */
public class HudPositionScreen extends Screen {
  private final Screen parent;
  private int boxX;
  private int boxY;
  private int boxW;
  private int boxH;
  private boolean dragging;
  private int dragDX;
  private int dragDY;
  private OpenOriginsConfig.HudCorner anchor;
  private Button doneButton;
  private Button resetButton;

  public HudPositionScreen(Screen parent) {
    super(Component.translatable("config.openorigins.hud_position"));
    this.parent = parent;
  }

  @Override
  protected void init() {
    BadgerHud.previewActive = true;
    this.rebuildBox();
    this.doneButton = Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
        .bounds(this.width / 2 - 155, this.height - 28, 150, 20).build();
    this.resetButton = Button.builder(Component.translatable("config.openorigins.reset_row"), b -> {
      OpenOriginsConfig cfg = OpenOriginsConfig.getInstance();
      cfg.setBadgerHudCorner(OpenOriginsConfig.HudCorner.TOP_LEFT);
      cfg.setBadgerHudOffset(4, 4);
      this.rebuildBox();
    }).bounds(this.width / 2 + 5, this.height - 28, 150, 20).build();
    this.addRenderableWidget(this.doneButton);
    this.addRenderableWidget(this.resetButton);
  }

  @Override
  public void onClose() {
    BadgerHud.previewActive = false;
    OpenOriginsConfig.getInstance().save();
    if (this.minecraft != null) {
      if (this.parent instanceof ConfigScreen cfg) {
        cfg.refreshLists();
      }
      this.minecraft.gui.setScreen(this.parent);
    }
  }

  @Override
  public void removed() {
    BadgerHud.previewActive = false;
  }

  private void rebuildBox() {
    Minecraft mc = Minecraft.getInstance();
    if (mc == null) return;
    OpenOriginsConfig cfg = OpenOriginsConfig.getInstance();
    List<BadgerHud.Row> rows;
    try {
      rows = BadgerHud.buildRows(mc, mc.font);
    } catch (Throwable t) {
      rows = List.of();
    }
    if (rows.isEmpty()) {
      boxW = 120;
      boxH = 40;
    } else {
      boxW = BadgerHud.maxWidth(mc.font, rows);
      boxH = BadgerHud.totalHeight(rows);
    }
    anchor = cfg.getBadgerHudCorner();
    int sw = mc.getWindow().getGuiScaledWidth();
    int sh = mc.getWindow().getGuiScaledHeight();
    int[] box = BadgerHud.boxAt(anchor, cfg.getBadgerHudDX(), cfg.getBadgerHudDY(), boxW, boxH,
        sw, sh);
    boxX = box[0];
    boxY = box[1];
  }

  private int[] anchorDot(int sw, int sh) {
    return switch (anchor) {
      case TOP_RIGHT -> new int[]{sw - 3, 3};
      case BOTTOM_LEFT -> new int[]{3, sh - 3};
      case BOTTOM_RIGHT -> new int[]{sw - 3, sh - 3};
      default -> new int[]{3, 3};
    };
  }

  private int[] boxDot() {
    return switch (anchor) {
      case TOP_RIGHT -> new int[]{boxX + boxW, boxY};
      case BOTTOM_LEFT -> new int[]{boxX, boxY + boxH};
      case BOTTOM_RIGHT -> new int[]{boxX + boxW, boxY + boxH};
      default -> new int[]{boxX, boxY};
    };
  }

  private void applyDrag(int mx, int my) {
    Minecraft mc = Minecraft.getInstance();
    if (mc == null) return;
    int sw = mc.getWindow().getGuiScaledWidth();
    int sh = mc.getWindow().getGuiScaledHeight();
    int nx = Math.max(0, Math.min(sw - boxW, mx - dragDX));
    int ny = Math.max(0, Math.min(sh - boxH, my - dragDY));
    int cx = nx + boxW / 2;
    int cy = ny + boxH / 2;
    OpenOriginsConfig.HudCorner best = OpenOriginsConfig.HudCorner.TOP_LEFT;
    int bestD = Integer.MAX_VALUE;
    int[][] corners = {{0, 0}, {sw, 0}, {0, sh}, {sw, sh}};
    OpenOriginsConfig.HudCorner[] kinds = {
        OpenOriginsConfig.HudCorner.TOP_LEFT, OpenOriginsConfig.HudCorner.TOP_RIGHT,
        OpenOriginsConfig.HudCorner.BOTTOM_LEFT, OpenOriginsConfig.HudCorner.BOTTOM_RIGHT};
    for (int i = 0; i < 4; i++) {
      int dx = cx - corners[i][0];
      int dy = cy - corners[i][1];
      int d = dx * dx + dy * dy;
      if (d < bestD) {
        bestD = d;
        best = kinds[i];
      }
    }
    anchor = best;
    int ox = switch (best) {
      case TOP_RIGHT, BOTTOM_RIGHT -> sw - (nx + boxW);
      default -> nx;
    };
    int oy = switch (best) {
      case BOTTOM_LEFT, BOTTOM_RIGHT -> sh - (ny + boxH);
      default -> ny;
    };
    boxX = nx;
    boxY = ny;
    OpenOriginsConfig cfg = OpenOriginsConfig.getInstance();
    cfg.setBadgerHudCorner(best);
    cfg.setBadgerHudOffset(ox, oy);
  }

  @Override
  public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    if (event.button() == 0 && event.x() >= boxX && event.x() < boxX + boxW
        && event.y() >= boxY && event.y() < boxY + boxH) {
      dragging = true;
      dragDX = (int) Math.round(event.x()) - boxX;
      dragDY = (int) Math.round(event.y()) - boxY;
      return true;
    }
    return super.mouseClicked(event, doubleClick);
  }

  @Override
  public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
    if (dragging) {
      applyDrag((int) Math.round(event.x()), (int) Math.round(event.y()));
      return true;
    }
    return super.mouseDragged(event, dragX, dragY);
  }

  @Override
  public boolean mouseReleased(MouseButtonEvent event) {
    dragging = false;
    return super.mouseReleased(event);
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
      float partialTick) {
    Minecraft mc = Minecraft.getInstance();
    if (mc != null) {
      Font font = mc.font;
      List<BadgerHud.Row> rows;
      try {
        rows = BadgerHud.buildRows(mc, font);
      } catch (Throwable t) {
        rows = List.of();
      }
      if (!rows.isEmpty()) {
        boxW = BadgerHud.maxWidth(font, rows);
        boxH = BadgerHud.totalHeight(rows);
        BadgerHud.drawBox(graphics, font, rows, boxX, boxY,
            OpenOriginsConfig.getInstance().isBadgerHudBackdrop());
      }
      int sw = mc.getWindow().getGuiScaledWidth();
      int sh = mc.getWindow().getGuiScaledHeight();
      int[] a = anchorDot(sw, sh);
      int[] p = boxDot();
      drawDot(graphics, a[0], a[1], 0xFFFFAA00);
      drawDot(graphics, p[0], p[1], 0xFFFFFFFF);
      drawLine(graphics, a[0], a[1], p[0], p[1]);
    }
    super.extractRenderState(graphics, mouseX, mouseY, partialTick);
  }

  private static void drawDot(GuiGraphicsExtractor graphics, int x, int y, int color) {
    graphics.fill(x - 2, y - 2, x + 3, y + 3, color);
  }

  private static void drawLine(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
    int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
    if (steps == 0) return;
    for (int i = 0; i <= steps; i += 2) {
      int x = x0 + (x1 - x0) * i / steps;
      int y = y0 + (y1 - y0) * i / steps;
      graphics.fill(x, y, x + 1, y + 1, 0x80FFFFFF);
    }
  }
}
