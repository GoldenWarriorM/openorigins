package com.openorigins.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.openorigins.client.ConnectionUtil;
import com.openorigins.client.OpenOriginsConfig;
import com.openorigins.client.OriginFeature;
import com.openorigins.client.util.CustomItemIds;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Experimental strict recipe-book matching: stacks carrying custom identity
 * (polymer id or custom model data) never satisfy plain holder requirements,
 * so e.g. a banana box (paper base) no longer marks paper recipes craftable.
 * Only narrows the vanilla result, never widens it. Hides legitimate
 * custom-ingredient crafts too, because recipe requirements carry no
 * component info to match against.
 */
@Mixin(RecipeDisplayEntry.class)
public abstract class StrictRecipeMatchMixin {
  @ModifyReturnValue(
      method = "canCraft(Lnet/minecraft/world/entity/player/StackedItemContents;)Z",
      at = @At("RETURN"))
  private boolean openorigins$strictMatch(boolean original, StackedItemContents contents) {
    if (!original) {
      return false;
    }
    if (!OpenOriginsConfig.getInstance().isEnabled(OriginFeature.STRICT_RECIPE_MATCH)) {
      return true;
    }
    if (!ConnectionUtil.isConnected()) {
      return true;
    }
    RecipeDisplayEntry self = (RecipeDisplayEntry) (Object) this;
    Optional<List<Ingredient>> requirements = self.craftingRequirements();
    if (requirements.isEmpty()) {
      return false;
    }
    Minecraft mc = Minecraft.getInstance();
    if (mc == null || mc.player == null) {
      return true;
    }
    StackedItemContents plain = new StackedItemContents();
    for (ItemStack stack : mc.player.getInventory().getNonEquipmentItems()) {
      if (stack.isEmpty() || isCustom(stack)) {
        continue;
      }
      plain.accountStack(stack);
    }
    return plain.canCraft(requirements.get(), null);
  }

  private static boolean isCustom(ItemStack stack) {
    if (CustomItemIds.resolve(stack).isPresent()) {
      return true;
    }
    CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
    return cmd != null && !cmd.floats().isEmpty();
  }
}
