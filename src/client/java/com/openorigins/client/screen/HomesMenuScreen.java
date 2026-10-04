package com.openorigins.client.screen;

import com.openorigins.client.util.HomeFetch;
import com.openorigins.client.util.HomeInfoCache;
import com.openorigins.client.util.HomeListFetcher;
import com.openorigins.client.util.ServerPackIcons;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

/**
 * Trimmed homes management: grid of house icons from the server pack,
 * left-click teleports ({@code /home <name>}), right-click deletes
 * ({@code /delhome <name>}). Free slots show a plus opening name input.
 */
public class HomesMenuScreen extends Screen implements HomePanelHost {
  static final int COLS = 5;
  static final int ICON = 40;
  static final int GAP = 8;
  static final int LABEL_H = 12;

  private List<String> homes;
  private boolean failed;
  private long retryAt;
  private int retries;

  public HomesMenuScreen() {
    super(Component.translatable("menu.openorigins.homes.title"));
  }

  @Override
  protected void init() {
    HomeInfoCache.backfillResets();
    if (this.homes == null && !this.failed) {
      List<String> cached = HomeInfoCache.names();
      if (!cached.isEmpty()) {
        this.homes = new ArrayList<>(cached);
      }
      if (this.minecraft != null && this.minecraft.player != null) {
        HomeListFetcher.fetchHomes(names -> {
          if (this.minecraft == null || this.minecraft.gui.screen() != this) {
            return;
          }
          if (names.isEmpty() && this.homes != null && !this.homes.isEmpty()
              && !HomeListFetcher.trustEmpty() && this.retries < 5) {
            this.retries++;
            this.retryAt = System.currentTimeMillis() + 3000;
            return;
          }
          this.retries = 0;
          boolean changed = HomeInfoCache.setNames(names);
          if (changed || this.homes == null) {
            this.homes = new ArrayList<>(names);
            this.layoutWidgets();
          }
          HomeFetch.consumeRefetch();
        });
      } else {
        this.failed = true;
      }
    }
    this.layoutWidgets();
  }

  @Override
  public void tick() {
    super.tick();
    if (this.retryAt > 0 && System.currentTimeMillis() >= this.retryAt) {
      this.retryAt = 0;
      this.homes = null;
      this.init();
    }
  }

  @Override
  public void rebuildHomeWidgets() {
    this.layoutWidgets();
  }

  @Override
  public void forgetHome(String name) {
    if (this.homes != null) {
      this.homes.removeIf(n -> n.equalsIgnoreCase(name));
    }
    this.layoutWidgets();
  }

  void refresh() {
    this.homes = null;
    this.failed = false;
    this.init();
  }

  void layoutWidgets() {
    this.clearWidgets();
    int cx = this.width / 2;
    if (this.homes == null) {
      return;
    }
    java.util.List<HomeInfoCache.Cell> cells = HomeInfoCache.cellsFor(this.homes);
    if (cells.isEmpty() && this.homes.isEmpty()) {
      cells = new ArrayList<>(List.of(new HomeInfoCache.Cell("", "", true)));
    }
    int cellW = ICON + GAP;
    int rows = Math.max(1, (cells.size() + COLS - 1) / COLS);
    int gridW = COLS * cellW - GAP;
    int gridH = rows * (ICON + LABEL_H + GAP) - GAP;
    int y0 = Math.max(44, (this.height - gridH) / 2 - 10);
    for (int i = 0; i < cells.size(); i++) {
      HomeInfoCache.Cell cell = cells.get(i);
      int col = i % COLS;
      int row = i / COLS;
      int x = cx - gridW / 2 + col * cellW;
      int y = y0 + row * (ICON + LABEL_H + GAP);
      if (!cell.plus()) {
        this.addRenderableWidget(makeHomeCell(this.minecraft, x, y, ICON, cell.label(),
            () -> teleport(cell.command()), () -> delete(cell.command())));
      } else {
        this.addRenderableWidget(makePlusCell(x, y, ICON,
            () -> openSetHome(HomesMenuScreen::new)));
      }
    }
    int btnY = Math.min(this.height - 30, y0 + gridH + 12);
    org.slf4j.LoggerFactory.getLogger("openorigins").info("[homes] panel cells: {}",
        cells.stream().map(c -> c.plus() ? "+" : c.label() + "->" + c.command())
            .collect(java.util.stream.Collectors.joining(", ")));
    this.addRenderableWidget(Button.builder(
        Component.translatable("menu.openorigins.homes.details"), b -> HomeFetch.start(HomesMenuScreen::new))
        .bounds(cx - 60, btnY, 120, 20).build());
  }

