package com.openorigins.client.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.gui.screens.options.controls.KeyBindsList$KeyEntry")
public interface KeyEntryAccessor {
  @Accessor("key")
  KeyMapping openorigins$getKey();
}
