package com.openorigins.client.mixin;

import com.openorigins.client.BadgerHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A chest menu layers a dim gradient and a blur over the HUD, so the badger task
 * rows went dark while turning items in. For those screens only, the same rows
 * are drawn here right after the screen's background and widgets: above the dim,
 * but below the chest's own panel. Every other menu keeps the dimmed HUD.
 */
@Mixin(Screen.class)
public abstract class ScreenHudMixin {
  @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
      at = @At("TAIL"))
  private void openorigins$drawBadgerHud(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
      float partialTick, CallbackInfo ci) {
    Minecraft mc = Minecraft.getInstance();
    if (mc == null || mc.level == null || mc.player == null
        || !(mc.gui.screen() instanceof AbstractContainerScreen<?>)) {
      return;
    }
    BadgerHud.draw(graphics);
  }
}
