package com.openorigins.client;

import com.openorigins.client.mixin.BossBarAccessor;
import com.openorigins.client.util.BadgerCache;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Corner-anchored HUD with the active badger task requirements. */
public final class BadgerHud implements net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement {
  public static final Identifier ID = Identifier.fromNamespaceAndPath("openorigins", "badger_task");

  public record Row(ItemStack icon, String text, int color) {}

  /** Placeholder layout for the position editor when no task data exists. */
  public static List<Row> sampleRows() {
    List<Row> rows = new ArrayList<>();
    rows.add(new Row(null, "Badger Task (streak 13)", 0xFFFFFFFF));
    rows.add(new Row(null, "Red Mushroom 0/2", 0xFFFFFFFF));
    rows.add(new Row(null, "Coal 0/10", 0xFFFFFFFF));
    rows.add(new Row(null, "Lapis Lazuli 0/6", 0xFFFFFFFF));
    rows.add(new Row(null, "Basic: (2/5)", 0xFFFFFFFF));
    rows.add(new Row(null, "Time left: 7h 56m", 0xFFAAAAAA));
    rows.add(new Row(null, "Streak resets in 6d", 0xFFAAAAAA));
    return rows;
  }

  /** True while the position editor is open (live HUD stays out of the way). */
  public static boolean previewActive;

  /** HUD is rebuilt at most 4×/s; buildRows allocates instead of per-frame. */
  private static List<Row> cachedRows = List.of();
  private static long cachedRowsAt;

  public static List<Row> buildRows(Minecraft mc, Font font) {
    long now = System.currentTimeMillis();
    if (now - cachedRowsAt < 250) {
      return cachedRows;
    }
    List<Row> rows = buildRows0(mc, font);
    cachedRowsAt = now;
    cachedRows = rows;
    return rows;
  }

  private static List<Row> buildRows0(Minecraft mc, Font font) {
    List<Row> rows = new ArrayList<>();
    List<BadgerCache.Required> req = BadgerCache.required();
    if (req.isEmpty()) {
      return rows;
    }
    rows.add(new Row(null, Component.translatable("hud.openorigins.badger_task",
        BadgerCache.streakScore()).getString(), 0xFFFFFFFF));
    for (BadgerCache.Required r : req) {
      ItemStack stack = BadgerCache.iconFor(r);
      if (stack.isEmpty()) {
        continue;
      }
      int have = countInInventory(mc, r);
      boolean done = have >= r.count();
      String labelName = r.displayName().isEmpty() ? stack.getHoverName().getString() : r.displayName();
      rows.add(new Row(stack, labelName + " " + have + "/" + r.count(),
          done ? 0xFF84BA4A : 0xFFFFFFFF));
    }
    // Quest stage the player is on, e.g. "Basic: (2/5)" — matches the menu.
    for (BadgerCache.Tier t : BadgerCache.tiers()) {
      rows.add(new Row(null, t.name() + ": (" + t.done() + "/" + t.total() + ")",
          t.done() >= t.total() ? 0xFF84BA4A : 0xFFFFFFFF));
    }
    if (BadgerCache.daysUntilReset() > 0) {
      rows.add(new Row(null, Component.translatable("hud.openorigins.badger_reset",
          BadgerCache.daysUntilReset()).getString(), 0xFFAAAAAA));
    }
    if (!BadgerCache.taskTimeLeft().isEmpty()) {
      rows.add(rows.size() - (BadgerCache.daysUntilReset() > 0 ? 1 : 0),
          new Row(null, Component.translatable("hud.openorigins.badger_timeleft",
              com.openorigins.client.util.OpenWorldCache.shortDuration(
                  BadgerCache.taskTimeLeft())).getString(), 0xFFAAAAAA));
    }
    return rows;
  }

  public static int maxWidth(Font font, List<Row> rows) {
    int maxW = 0;
    for (Row row : rows) {
      int w = (row.icon() != null ? 19 : 0) + font.width(row.text());
      if (w > maxW) {
        maxW = w;
      }
    }
    return maxW;
  }

  public static int totalHeight(List<Row> rows) {
    if (rows.isEmpty()) {
      return 0;
    }
    int totalH = 11;
    for (int i = 1; i < rows.size(); i++) {
      totalH += rows.get(i).icon() != null ? 20 : 12;
    }
    return totalH;
  }

