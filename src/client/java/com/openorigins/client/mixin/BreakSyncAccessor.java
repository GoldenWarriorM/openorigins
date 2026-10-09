package com.openorigins.client.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MultiPlayerGameMode.class)
public interface BreakSyncAccessor {
  @Accessor("destroyProgress")
  float openorigins$getDestroyProgress();
}
