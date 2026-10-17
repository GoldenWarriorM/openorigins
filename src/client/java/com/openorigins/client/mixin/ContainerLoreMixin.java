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

  @Inject(method = "init()V", at = @At("TAIL"))
  private void openorigins$captureHomesLore(CallbackInfo ci) {
    AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
    int n = HomeInfoCache.captureFrom(self);
    if (n > 0) {
      com.openorigins.client.util.DebugLog.info("[homes-lore] captured {} homes on open", n);
    }
    com.openorigins.client.util.OpenWorldCache.scan(self);
    com.openorigins.client.util.BadgerCache.scan(self);
    com.openorigins.client.util.CosmeticCatalogCache.scan(self);
    com.openorigins.client.util.CosmeticCatalogCache.profilePoll(self);
  }
}
