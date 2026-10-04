package com.openorigins.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** On-demand full container dump for diagnostics. */
public final class ContainerDump {
  private ContainerDump() {}

  private static final Logger LOG = LoggerFactory.getLogger("openorigins");

  public static void dump(Minecraft client) {
    if (client == null || !(client.gui.screen() instanceof AbstractContainerScreen<?> container)
        || client.player == null) {
      return;
    }
    Object inv = client.player.getInventory();
    int idx = 0;
    for (Slot slot : container.getMenu().slots) {
      String tag = (inv != null && slot.container == inv) ? "[inv]" : "[top]";
      if (!slot.hasItem()) {
        idx++;
        continue;
      }
      ItemStack stack = slot.getItem();
      StringBuilder lb = new StringBuilder();
      ItemLore lore = stack.get(DataComponents.LORE);
      if (lore != null) {
        for (Component line : lore.lines()) {
          if (lb.length() > 0) {
            lb.append(" // ");
          }
          lb.append(line.getString());
          if (lb.length() > 600) {
            break;
          }
        }
      }
      LOG.info("[menu-slot] {} {} '{}' x{} lore: {}", tag, idx,
          stack.getHoverName().getString(), stack.getCount(), lb);
      idx++;
    }
  }
}
