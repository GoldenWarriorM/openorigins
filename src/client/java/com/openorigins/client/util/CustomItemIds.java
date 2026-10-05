package com.openorigins.client.util;

import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Resolves the real server-side id of custom items for advanced tooltips.
 * Custom blocks/items carry their registry key inside custom data.
 */
public final class CustomItemIds {
  private CustomItemIds() {}

  public static Optional<String> resolve(ItemStack stack) {
    if (stack == null || stack.isEmpty()) {
      return Optional.empty();
    }
    CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
    if (custom == null) {
      return Optional.empty();
    }
    CompoundTag tag = custom.copyTag();
    Optional<CompoundTag> polymerOpt = tag.getCompound("$polymer:stack");
    if (polymerOpt.isPresent()) {
      Optional<String> id = polymerOpt.get().getString("id");
      if (id.isPresent() && !id.get().isBlank()) {
        return Optional.of(id.get().contains(":") ? id.get() : "originrealms:" + id.get());
      }
    }
    Optional<String> blockKey = tag.getString("CustomBlock");
    if (blockKey.isPresent()) {
      return blockKey;
    }
    Optional<CompoundTag> valuesOpt = tag.getCompound("PublicBukkitValues");
    if (valuesOpt.isEmpty()) {
      return Optional.empty();
    }
    CompoundTag values = valuesOpt.get();
    Optional<String> registryKey = values.getString("xcore:item-registry-key");
    if (registryKey.isPresent()) {
      return registryKey;
    }
    if (values.getBooleanOr("Cosmetic", false)) {
      Component name = stack.get(DataComponents.CUSTOM_NAME);
      if (name != null) {
        String plain = name.getString().replaceAll("[^\\x00-\\x7F]", "")
            .toLowerCase(Locale.ROOT).strip().replace(' ', '_');
        if (!plain.isEmpty()) {
          return Optional.of(plain);
        }
      }
      return Optional.of("cosmetic");
    }
    return Optional.empty();
  }
}
