package com.openorigins.client.mixin;

import com.openorigins.client.HotkeyFeatures;
import com.openorigins.client.util.ContainerDump;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets hotkeys fire inside container GUIs (scan dump). */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerHotkeyMixin {
  @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
  private void openorigins$guiHotkey(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
    if (HotkeyFeatures.matchesScan(event)) {
      org.slf4j.LoggerFactory.getLogger("openorigins").info("[hotkey] scan pressed in GUI");
      try {
        ContainerDump.dump(Minecraft.getInstance());
      } catch (Throwable t) {
        org.slf4j.LoggerFactory.getLogger("openorigins").warn("[hotkey] dump failed: {}", t.toString());
      }
      cir.setReturnValue(true);
    }
  }
}
