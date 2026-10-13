package com.openorigins.client.mixin;

import com.openorigins.client.OpenOriginsConfig;
import com.openorigins.client.OriginFeature;
import com.openorigins.client.util.OaBlockPick;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Middle-click picks custom blocks by matching inventory item ids. */
@Mixin(Minecraft.class)
public abstract class OaBlockPickMixin {
  @Inject(method = "pickBlockOrEntity", at = @At("HEAD"), cancellable = true)
  private void openorigins$pickCustom(CallbackInfo ci) {
    boolean on;
    try {
      on = OpenOriginsConfig.getInstance().isEnabled(OriginFeature.OA_BLOCK_PICKER);
    } catch (Throwable t) {
      return;
    }
    if (!on) return;
    Minecraft mc;
    LocalPlayer player;
    try {
      mc = Minecraft.getInstance();
      player = mc.player;
      if (player == null || mc.level == null) return;
    } catch (Throwable t) {
      return;
    }
    HitResult hit;
    try {
      hit = mc.hitResult;
    } catch (Throwable t) {
      return;
    }
    if (!(hit instanceof BlockHitResult blockHit)) return;
    BlockState state;
    try {
      state = mc.level.getBlockState(blockHit.getBlockPos());
    } catch (Throwable t) {
      return;
    }
    String want = OaBlockPick.customBlockName(state);
    if (want.isEmpty()) return;
    Inventory inv = player.getInventory();
    int pickSlot = -1;
    for (int slot = 0; slot < inv.getContainerSize(); slot++) {
      ItemStack stack;
      try {
        stack = inv.getItem(slot);
      } catch (Throwable t) {
        continue;
      }
      if (stack.isEmpty()) continue;
      String have = OaBlockPick.customItemName(stack);
      if (have.isEmpty()) continue;
      if (OaBlockPick.namesMatch(want, have)) {
        pickSlot = slot;
        break;
      }
    }
    if (pickSlot < 0 || pickSlot >= 9) return;
    try {
      inv.setSelectedSlot(pickSlot);
    } catch (Throwable t) {
      return;
    }
    ci.cancel();
  }
}
