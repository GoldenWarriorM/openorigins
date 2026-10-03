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
    try {
      StringBuilder sb = new StringBuilder();
      int count = 0;
      for (net.minecraft.world.inventory.Slot slot : self.getMenu().slots) {
        if (!slot.hasItem() || count >= 30) {
          continue;
        }
        String nm = slot.getItem().getHoverName().getString();
        if (sb.length() > 0) {
          sb.append(" | ");
        }
        sb.append(nm.length() > 40 ? nm.substring(0, 40) : nm);
        count++;
      }
      LORE_LOG.info("[menu-dump] {} slots: {}", count, sb);
    } catch (Throwable ignored) {
    }
  }
}
