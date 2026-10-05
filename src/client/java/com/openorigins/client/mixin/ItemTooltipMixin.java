package com.openorigins.client.mixin;

import com.openorigins.client.ConnectionUtil;
import com.openorigins.client.OpenOriginsConfig;
import com.openorigins.client.OriginFeature;
import com.openorigins.client.util.CustomItemIds;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shows the real custom id (originrealms:...) instead of the vanilla base
 * (minecraft:paper, ...) in advanced (F3+H) tooltips.
 */
@Mixin(ItemStack.class)
public abstract class ItemTooltipMixin {
  @Inject(method = "getTooltipLines", at = @At("TAIL"))
  private void openorigins$customId(Item.TooltipContext context, Player player,
      TooltipFlag tooltipFlag, CallbackInfoReturnable<List<Component>> cir) {
    if (tooltipFlag == null || !tooltipFlag.isAdvanced()) {
      return;
    }
    if (!OpenOriginsConfig.getInstance().isEnabled(OriginFeature.CUSTOM_ITEM_IDS)) {
      return;
    }
    if (!ConnectionUtil.isConnected()) {
      return;
    }
    ItemStack self = (ItemStack) (Object) this;
    Optional<String> key = CustomItemIds.resolve(self);
    String comps = "?";
    try {
      comps = self.getComponents().keySet().stream()
          .map(k -> k.toString()).collect(java.util.stream.Collectors.joining(","));
    } catch (Throwable ignored) {
    }
    com.openorigins.client.util.DebugLog.info("[itemid] vanilla={} resolved={} connected={} enabled={} comps=[{}]",
        BuiltInRegistries.ITEM.getKey(self.getItem()),
        key.map(Object::toString).orElse("<empty>"), ConnectionUtil.isConnected(),
        OpenOriginsConfig.getInstance().isEnabled(OriginFeature.CUSTOM_ITEM_IDS), comps);
    if (key.isEmpty()) {
      return;
    }
    String vanillaId = BuiltInRegistries.ITEM.getKey(self.getItem()).toString();
    if (key.get().equals(vanillaId)) {
      return;
    }
    List<Component> lines = cir.getReturnValue();
    if (lines == null) {
      return;
    }
    for (int i = lines.size() - 1; i >= 0; i--) {
      if (lines.get(i).getString().equals(vanillaId)) {
        lines.set(i, Component.literal("originrealms:" + key.get())
            .withStyle(ChatFormatting.DARK_GRAY));
        return;
      }
    }
  }
}