  /** Box top-left for an anchor corner, offsets and content size. */
  public static int[] boxAt(OpenOriginsConfig.HudCorner corner, int dx, int dy, int maxW,
      int totalH, int sw, int sh) {
    int x = switch (corner) {
      case TOP_RIGHT, BOTTOM_RIGHT -> sw - dx - maxW;
      default -> dx;
    };
    int y = switch (corner) {
      case BOTTOM_LEFT, BOTTOM_RIGHT -> sh - dy - totalH;
      default -> dy;
    };
    return new int[]{x, y};
  }

  public static void drawBox(GuiGraphicsExtractor graphics, Font font, List<Row> rows, int x,
      int y, boolean backdrop) {
    drawBox(graphics, font, rows, x, y, backdrop, maxWidth(font, rows), totalHeight(rows));
  }

  public static void drawBox(GuiGraphicsExtractor graphics, Font font, List<Row> rows, int x,
      int y, boolean backdrop, int maxW, int totalH) {
    if (backdrop) {
      int pad = 3;
      int cut = 1;
      int bx0 = x - pad;
      int by0 = y - pad;
      int bx1 = x + maxW + pad;
      int by1 = y + totalH + pad;
      int bg = 0x80000000;
      graphics.fill(bx0 + cut, by0, bx1 - cut, by1, bg);
      graphics.fill(bx0, by0 + cut, bx0 + cut, by1 - cut, bg);
      graphics.fill(bx1 - cut, by0 + cut, bx1, by1 - cut, bg);
    }
    graphics.text(font, rows.get(0).text(), x + (rows.get(0).icon() != null ? 19 : 0), y,
        rows.get(0).color());
    y += 11;
    for (int i = 1; i < rows.size(); i++) {
      Row row = rows.get(i);
      if (row.icon() != null) {
        graphics.item(row.icon(), x, y);
        graphics.itemDecorations(font, row.icon(), x, y);
        graphics.text(font, row.text(), x + 19, y + 5, row.color());
      } else {
        graphics.text(font, row.text(), x, y + 1, row.color());
      }
      y += row.icon() != null ? 20 : 12;
    }
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
    Minecraft mc = Minecraft.getInstance();
    if (mc == null || !OpenOriginsConfig.getInstance().isEnabled(OriginFeature.BADGER_TASK_HUD)
        || !OpenOriginsConfig.getInstance().isBadgerHudVisible()) {
      return;
    }
    if (previewActive || mc.gui.hud.isHidden() || mc.player == null) {
      return;
    }
    Font font = mc.font;
    List<Row> rows = buildRows(mc, font);
    if (rows.isEmpty()) {
      return;
    }
    int maxW = maxWidth(font, rows);
    int totalH = totalHeight(rows);
    int sw = mc.getWindow().getGuiScaledWidth();
    int sh = mc.getWindow().getGuiScaledHeight();
    OpenOriginsConfig.HudCorner corner = OpenOriginsConfig.getInstance().getBadgerHudCorner();
    int dx = OpenOriginsConfig.getInstance().getBadgerHudDX();
    int dy = OpenOriginsConfig.getInstance().getBadgerHudDY();
    int[] box = boxAt(corner, dx, dy, maxW, totalH, sw, sh);
    int x = box[0];
    int y = box[1];
    if ((corner == OpenOriginsConfig.HudCorner.TOP_LEFT
        || corner == OpenOriginsConfig.HudCorner.TOP_RIGHT) && dx + maxW > sw / 2 - 91) {
      try {
        int bars = ((BossBarAccessor) mc.gui.hud.getBossOverlay()).openorigins$getBossEvents()
            .size();
        if (bars > 0) y = 14 + bars * 19;
      } catch (Throwable ignored) {
      }
    }
    drawBox(graphics, font, rows, x, y, OpenOriginsConfig.getInstance().isBadgerHudBackdrop(),
        maxW, totalH);
  }

  private static int countInInventory(Minecraft mc, BadgerCache.Required r) {
    if (mc.player == null) {
      return 0;
    }
    int total = 0;
    net.minecraft.world.entity.player.Inventory inv = mc.player.getInventory();
    for (int i = 0; i < inv.getContainerSize(); i++) {
      ItemStack stack = inv.getItem(i);
      if (stack.isEmpty()) {
        continue;
      }
      String id = net.minecraft.core.registries.BuiltInRegistries.ITEM
          .getKey(stack.getItem()).toString();
      if (!id.equals(r.itemId())) {
        continue;
      }
      if (!r.displayName().isEmpty()
          && !stack.getHoverName().getString().equals(r.displayName())) {
        continue;
      }
      total += stack.getCount();
    }
    return total;
  }
}
