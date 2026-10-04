package com.openorigins.client.mixin;

import com.openorigins.client.util.HomeInfoCache;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures home lore from the server's own Homes container GUI. */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerLoreMixin {
  private static final org.slf4j.Logger LORE_LOG =
      org.slf4j.LoggerFactory.getLogger("openorigins");

  @Inject(method = "init()V", at = @At("TAIL"))
  private void openorigins$captureHomesLore(CallbackInfo ci) {
    AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
    LORE_LOG.info("[homes-lore] container opened: '{}'", self.getTitle().getString().trim());
    int n = HomeInfoCache.captureFrom(self);
    if (n > 0) {
      LORE_LOG.info("[homes-lore] captured {} homes on open", n);
    }
    com.openorigins.client.util.OpenWorldCache.scan(self);
    com.openorigins.client.util.BadgerCache.scan(self);
    com.openorigins.client.util.CosmeticCatalogCache.scan(self);
    try {
      Object inv = null;
      try {
        if (net.minecraft.client.Minecraft.getInstance() != null
            && net.minecraft.client.Minecraft.getInstance().player != null) {
          inv = net.minecraft.client.Minecraft.getInstance().player.getInventory();
        }
      } catch (Throwable ignored) {
      }
      int idx = 0;
      for (net.minecraft.world.inventory.Slot slot : self.getMenu().slots) {
        if (idx >= 80) {
          break;
        }
        String tag = (inv != null && slot.container == inv) ? "inv" : "top";
        if (!slot.hasItem()) {
          idx++;
          continue;
        }
        net.minecraft.world.item.ItemStack stack = slot.getItem();
        String firstLore = "";
        net.minecraft.world.item.component.ItemLore lore =
            stack.get(net.minecraft.core.component.DataComponents.LORE);
        if (lore != null && !lore.lines().isEmpty()) {
          firstLore = lore.lines().get(0).getString();
          if (firstLore.length() > 60) {
            firstLore = firstLore.substring(0, 60);
          }
        }
        LORE_LOG.info("[menu-slot] {} {} '{}' x{} lore1: '{}'", tag, idx,
            stack.getHoverName().getString(), stack.getCount(), firstLore);
        idx++;
      }
    } catch (Throwable ignored) {
    }
  }
}
