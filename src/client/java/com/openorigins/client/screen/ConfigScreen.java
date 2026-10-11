package com.openorigins.client.screen;

import com.openorigins.client.HotkeyFeatures;
import com.openorigins.client.OpenOriginsConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.Button;
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
  private ConfigTab[] tabs = new ConfigTab[0];
  private Tab[] pages = new Tab[0];
  private final List<Button> tabButtons = new ArrayList<>();
  private int activeTab;

  public ConfigScreen(Screen parent) {
    super(Component.translatable("screen.openorigins.config.title"));
    this.parent = parent;
  }

  private ConfigTabPage pageFor(ConfigTab tab) {
    FeatureList list = new FeatureList(this.minecraft, this.width,
        this.layout.getContentHeight(), this.layout.getHeaderHeight(), tab);
    return new ConfigTabPage(Component.translatable(tab.getTitleKey()), list, this.layout);
  }

  private int tabWidth(ConfigTab tab) {
    if (this.minecraft == null) {
      return 80;
    }
    return this.minecraft.font.width(Component.translatable(tab.getTitleKey())) + 24;
  }

  private void refreshTabButtons() {
    for (int i = 0; i < this.tabButtons.size() && i < this.tabs.length; i++) {
      Component title = Component.translatable(this.tabs[i].getTitleKey());
      this.tabButtons.get(i).setMessage(i == this.activeTab
          ? title.copy().withStyle(net.minecraft.ChatFormatting.GOLD)
          : title.copy().withStyle(net.minecraft.ChatFormatting.GRAY));
    }
  }

  private void selectTab(int i) {
    if (i < 0 || i >= this.pages.length) {
      return;
    }
    this.activeTab = i;
    this.tabManager.setCurrentTab(this.pages[i], true);
    this.refreshTabButtons();
  }

  @Override
  protected void init() {
    this.layout = new HeaderAndFooterLayout(this);
    this.tabManager = new TabManager(this::addRenderableWidget, this::removeWidget);
    this.tabs = ConfigTab.values();
    this.pages = new Tab[this.tabs.length];
    for (int i = 0; i < this.tabs.length; i++) {
      pages[i] = this.pageFor(this.tabs[i]);
    }
    this.tabButtons.clear();
    for (int i = 0; i < this.tabs.length; i++) {
      final int idx = i;
      Button b = Button.builder(Component.translatable(this.tabs[i].getTitleKey()),
          button -> this.selectTab(idx)).bounds(0, 0, this.tabWidth(this.tabs[i]), 20).build();
      this.tabButtons.add(b);
      this.addRenderableWidget(b);
    }
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
    this.activeTab = 0;
    this.tabManager.setCurrentTab(this.pages[0], false);
    this.refreshTabButtons();
    this.repositionElements();
  }

  protected void repositionElements() {
    int totalW = 0;
    for (Button b : this.tabButtons) {
      totalW += b.getWidth();
    }
    totalW += 4 * Math.max(0, this.tabButtons.size() - 1);
    int x = (this.width - totalW) / 2;
    for (Button b : this.tabButtons) {
      b.setX(x);
      b.setY(7);
      x += b.getWidth() + 4;
    }
    int tabAreaTop = 7 + 20 + 4;
    this.tabManager.setTabArea(new ScreenRectangle(0, tabAreaTop, this.width,
        this.height - this.layout.getFooterHeight() - tabAreaTop));
    this.layout.setHeaderHeight(tabAreaTop);
    this.layout.arrangeElements();
  }

  @Override
  public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
    if (com.openorigins.client.HotkeyFeatures.isScanCaptureArmed()
        || com.openorigins.client.HotkeyFeatures.isCapturingAny()) {
      if (com.openorigins.client.HotkeyFeatures.isScanCaptureArmed()) {
        com.openorigins.client.HotkeyFeatures.confirmScanCapture(event.key(), event.scancode());
      } else {
        com.openorigins.client.HotkeyFeatures.confirmCapture(event.key(), event.scancode());
      }
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
