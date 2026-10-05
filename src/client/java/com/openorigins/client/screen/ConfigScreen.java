package com.openorigins.client.screen;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class ConfigScreen extends Screen {
  public HeaderAndFooterLayout layout;
  private final Screen parent;
  private FeatureList list;
  private ConfigTab tab = ConfigTab.GENERAL;
  private final Button[] tabButtons = new Button[ConfigTab.values().length];

  public ConfigScreen(Screen parent) {
    super(Component.translatable("screen.openorigins.config.title"));
    this.parent = parent;
  }

  @Override
  protected void init() {
    this.layout = new HeaderAndFooterLayout(this, 62, 33);
    this.layout.addTitleHeader(this.title, this.font);
    this.list = this.layout.addToContents(new FeatureList(this.minecraft, this.width,
        this.layout.getContentHeight(), this.layout.getHeaderHeight(), this.tab));
    this.layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
        .width(200).build());
    this.layout.visitWidgets(this::addRenderableWidget);
    ConfigTab[] tabs = ConfigTab.values();
    for (int i = 0; i < tabs.length; i++) {
      ConfigTab t = tabs[i];
      Button b = Button.builder(Component.translatable(t.getTitleKey()), button -> {
        this.tab = t;
        this.clearWidgets();
        this.init();
      }).bounds(0, 0, 100, 20).build();
      b.active = this.tab != t;
      this.tabButtons[i] = b;
      this.addRenderableWidget(b);
    }
    this.repositionElements();
  }

  protected void repositionElements() {
    this.layout.arrangeElements();
    if (this.list != null) {
      this.list.updateSize(this.width, this.layout);
    }
    int rowW = ConfigTab.values().length * 100 + (ConfigTab.values().length - 1) * 8;
    int x = (this.width - rowW) / 2;
    for (Button b : this.tabButtons) {
      if (b != null) {
        b.setX(x);
        b.setY(36);
        x += 108;
      }
    }
  }

  @Override
  public void onClose() {
    if (this.minecraft != null) {
      this.minecraft.gui.setScreen(this.parent);
    }
  }
}
