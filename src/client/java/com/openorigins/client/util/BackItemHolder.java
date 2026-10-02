package com.openorigins.client.util;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.jetbrains.annotations.Nullable;

/** Back-item slot on the avatar render state. */
public interface BackItemHolder {
  void openorigins$setBackItem(@Nullable ItemStackRenderState state);

  @Nullable
  ItemStackRenderState openorigins$getBackItem();
}
