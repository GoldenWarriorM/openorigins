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
    }).width(150).build();
    this.refreshCornerButton();
    net.minecraft.client.gui.components.EditBox capBox =
        new net.minecraft.client.gui.components.EditBox(this.font, 0, 0, 40, 20,
            Component.translatable("config.openorigins.capacity_hint"));
    capBox.setMaxLength(2);
    capBox.setValue(Integer.toString(
        com.openorigins.client.OpenOriginsConfig.getInstance().getHomeCapacity()));
    capBox.setResponder(s -> {
      String digits = s.replaceAll("[^0-9]", "");
      if (!digits.equals(s)) {
        capBox.setValue(digits);
        return;
      }
      if (!digits.isEmpty()) {
        try {
          com.openorigins.client.OpenOriginsConfig.getInstance()
              .setHomeCapacity(Integer.parseInt(digits));
        } catch (NumberFormatException ignored) {
        }
      }
    });
    capBox.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
        Component.translatable("config.openorigins.capacity_hint")));
    net.minecraft.client.gui.layouts.LinearLayout row =
        net.minecraft.client.gui.layouts.LinearLayout.horizontal().spacing(8);
    row.addChild(this.cornerButton);
    row.addChild(capBox);
    row.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
        .width(150).build());
    this.layout.addToFooter(row);
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
