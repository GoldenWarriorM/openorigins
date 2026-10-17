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
  private final List<SideTabButton> tabButtons = new ArrayList<>();
  private double tabHiY = 0;
  private double tabHiH = 0;
  private double tabHiAlpha = 0;
  private double tabSelY = 0;
  private double tabSelH = 0;
  private long tabHiLastNs = 0;
  private static final double TAB_HI_SPEED = 14.0;
  private Button resetButton;
  private Button doneButton;
  private int activeTab;
  private ConfigTab highlightTab;
  private static final int PANEL_X = 10;
  private static final int PANEL_TOP = 30;
  private static final int ROW_H = UiConstants.CONFIG_ROW_H;

  public ConfigScreen(Screen parent) {
    super(Component.translatable("screen.openorigins.config.title"));
    this.parent = parent;
  }

  private ConfigTabPage pageFor(ConfigTab tab) {
    FeatureList list = new FeatureList(this.minecraft, this.width,
        this.layout.getContentHeight(), this.layout.getHeaderHeight(), tab);
    list.scrollToSection(tab);
    return new ConfigTabPage(tab, Component.translatable(tab.getTitleKey()), list, this.layout);
  }

  private int panelWidth() {
    int w = 0;
    if (this.minecraft != null) {
      for (ConfigTab tab : this.tabs) {
        w = Math.max(w,
            this.minecraft.font.width(Component.translatable(tab.getTitleKey())));
      }
    }
    return w + 24;
  }

  private void refreshTabButtons() {
    for (int i = 0; i < this.tabButtons.size() && i < this.tabs.length; i++) {
      this.tabButtons.get(i).setSelected(this.tabs[i] == this.highlightTab);
    }
  }

  private void selectTab(int i) {
    if (i < 0 || i >= this.pages.length) {
      return;
    }
    this.activeTab = i;
    this.highlightTab = this.tabs[i];
    this.tabManager.setCurrentTab(this.pages[i], true);
    if (this.pages[i] instanceof ConfigTabPage page) {
      page.list().snapGroups();
      page.list().scrollToSection(this.tabs[i]);
    }
    this.refreshTabButtons();
  }

  @Override
  protected void init() {
    this.layout = new HeaderAndFooterLayout(this);
    this.tabManager = new TabManager(this::addRenderableWidget, this::removeWidget);
    this.tabs = ConfigTab.values();
    this.pages = new Tab[this.tabs.length];
    for (int i = 0; i < this.tabs.length; i++) {
      pages[i] = this.pageFor(tabs[i]);
    }
    com.openorigins.client.screen.FeatureList.setSpyListener(tab -> {
      if (tab != null && tab != this.highlightTab) {
        this.highlightTab = tab;
        this.refreshTabButtons();
      }
    });
    this.tabButtons.clear();
    int pw = this.panelWidth();
    for (int i = 0; i < this.tabs.length; i++) {
      final int idx = i;
      SideTabButton b = new SideTabButton(PANEL_X, PANEL_TOP + idx * ROW_H, pw, ROW_H,
          Component.translatable(this.tabs[i].getTitleKey()), () -> this.selectTab(idx));
      this.tabButtons.add(b);
      this.addRenderableWidget(b);
    }
    LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
    this.resetButton = Button.builder(Component.translatable("config.openorigins.reset"),
        button -> {
          OpenOriginsConfig.getInstance().resetAll();
          HotkeyFeatures.reloadScanKey();
          if (this.minecraft != null) {
            this.minecraft.gui.setScreen(new ConfigScreen(this.parent));
          }
        }).width(120).build();
    footer.addChild(this.resetButton);
    this.doneButton =
        Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(200).build();
    footer.addChild(this.doneButton);
    this.layout.visitWidgets(button -> {
      button.setTabOrderGroup(1);
      this.addRenderableWidget(button);
    });
    this.selectTab(0);
    try {
      if (System.currentTimeMillis() - savedAtMs < SCROLL_KEEP_MS
          && savedTab >= 0 && savedTab < this.pages.length
          && this.pages[savedTab] instanceof ConfigTabPage page) {
        this.selectTab(savedTab);
        page.list().setScrollAmount(savedScroll);
      }
    } catch (Throwable ignored) {
    }
    this.repositionElements();
  }

  protected void repositionElements() {
    int panelW = this.panelWidth();
    int gap = 8, margin = 10;
    int tabW = Math.max(UiConstants.TAB_W_MIN,
        Math.min(UiConstants.TAB_W_MAX, this.width - (margin + panelW + gap) - margin));
    int totalW = panelW + gap + tabW;
    int startX = Math.max(margin, (this.width - totalW) / 2);
    for (int i = 0; i < this.tabButtons.size(); i++) {
      SideTabButton b = this.tabButtons.get(i);
      b.setX(startX);
      b.setY(PANEL_TOP + i * ROW_H);
    }
    int contentX = startX + panelW + gap;
    int tabAreaTop = PANEL_TOP;
    this.tabManager.setTabArea(new ScreenRectangle(contentX, tabAreaTop, tabW,
        this.height - this.layout.getFooterHeight() - tabAreaTop));
    this.layout.setHeaderHeight(tabAreaTop);
    this.layout.arrangeElements();
    // Footer buttons keep their sizes; center the pair over the content column.
    try {
      if (this.resetButton != null && this.doneButton != null) {
        int pairL = Math.min(this.resetButton.getX(), this.doneButton.getX());
        int pairR = Math.max(this.resetButton.getX() + this.resetButton.getWidth(),
            this.doneButton.getX() + this.doneButton.getWidth());
        int dx = (contentX + tabW / 2) - (pairL + pairR) / 2;
        this.resetButton.setX(this.resetButton.getX() + dx);
        this.doneButton.setX(this.doneButton.getX() + dx);
      }
    } catch (Throwable ignored) {
    }
  }

  @Override
  public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
    if (com.openorigins.client.HotkeyFeatures.isScanCaptureArmed()
        || com.openorigins.client.HotkeyFeatures.isCapturingAny()) {
      if (com.openorigins.client.HotkeyFeatures.isScanCaptureArmed()) {
        com.openorigins.client.HotkeyFeatures.confirmScanCapture(event.key(), com.openorigins.client.util.VersionCompat.auxCode(event));
      } else {
        com.openorigins.client.HotkeyFeatures.confirmCapture(event.key(), com.openorigins.client.util.VersionCompat.auxCode(event));
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
  public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX,
      int mouseY, float partialTick) {
    this.drawTabHighlight(graphics, mouseX, mouseY);
    super.extractRenderState(graphics, mouseX, mouseY, partialTick);
  }

  /** Gliding sidebar highlight matching the row list, no gaps. */
  private void drawTabHighlight(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX,
      int mouseY) {
    long now = System.nanoTime();
    if (this.tabHiLastNs == 0) this.tabHiLastNs = now;
    double dt = Math.min(0.05, Math.max(0, (now - this.tabHiLastNs) / 1_000_000_000.0));
    this.tabHiLastNs = now;
    SideTabButton target = null;
    SideTabButton hoveredBtn = null;
    try {
      for (int i = 0; i < this.tabButtons.size() && i < this.tabs.length; i++) {
        SideTabButton b = this.tabButtons.get(i);
        if (b.isHovered()) {
          hoveredBtn = b;
          break;
        }
      }
      if (this.highlightTab != null) {
        for (int i = 0; i < this.tabButtons.size() && i < this.tabs.length; i++) {
          if (this.tabs[i] == this.highlightTab) {
            target = this.tabButtons.get(i);
            break;
          }
        }
      }
    } catch (Throwable ignored) {
    }
    double wantY = 0;
    double wantH = 0;
    double wantA = 0;
    int tx = 0;
    int tw = 0;
    if (target != null) {
      try {
        wantY = target.getY();
        wantH = target.getHeight();
        tx = target.getX();
        tw = target.getWidth();
        wantA = 1;
      } catch (Throwable ignored) {
      }
    }
    if (tabHiH <= 0 && wantH > 0 && tabHiAlpha <= 0) {
      tabHiY = wantY;
      tabHiH = wantH;
    }
    double k = Math.min(1.0, dt * TAB_HI_SPEED);
    if (target != null) {
      tabHiY += (wantY - tabHiY) * k;
      tabHiH += (wantH - tabHiH) * k;
    }
    tabHiAlpha += (wantA - tabHiAlpha) * Math.min(1.0, dt * TAB_HI_SPEED * 0.8);
    if (tabHiAlpha >= 0.02 && tabHiH >= 2 && tw > 0) {
      int a = (int) Math.round(72 * Math.max(0, Math.min(1, tabHiAlpha)));
      graphics.fill(tx, (int) Math.round(tabHiY), tx + tw, (int) Math.round(tabHiY + tabHiH),
          (a << 24) | 0xA0A0A0);
    }
    try {
      if (hoveredBtn != null && hoveredBtn != target) {
        graphics.fill(hoveredBtn.getX(), hoveredBtn.getY(),
            hoveredBtn.getX() + hoveredBtn.getWidth(),
            hoveredBtn.getY() + hoveredBtn.getHeight(), (72 << 24) | 0xA0A0A0);
      }
    } catch (Throwable ignored) {
    }
    try {
      SideTabButton sel = null;
      if (this.highlightTab != null) {
        for (int i = 0; i < this.tabButtons.size() && i < this.tabs.length; i++) {
          if (this.tabs[i] == this.highlightTab) {
            sel = this.tabButtons.get(i);
            break;
          }
        }
      }
      if (sel != null) {
        double sy = sel.getY();
        double sh = sel.getHeight();
        if (tabSelH <= 0) {
          tabSelY = sy;
          tabSelH = sh;
        }
        tabSelY += (sy - tabSelY) * k;
        tabSelH += (sh - tabSelH) * k;
        graphics.fill(sel.getX(), (int) Math.round(tabSelY), sel.getX() + 2,
            (int) Math.round(tabSelY + tabSelH), 0xFFFFAA00);
      }
    } catch (Throwable ignored) {
    }
  }

  @Override
  public void tick() {
    super.tick();
    try {
      if (this.resetButton != null) {
        this.resetButton.active =
            !com.openorigins.client.OpenOriginsConfig.getInstance().isDefault();
      }
    } catch (Throwable ignored) {
    }
  }

  private static int savedTab = 0;
  private static double savedScroll = 0;
  private static long savedAtMs = 0;
  private static final long SCROLL_KEEP_MS = 15000;

  public void refreshLists() {
    try {
      for (Tab page : this.pages) {
        if (page instanceof ConfigTabPage p) {
          p.list().refreshEntries();
        }
      }
    } catch (Throwable ignored) {
    }
  }

  @Override
  public void onClose() {
    try {
      savedTab = this.activeTab;
      if (this.pages != null && this.activeTab >= 0 && this.activeTab < this.pages.length
          && this.pages[this.activeTab] instanceof ConfigTabPage page) {
        savedScroll = page.list().scrollAmount();
      } else {
        savedScroll = 0;
      }
      savedAtMs = System.currentTimeMillis();
    } catch (Throwable ignored) {
    }
    com.openorigins.client.screen.FeatureList.setSpyListener(null);
    if (this.minecraft != null) {
      this.minecraft.gui.setScreen(this.parent);
    }
  }
}
