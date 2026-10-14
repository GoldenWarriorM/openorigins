package com.openorigins.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.openorigins.client.ConnectionUtil;
import com.openorigins.client.OpenOriginsConfig;
import com.openorigins.client.OriginFeature;
import com.openorigins.client.util.CustomItemIds;
import com.openorigins.client.util.DebugLog;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Experimental strict recipe-book matching for custom items. Display inputs
 * are resolved to real stacks (which may carry polymer ids) and matched
 * exactly against inventory: a banana box only satisfies a banana-box slot,
 * plain paper only a plain-paper slot. Only narrows the vanilla result, never
 * widens it. Falls back to plain-only counting when displays carry no custom
 * data or cannot be resolved.
 */
@Mixin(RecipeDisplayEntry.class)
public abstract class StrictRecipeMatchMixin {
  /**
   * Single resolved id per stack (polymer id when present, else vanilla id).
   * Matches polymer's own CompatUtils semantics: holders may drift between
   * placeholder items across versions, the server id is what counts.
   */

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
    if (mc == null || mc.player == null || mc.level == null) {
      return true;
    }
    List<SlotDisplay> inputs = displayInputs(self.display());
    if (inputs == null) {
      return plainOnly(self, mc, requirements.get());
    }
    List<Set<String>> slots;
    boolean anyCustom;
    try {
      ContextMap context = SlotDisplayContext.fromLevel(mc.level);
      slots = new ArrayList<>();
      anyCustom = false;
      for (SlotDisplay input : inputs) {
        List<ItemStack> resolved = input.resolveForStacks(context);
        if (resolved.isEmpty()) {
          continue;
        }
        Set<String> options = new HashSet<>();
        for (ItemStack stack : resolved) {
          if (stack.isEmpty()) {
            continue;
          }
          String id = idKey(stack);
          options.add(id);
          if (isCustomId(id)) {
            anyCustom = true;
          }
        }
        if (!options.isEmpty()) {
          slots.add(options);
        }
      }
    } catch (Throwable t) {
      return plainOnly(self, mc, requirements.get());
    }
    if (!anyCustom) {
      return plainOnly(self, mc, requirements.get());
    }
    Map<String, Integer> have = new HashMap<>();
    addInventory(have, mc.player.getInventory().getNonEquipmentItems());
    addInventory(have, List.of(mc.player.containerMenu.getCarried(), mc.player.getOffhandItem()));
    boolean ok = assign(slots, 0, have);
    if (!ok) {
      DebugLog.info("[recipe] strict-hide {} (needs custom inputs)",
          self.id());
    }
    return ok;
  }

  private static List<SlotDisplay> displayInputs(RecipeDisplay display) {
    if (display instanceof ShapedCraftingRecipeDisplay shaped) {
      return shaped.ingredients();
    }
    if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
      return shapeless.ingredients();
    }
    return null;
  }

  private static boolean plainOnly(RecipeDisplayEntry self, Minecraft mc,
      List<Ingredient> requirements) {
    StackedItemContents plain = new StackedItemContents();
    for (ItemStack stack : mc.player.getInventory().getNonEquipmentItems()) {
      if (stack.isEmpty() || isCustom(stack)) {
        continue;
      }
      plain.accountStack(stack);
    }
    return plain.canCraft(requirements, null);
  }

  private static void addInventory(Map<String, Integer> have, Iterable<ItemStack> stacks) {
    for (ItemStack stack : stacks) {
      if (stack == null || stack.isEmpty()) {
        continue;
      }
      String key = idKey(stack);
      have.put(key, have.getOrDefault(key, 0) + stack.getCount());
    }
  }

  private static boolean assign(List<Set<String>> slots, int index, Map<String, Integer> have) {
    if (index >= slots.size()) {
      return true;
    }
    for (String key : slots.get(index)) {
      int left = have.getOrDefault(key, 0);
      if (left <= 0) {
        continue;
      }
      have.put(key, left - 1);
      if (assign(slots, index + 1, have)) {
        return true;
      }
      have.put(key, left);
    }
    return false;
  }

  /** Resolved server id, else vanilla id — never null. */
  private static String idKey(ItemStack stack) {
    Optional<String> id = CustomItemIds.resolve(stack);
    if (id.isPresent()) {
      return id.get();
    }
    CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
    if (cmd != null && !cmd.floats().isEmpty()) {
      return "cmd:" + cmd.floats();
    }
    return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
  }

  private static boolean isCustomId(String idKey) {
    return !idKey.startsWith("minecraft:");
  }

  private static boolean isCustom(ItemStack stack) {
    if (CustomItemIds.resolve(stack).isPresent()) {
      return true;
    }
    CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
    return cmd != null && !cmd.floats().isEmpty();
  }
}
