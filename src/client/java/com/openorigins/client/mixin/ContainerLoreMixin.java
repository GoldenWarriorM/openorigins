package com.openorigins.client.mixin;

import com.openorigins.client.util.HomeInfoCache;
import java.util.List;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
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
    String title = self.getTitle().getString().trim();
    LORE_LOG.info("[homes-lore] container opened: '{}'", title);
    if (!title.equalsIgnoreCase("Homes")) {
      return;
    }
    for (Slot slot : self.getMenu().slots) {
      if (!slot.hasItem()) {
        continue;
      }
      ItemStack stack = slot.getItem();
      Component name = stack.get(DataComponents.CUSTOM_NAME);
      ItemLore lore = stack.get(DataComponents.LORE);
      if (name == null || lore == null || lore.lines().isEmpty()) {
        continue;
      }
      List<Component> lines = lore.lines();
      boolean looksLikeHome = false;
      for (Component line : lines) {
        String s = line.getString();
        if (s.contains("World:") || s.contains("Position:") || s.contains("Created:")) {
          looksLikeHome = true;
          break;
        }
      }
      if (looksLikeHome) {
        HomeInfoCache.put(name.getString(), lines);
        LORE_LOG.info("[homes-lore] captured home '{}' ({} lore lines)", name.getString(), lines.size());
      }
    }
  }
}
