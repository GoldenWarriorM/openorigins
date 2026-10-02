package com.openorigins.client.util;

import com.openorigins.client.server.EntityData;
import org.jetbrains.annotations.Nullable;

/** Custom tag slot on the render state. */
public interface TagHolder {
  void openorigins$setTag(@Nullable EntityData.NameTag tag);

  @Nullable
  EntityData.NameTag openorigins$getTag();
}
