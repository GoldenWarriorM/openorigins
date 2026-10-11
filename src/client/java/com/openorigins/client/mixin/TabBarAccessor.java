package com.openorigins.client.mixin;

import net.minecraft.client.gui.components.TabButton;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TabNavigationBar.class)
public interface TabBarAccessor {
  @Accessor("tabButtons")
  com.google.common.collect.ImmutableList<TabButton> openorigins$tabButtons();
}
