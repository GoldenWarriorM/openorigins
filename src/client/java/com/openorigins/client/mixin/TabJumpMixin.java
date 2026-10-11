package com.openorigins.client.mixin;

import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tab selection (click or keyboard focus) teleports the single settings feed:
 * pre-scrolls the newly shown page to its section (shared scroll carries it
 * everywhere).
 */
@Mixin(TabNavigationBar.class)
public abstract class TabJumpMixin {
  @Inject(method = "setFocused", at = @At("TAIL"))
  private void openorigins$jumpToSection(GuiEventListener focused, CallbackInfo ci) {
    try {
      if (focused instanceof net.minecraft.client.gui.components.TabButton button
          && button.tab() instanceof com.openorigins.client.screen.ConfigTabPage page) {
        page.list().scrollToSection(page.tab);
      }
    } catch (Throwable ignored) {
    }
  }
}
