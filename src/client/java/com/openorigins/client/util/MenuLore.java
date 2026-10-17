package com.openorigins.client.util;

import net.minecraft.world.item.ItemStack;

/**
 * Shared lore-scan primitive: iterates menu slots with content outside the
 * player inventory. Five capture paths (homes, worlds, badger, cosmetics,
 * price database) re-implemented this skip logic; menu lore-format changes
 * used to need five edits.
 */
public final class MenuLore {
  private MenuLore() {}

  /** Calls back for every inventory-external slot that currently holds an
   *  item; index is the menu slot index (top slot list order). */
  public static void forEachTop(net.minecraft.world.inventory.AbstractContainerMenu menu,
      java.util.function.BiConsumer<Integer, ItemStack> visitor) {
    if (menu == null) {
      return;
    }
    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
    Object playerInv = (mc != null && mc.player != null) ? mc.player.getInventory() : null;
    for (int i = 0; i < menu.slots.size(); i++) {
      net.minecraft.world.inventory.Slot slot = menu.slots.get(i);
      if (!slot.hasItem()) {
        continue;
      }
      if (playerInv != null && slot.container == playerInv) {
        continue;
      }
      visitor.accept(i, slot.getItem());
    }
  }
}
