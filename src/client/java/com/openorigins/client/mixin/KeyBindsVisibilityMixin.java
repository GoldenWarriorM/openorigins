package com.openorigins.client.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.openorigins.client.HotkeyFeatures;
import com.openorigins.client.util.DebugLog;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Hides the Scan Worn Cosmetics binding unless debug logging is on. */
@Mixin(KeyBindsList.class)
public abstract class KeyBindsVisibilityMixin {
  @WrapWithCondition(method = "<init>",
      at = @At(value = "INVOKE",
          target = "Lnet/minecraft/client/gui/screens/options/controls/KeyBindsList;addEntry(Lnet/minecraft/client/gui/components/AbstractSelectionList$Entry;)I"))
  private boolean openorigins$hideScanKey(KeyBindsList self, Object entry) {
    try {
      if (entry instanceof KeyBindsList.KeyEntry) {
        KeyEntryAccessor acc = (KeyEntryAccessor) entry;
        if (acc.openorigins$getKey() == HotkeyFeatures.scanKey() && !DebugLog.enabled()) {
          return false;
        }
      }
    } catch (Throwable ignored) {
    }
    return true;
  }
}
