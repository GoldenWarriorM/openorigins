package com.openorigins.client.mixin;

import net.minecraft.client.gui.components.TabButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tab clicks teleport the single settings feed: pre-scroll the target page
 * (shared scroll carries it everywhere) before vanilla switches to it.
 */
@Mixin(TabButton.class)
public abstract class TabJumpMixin {
  @Inject(method = "onPress", at = @At("HEAD"))
  private void openorigins$jumpToSection(CallbackInfo ci) {
    try {
      TabButton self = (TabButton) (Object) this;
      if (self.tab() instanceof com.openorigins.client.screen.ConfigTabPage page) {
        page.list().scrollToSection(page.tab);
      }
    } catch (Throwable ignored) {
    }
  }
}
