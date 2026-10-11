package com.openorigins.client.mixin;

import java.util.List;
import net.minecraft.client.gui.components.TabButton;
import net.minecraft.client.gui.components.tabs.MenuTabBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MenuTabBar.class)
public interface TabBarAccessor {
  @Accessor("tabButtons")
  List<TabButton> openorigins$tabButtons();
}