  static net.minecraft.client.gui.components.AbstractButton makeHomeCell(
      net.minecraft.client.Minecraft mc, int x, int y, int size, String name,
      Runnable teleport, Runnable delete) {
    ServerPackIcons.LoadedIcon li =
        ServerPackIcons.get(HomeInfoCache.iconPath(name)).orElse(null);
    Component tip = HomeInfoCache.tooltip(name);
    if (li != null) {
      return new HomeIconButton(x, y, size,
          Component.literal(name), li.textureId(), li.width(), li.height(), false, name, tip,
          teleport, delete);
    } else if (mc != null && mc.player != null) {
      Identifier skin = mc.player.getSkin().body().texturePath();
      return new HomeIconButton(x, y, size,
          Component.literal(name), skin, 64, 64, true, name, tip, teleport, delete);
    } else {
      return Button.builder(Component.literal(name), b -> teleport.run()).bounds(x, y, size, 20).build();
    }
  }

  static net.minecraft.client.gui.components.AbstractButton makePlusCell(
      int x, int y, int size, Runnable openInput) {
    ServerPackIcons.LoadedIcon li = ServerPackIcons.get(HomeInfoCache.PLUS_ICON).orElse(null);
    Component tip = Component.translatable("menu.openorigins.homes.new_hint");
    if (li != null) {
      return new HomeIconButton(x, y, size,
          Component.translatable("menu.openorigins.homes.new"), li.textureId(),
          li.width(), li.height(), false, "", tip, openInput, openInput);
    } else {
      return Button.builder(Component.translatable("menu.openorigins.homes.new"),
          b -> openInput.run()).bounds(x, y, size, 20).build();
    }
  }

  private static ServerPackIcons.LoadedIcon iconFor(Screen screen, String name) {
    return ServerPackIcons.get(HomeInfoCache.iconPath(name)).orElse(null);
  }

  private void openSetHome(java.util.function.Supplier<Screen> ret) {
    if (this.minecraft != null) {
      SetHomeScreen.returnTo = ret;
      this.minecraft.gui.setScreen(new SetHomeScreen());
    }
  }

  private void teleport(String name) {
    if (this.minecraft != null && this.minecraft.player != null) {
      this.minecraft.player.connection.sendCommand("home " + name);
    }
    this.onClose();
  }

  private void delete(String name) {
    if (this.minecraft != null && this.minecraft.player != null) {
      this.minecraft.player.connection.sendCommand("delhome " + name);
      com.openorigins.client.util.HomeListFetcher.noteMutation();
    }
    HomeFetch.requestIdleRefresh();
    this.refresh();
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor extractor,
      int mouseX, int mouseY, float partialTick) {
    extractor.fill(0, 0, this.width, this.height, 0xA0000000);
    super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    MutableComponent title = Component.translatable("menu.openorigins.homes.title").copy();
    if (this.homes != null) {
      title.append(Component.literal(" (" + this.homes.size() + ")").withStyle(ChatFormatting.GRAY));
    }
    int tw = this.font.width(title);
    extractor.text(this.font, title, this.width / 2 - tw / 2, 16, 0xFFFFFFFF);
    if (this.homes == null) {
      String s = Component.translatable("menu.openorigins.homes.loading").getString();
      int w = this.font.width(s);
      extractor.text(this.font, s, this.width / 2 - w / 2, this.height / 2, 0xFFAAAAAA);
    } else if (this.homes.isEmpty()) {
      String s = Component.translatable("menu.openorigins.homes.empty").getString();
      int w = this.font.width(s);
      extractor.text(this.font, s, this.width / 2 - w / 2, this.height / 2 - 20, 0xFFAAAAAA);
    }
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  public void onClose() {
    if (this.minecraft != null) {
      this.minecraft.gui.setScreen(null);
    }
  }
}
