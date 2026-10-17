package com.openorigins.client.mixin;

import com.openorigins.client.ConnectionUtil;
import com.openorigins.client.util.HelmetCosmeticTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.InteractionResult;

/**
 * Predicts RMB-equip (outside inventory) for the helmet overlay: vanilla
 * swaps hand content into the entity equipment slot without touching any
 * container, so MenuClickMixin never sees it. Server syncs confirm or roll
 * back via the usual optimistic machinery. Cosmetics are ignored downstream,
 * as if they did not exist.
 */
@Mixin(Equippable.class)
public abstract class EquipSwapMixin {
  @Inject(method = "swapWithEquipmentSlot", at = @At("TAIL"), require = 0)
  private void openorigins$predictEquip(ItemStack inHand, Player player,
      CallbackInfoReturnable<InteractionResult> cir) {
    if (!ConnectionUtil.isConnected()) {
      return;
    }
    Minecraft mc = Minecraft.getInstance();
    if (mc == null || mc.player == null || player != mc.player) {
      return;
    }
    if (mc.level != null && !mc.level.isClientSide()) {
      return;
    }
    Equippable self = (Equippable) (Object) this;
    if (self.slot() != EquipmentSlot.HEAD) {
      return;
    }
    ItemStack worn;
    try {
      worn = player.getItemBySlot(EquipmentSlot.HEAD);
    } catch (Throwable t) {
      return;
    }
    if (worn == null || worn.isEmpty()) {
      return;
    }
    HelmetCosmeticTracker.optimisticStash(worn);
  }
}
