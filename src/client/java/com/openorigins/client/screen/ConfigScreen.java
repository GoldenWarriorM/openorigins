package com.openorigins.client.screen;

import com.openorigins.client.HotkeyFeatures;
import com.openorigins.client.OpenOriginsConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.tabs.MenuTabBar;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class ConfigScreen extends Screen {
  public HeaderAndFooterLayout layout;
  private final Screen parent;
  private TabManager tabManager;
  private MenuTabBar tabBar;

  public ConfigScreen(Screen parent) {
    super(Component.translatable("screen.openorigins.config.title"));
    this.parent = parent;
  }

  private ConfigTabPage pageFor(ConfigTab tab) {
    FeatureList list = new FeatureList(this.minecraft, this.width,
        this.layout.getContentHeight(), this.layout.getHeaderHeight(), tab);
    return new ConfigTabPage(Component.translatable(tab.getTitleKey()), list, this.layout);
  }

  @Override
  protected void init() {
    this.layout = new HeaderAndFooterLayout(this);
    this.tabManager = new TabManager(this::addRenderableWidget, this::removeWidget);
    ConfigTab[] tabs = ConfigTab.values();
    Tab[] pages = new Tab[tabs.length];
    for (int i = 0; i < tabs.length; i++) {
      pages[i] = this.pageFor(tabs[i]);
    }
    this.tabBar = MenuTabBar.builder(this.tabManager, this.width).addTabs(pages).build();
    this.addRenderableWidget(this.tabBar);
    LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
    footer.addChild(Button.builder(Component.translatable("config.openorigins.reset"),
        button -> {
          OpenOriginsConfig.getInstance().resetAll();
          HotkeyFeatures.reloadScanKey();
          if (this.minecraft != null) {
            this.minecraft.gui.setScreen(new ConfigScreen(this.parent));
          }
        }).width(120).build());
    footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
        .width(200).build());
    this.layout.visitWidgets(button -> {
      button.setTabOrderGroup(1);
      this.addRenderableWidget(button);
    });
    this.tabBar.selectTab(0, false);
    this.repositionElements();
  }

  protected void repositionElements() {
    if (this.tabBar != null) {
      this.tabBar.arrangeElements(this.width);
      this.tabBar.setY(7);
      int tabAreaTop = this.tabBar.getRectangle().bottom() + 4;
      this.tabManager.setTabArea(new ScreenRectangle(0, tabAreaTop, this.width,
          this.height - this.layout.getFooterHeight() - tabAreaTop));
      this.layout.setHeaderHeight(tabAreaTop);
      this.layout.arrangeElements();
    }
  }

  @Override
  public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
    if (com.openorigins.client.HotkeyFeatures.isScanCaptureArmed()) {
      com.openorigins.client.HotkeyFeatures.confirmScanCapture(event.key(), event.scancode());
      net.minecraft.client.gui.components.tabs.Tab t = this.tabManager.getCurrentTab();
      if (t instanceof ConfigTabPage page) {
        page.list().refreshEntries();
      }
      return true;
    }
    return super.keyPressed(event);
  }

  @Override
  public void onClose() {
    if (this.minecraft != null) {
      this.minecraft.gui.setScreen(this.parent);
    }
  }
}
