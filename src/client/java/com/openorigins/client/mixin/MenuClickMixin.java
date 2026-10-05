package com.openorigins.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optimistic helmet tracking: applies the client-predicted menu state
 * immediately, the server corrects it afterwards on mismatch.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class MenuClickMixin {
  private static ItemStack preClickItem = ItemStack.EMPTY;
  private static int preClickSlot = -1;

  @Inject(method = "clicked", at = @At("HEAD"))
  private void openorigins$captureClean(int slotIndex, int button, ContainerInput clickType,
      Player player, CallbackInfo ci) {
    AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
    Minecraft mc = Minecraft.getInstance();
    if (mc == null || mc.player == null || player != mc.player) {
      return;
    }
    if (menu.containerId == 0) {
      preClickSlot = slotIndex;
      try {
        preClickItem = menu.getSlot(slotIndex).getItem().copy();
      } catch (Throwable ignored) {
        preClickItem = ItemStack.EMPTY;
      }
      return;
    }
    try {
      net.minecraft.world.inventory.Slot slot = menu.getSlot(slotIndex);
      if (slot != null && slot.hasItem() && hasToggleMarker(slot.getItem())) {
        com.openorigins.client.util.CosmeticCatalogCache.putClean(slot.getItem());
      }
      if (slot != null && slot.hasItem()) {
        net.minecraft.world.item.ItemStack stack = slot.getItem();
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM
            .getKey(stack.getItem()).toString();
        String name = stack.getHoverName().getString();
        for (com.openorigins.client.util.BadgerCache.Required r
            : com.openorigins.client.util.BadgerCache.required()) {
          if (r.itemId().equals(id) && r.displayName().equals(name)) {
            com.openorigins.client.util.BadgerCache.noteTaskClick(menu.containerId, id, name);
            break;
          }
        }
      }
    } catch (Throwable ignored) {
    }
  }

  @Inject(method = "clicked", at = @At("TAIL"))
  private void openorigins$optimistic(int slotIndex, int button, ContainerInput clickType,
      Player player, CallbackInfo ci) {
    AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
    if (menu.containerId != 0) {
      return;
    }
    ItemStack armor5;
    try {
      armor5 = menu.getSlot(5).getItem();
    } catch (Throwable t) {
      return;
    }
    if (armor5 == null || armor5.isEmpty()) {
      com.openorigins.client.util.HelmetCosmeticTracker.optimisticClear();
    } else {
      com.openorigins.client.util.HelmetCosmeticTracker.optimisticStash(armor5);
    }
    try {
      ItemStack post = menu.getSlot(slotIndex).getItem();
      ItemStack pre = preClickItem;
      preClickItem = ItemStack.EMPTY;
      preClickSlot = -1;
      if (!pre.isEmpty() && (post == null || post.isEmpty()
          || !ItemStack.isSameItemSameComponents(pre, post))) {
        com.openorigins.client.util.HelmetCosmeticTracker.optimisticEquipFrom(pre, armor5);
      }
    } catch (Throwable ignored) {
    }
  }

  private static boolean hasToggleMarker(net.minecraft.world.item.ItemStack stack) {
    if (stack == null || stack.isEmpty()) {
      return false;
    }
    net.minecraft.world.item.component.ItemLore lore = stack.get(
        net.minecraft.core.component.DataComponents.LORE);
    if (lore == null) {
      return false;
    }
    for (net.minecraft.network.chat.Component line : lore.lines()) {
      if (line.getString().contains("Left-click to")) {
        return true;
      }
    }
    return false;
  }
}
