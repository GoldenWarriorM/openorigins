package com.openorigins.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optimistic helmet tracking: applies the client-predicted menu state
 * immediately, the server corrects it afterwards on mismatch.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class MenuClickMixin {
  @Inject(method = "clicked", at = @At("TAIL"))
  private void openorigins$optimistic(int slotIndex, int button, ContainerInput clickType,
      Player player, CallbackInfo ci) {
    AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
    if (menu.containerId != 0) {
      return;
    }
    Minecraft mc = Minecraft.getInstance();
    if (mc == null || mc.player == null || player != mc.player) {
      return;
    }
    ItemStack armor5;
    try {
      armor5 = menu.getSlot(5).getItem();
    } catch (Throwable t) {
      return;
    }
    if (armor5 == null || armor5.isEmpty()) {
      com.openorigins.client.util.HelmetCosmeticTracker.optimisticClear();
    } else {
      com.openorigins.client.util.HelmetCosmeticTracker.optimisticStash(armor5);
    }
  }
}
