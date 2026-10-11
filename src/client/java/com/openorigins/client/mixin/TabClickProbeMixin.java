package com.openorigins.client.mixin;

import net.minecraft.client.gui.components.TabButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Temporary diagnostics: which config tab clicks actually fire. */
@Mixin(TabButton.class)
public abstract class TabClickProbeMixin {
  @Inject(method = "onPress", at = @At("HEAD"))
  private void openorigins$logTabClick(CallbackInfo ci) {
    try {
      TabButton self = (TabButton) (Object) this;
      String title = "?";
      try {
        if (self.tab() instanceof net.minecraft.client.gui.components.tabs.GridLayoutTab grid) {
          title = grid.getTabTitle().getString();
        }
      } catch (Throwable ignored) {
      }
      com.openorigins.client.util.DebugLog.info("[config] tab clicked: '{}'", title);
    } catch (Throwable ignored) {
    }
  }
}
