package com.openorigins.client;

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

  private record Row(ItemStack icon, String text, int color) {}

  @Override
  public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
    Minecraft mc = Minecraft.getInstance();
    if (mc == null || !OpenOriginsConfig.getInstance().isEnabled(OriginFeature.BADGER_TASK_HUD)) {
      return;
    }
    if (mc.gui.hud.isHidden() || mc.player == null) {
      return;
    }
    List<BadgerCache.Required> req = BadgerCache.required();
    if (req.isEmpty()) {
      return;
    }
    Font font = mc.font;
    List<Row> rows = new ArrayList<>();
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
          done ? 0xFF55FF55 : 0xFFFFFFFF));
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
    int maxW = 0;
    for (Row row : rows) {
      int w = (row.icon() != null ? 19 : 0) + font.width(row.text());
      if (w > maxW) {
        maxW = w;
      }
    }
    int totalH = 11;
    for (int i = 1; i < rows.size(); i++) {
      totalH += rows.get(i).icon() != null ? 20 : 12;
    }
    totalH += 2;
    int sw = mc.getWindow().getGuiScaledWidth();
    int sh = mc.getWindow().getGuiScaledHeight();
    OpenOriginsConfig.HudCorner corner = OpenOriginsConfig.getInstance().getBadgerHudCorner();
    int x = switch (corner) {
      case TOP_RIGHT, BOTTOM_RIGHT -> sw - 4 - maxW;
      default -> 4;
    };
    int y = switch (corner) {
      case BOTTOM_LEFT, BOTTOM_RIGHT -> sh - 4 - totalH;
      default -> 4;
    };
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
