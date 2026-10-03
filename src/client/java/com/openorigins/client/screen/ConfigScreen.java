package com.openorigins.client.screen;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class ConfigScreen extends Screen {
  public final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
  private final Screen parent;
  private FeatureList list;

  public ConfigScreen(Screen parent) {
    super(Component.translatable("screen.openorigins.config.title"));
    this.parent = parent;
  }

  private Button cornerButton;

  @Override
  protected void init() {
    this.layout.addTitleHeader(this.title, this.font);
    this.list = this.layout.addToContents(new FeatureList(this.minecraft, this.width,
        this.layout.getContentHeight(), this.layout.getHeaderHeight()));
    this.cornerButton = Button.builder(Component.empty(), button -> {
      com.openorigins.client.OpenOriginsConfig.getInstance().cycleBadgerHudCorner();
      this.refreshCornerButton();
    }).width(200).build();
    this.refreshCornerButton();
    this.layout.addToFooter(this.cornerButton);
    this.layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
        .width(200).build());
    this.layout.visitWidgets(this::addRenderableWidget);
    this.repositionElements();
  }

  private void refreshCornerButton() {
    if (this.cornerButton == null) {
      return;
    }
    com.openorigins.client.OpenOriginsConfig.HudCorner corner =
        com.openorigins.client.OpenOriginsConfig.getInstance().getBadgerHudCorner();
    this.cornerButton.setMessage(Component.translatable("config.openorigins.badger_corner",
        Component.translatable("config.openorigins.corner." + corner.name().toLowerCase())));
  }

  protected void repositionElements() {
    this.layout.arrangeElements();
    if (this.list != null) {
      this.list.updateSize(this.width, this.layout);
    }
  }

  @Override
  public void onClose() {
    if (this.minecraft != null) {
      this.minecraft.gui.setScreen(this.parent);
    }
  }
}
