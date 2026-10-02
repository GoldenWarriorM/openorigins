package com.openorigins.client.mixin;

import com.openorigins.client.server.EntityData;
import com.openorigins.client.util.TagHolder;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public abstract class RenderStateTagMixin implements TagHolder {
  @Unique private EntityData.NameTag openorigins$tag;

  @Override
  public void openorigins$setTag(@Nullable EntityData.NameTag tag) {
    this.openorigins$tag = tag;
  }

  @Override
  public @Nullable EntityData.NameTag openorigins$getTag() {
    return this.openorigins$tag;
  }
}
