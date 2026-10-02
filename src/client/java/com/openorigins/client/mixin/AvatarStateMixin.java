package com.openorigins.client.mixin;

import com.openorigins.client.util.BackItemHolder;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public abstract class AvatarStateMixin implements BackItemHolder {
  @Unique private ItemStackRenderState openorigins$backItem;

  @Override
  public void openorigins$setBackItem(@Nullable ItemStackRenderState state) {
    this.openorigins$backItem = state;
  }

  @Override
  public @Nullable ItemStackRenderState openorigins$getBackItem() {
    return this.openorigins$backItem;
  }
}
