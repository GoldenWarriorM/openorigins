package com.openorigins.client;

import com.openorigins.client.util.BadgerCache;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Top-left HUD with the active badger task requirements. */
public final class BadgerHud implements net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement {
  public static final Identifier ID = Identifier.fromNamespaceAndPath("openorigins", "badger_task");

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
    int x = 4;
    int y = 4;
    graphics.text(font, Component.translatable("hud.openorigins.badger_task",
        BadgerCache.streakScore()), x, y, 0xFFFFFFFF);
    y += 11;
    for (BadgerCache.Required r : req) {
      Identifier id = Identifier.tryParse(r.itemId());
      if (id == null) {
        continue;
      }
      net.minecraft.world.item.Item item =
          net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id);
      ItemStack stack = new ItemStack(item, r.count());
      graphics.item(stack, x, y);
      graphics.itemDecorations(font, stack, x, y);
      int have = countInInventory(mc, r.itemId());
      boolean done = have >= r.count();
      String progress = have + "/" + r.count();
      graphics.text(font, progress, x + 19, y + 5,
          done ? 0xFF55FF55 : 0xFFFFFFFF);
      y += 18;
    }
    if (BadgerCache.daysUntilReset() > 0) {
      graphics.text(font, Component.translatable("hud.openorigins.badger_reset",
          BadgerCache.daysUntilReset()), x, y + 1, 0xFFAAAAAA);
    }
  }

  private static int countInInventory(Minecraft mc, String itemId) {
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
      if (id.equals(itemId)) {
        total += stack.getCount();
      }
    }
    return total;
  }
}
