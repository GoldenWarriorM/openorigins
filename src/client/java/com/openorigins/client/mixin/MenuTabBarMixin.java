package com.openorigins.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.TabButton;
import net.minecraft.client.gui.components.tabs.MenuTabBar;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Vanilla splits max 400px evenly between tabs (~53px for 7 tabs), clipping
 * every title. Size each tab by its own text instead; vanilla look kept.
 */
@Mixin(MenuTabBar.class)
public abstract class MenuTabBarMixin {
  @Redirect(
      method = "arrangeElements",
      at = @At(
          value = "INVOKE",
          target = "Lnet/minecraft/client/gui/components/TabButton;setWidth(I)V"))
  private void openorigins$tabWidth(TabButton button, int equalWidth) {
    try {
      Tab tab = button.tab();
      Component title = null;
      if (tab instanceof net.minecraft.client.gui.components.tabs.GridLayoutTab grid) {
        title = grid.getTabTitle();
      }
      Minecraft mc = Minecraft.getInstance();
      if (title != null && mc != null) {
        button.setWidth(mc.font.width(title) + 24);
        return;
      }
    } catch (Throwable ignored) {
    }
    button.setWidth(equalWidth);
  }
}
