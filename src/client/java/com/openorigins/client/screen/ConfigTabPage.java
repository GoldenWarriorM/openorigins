package com.openorigins.client.screen;

import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;

/** One config tab page hosting its feature list. */
public class ConfigTabPage extends GridLayoutTab {
  private final FeatureList list;
  private final HeaderAndFooterLayout layout;

  public ConfigTabPage(Component title, FeatureList list, HeaderAndFooterLayout layout) {
    super(title);
    this.list = list;
    this.layout = layout;
  }

  @Override
  public void visitChildren(Consumer<AbstractWidget> childrenConsumer) {
    super.visitChildren(childrenConsumer);
    childrenConsumer.accept(this.list);
  }

  @Override
  public void doLayout(ScreenRectangle rect) {
    super.doLayout(rect);
    this.list.updateSize(rect.width(), this.layout);
  }
}
