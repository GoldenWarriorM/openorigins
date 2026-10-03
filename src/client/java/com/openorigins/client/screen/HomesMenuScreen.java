package com.openorigins.client.screen;

import com.openorigins.client.util.HomeListFetcher;
import com.openorigins.client.util.ServerPackIcons;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

/**
 * Trimmed homes management: grid of house icons from the server pack,
 * left-click teleports ({@code /home <name>}), right-click deletes
 * ({@code /delhome <name>}).
 */
public class HomesMenuScreen extends Screen {
  private static final int COLS = 5;
  private static final int ICON = 40;
  private static final int GAP = 8;
  private static final int LABEL_H = 12;

  private List<String> homes;
  private boolean failed;
  private static boolean fetchArmed;
  private static long fetchDeadline;
  private static final java.util.Set<String> AUTO_TRIED =
      java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

  private static void startFetch() {
    Minecraft mc = Minecraft.getInstance();
    if (mc != null && mc.player != null) {
      fetchArmed = true;
      fetchDeadline = System.currentTimeMillis() + 10000;
      mc.player.connection.sendCommand("homes");
    }
  }

  public HomesMenuScreen() {
    super(Component.translatable("menu.openorigins.homes.title"));
  }

  @Override
  protected void init() {
    if (this.homes == null && !this.failed) {
      if (this.minecraft != null && this.minecraft.player != null) {
        HomeListFetcher.fetchHomes(names -> {
          if (this.minecraft == null || this.minecraft.gui.screen() != this) {
            return;
          }
          this.homes = new ArrayList<>(names);
          this.layoutWidgets();
          this.autoFetchMissing();
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
  }

  /** Silently pulls missing details via the server menu, once per home. */
  private void autoFetchMissing() {
    if (this.homes == null || this.homes.isEmpty() || fetchArmed) {
      return;
    }
    boolean need = false;
    for (String n : this.homes) {
      String k = com.openorigins.client.util.HomeInfoCache.key(n);
      if (!com.openorigins.client.util.HomeInfoCache.has(n) && AUTO_TRIED.add(k)) {
        need = true;
      }
    }
    if (need) {
      startFetch();
    }
  }

  /** Called when home lore is captured; returns to our menu if a fetch is armed. */
  public static void onCaptured() {
    if (!fetchArmed || System.currentTimeMillis() > fetchDeadline) {
      fetchArmed = false;
      return;
    }
    fetchArmed = false;
    Minecraft mc = Minecraft.getInstance();
    if (mc != null) {
      mc.execute(() -> {
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof HomesMenuScreen)) {
          mc.gui.setScreen(new HomesMenuScreen());
        }
      });
    }
  }

  private void refresh() {
    this.homes = null;
    this.failed = false;
    this.init();
  }

  private void layoutWidgets() {
    this.clearWidgets();
    int cx = this.width / 2;
    if (this.homes == null) {
      return;
    }
    java.util.Map<String, String> byKey = new java.util.HashMap<>();
    for (String n : this.homes) {
      byKey.put(com.openorigins.client.util.HomeInfoCache.key(n), n);
    }
    java.util.List<String> cells = new java.util.ArrayList<>();
    java.util.Set<String> covered = new java.util.HashSet<>();
    for (String e : com.openorigins.client.util.HomeInfoCache.layout()) {
      if (e.startsWith("home:")) {
        String k = e.substring(5);
        String real = byKey.get(k);
        if (real != null) {
          cells.add("home:" + real);
          covered.add(k);
        }
      } else {
        cells.add("locked");
      }
    }
    for (String n : this.homes) {
      if (!covered.contains(com.openorigins.client.util.HomeInfoCache.key(n))) {
        cells.add("home:" + n);
      }
    }
    java.util.Map<String, ServerPackIcons.LoadedIcon> icons = new java.util.HashMap<>();
    for (String c : cells) {
      if (c.startsWith("home:")) {
        String n = c.substring(5);
        ServerPackIcons.get(com.openorigins.client.util.HomeInfoCache.iconPath(n))
            .ifPresent(li -> icons.put(n, li));
      }
    }
    ServerPackIcons.get(com.openorigins.client.util.HomeInfoCache.PLUS_ICON)
        .ifPresent(li -> icons.put("\0plus", li));
    int cellW = ICON + GAP;
    int rows = Math.max(1, (cells.size() + COLS - 1) / COLS);
    int gridW = COLS * cellW - GAP;
    int gridH = rows * (ICON + LABEL_H + GAP) - GAP;
    int y0 = Math.max(44, (this.height - gridH) / 2 - 10);
    for (int i = 0; i < cells.size(); i++) {
      String cell = cells.get(i);
      int col = i % COLS;
      int row = i / COLS;
      int x = cx - gridW / 2 + col * cellW;
      int y = y0 + row * (ICON + LABEL_H + GAP);
      if (cell.startsWith("home:")) {
        String name = cell.substring(5);
        ServerPackIcons.LoadedIcon li = icons.get(name);
        Component tip = com.openorigins.client.util.HomeInfoCache.tooltip(name);
        if (li != null) {
          this.addRenderableWidget(new HomeButton(x, y, ICON,
              Component.literal(name), li.textureId(), li.width(), li.height(), name, tip,
              () -> teleport(name), () -> delete(name)));
        } else {
          this.addRenderableWidget(Button.builder(Component.literal(name), b -> teleport(name))
              .bounds(x, y, ICON, 20).build());
        }
      } else {
        ServerPackIcons.LoadedIcon li = icons.get("\0plus");
        Component tip = Component.translatable("menu.openorigins.homes.new_hint");
        if (li != null) {
          this.addRenderableWidget(new HomeButton(x, y, ICON,
              Component.translatable("menu.openorigins.homes.new"), li.textureId(),
              li.width(), li.height(), "+", tip,
              () -> openSetHome(), () -> openSetHome()));
        } else {
          this.addRenderableWidget(Button.builder(
              Component.translatable("menu.openorigins.homes.new"), b -> openSetHome())
              .bounds(x, y, ICON, 20).build());
        }
      }
    }
    int btnY = Math.min(this.height - 30, y0 + gridH + 12);
    this.addRenderableWidget(Button.builder(
        Component.translatable("menu.openorigins.homes.sethome"), b -> openSetHome())
        .bounds(cx - 125, btnY, 120, 20).build());
    this.addRenderableWidget(Button.builder(
        Component.translatable("menu.openorigins.homes.details"), b -> {
          if (this.minecraft != null && this.minecraft.player != null) {
            startFetch();
          }
        }).bounds(cx + 5, btnY, 120, 20).build());
  }

  private void openSetHome() {
    if (this.minecraft != null) {
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
    }
    this.refresh();
  }

  @Override
  public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor extractor,
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

  private static final class HomeButton extends AbstractButton {
    private final Identifier textureId;
    private final int texW;
    private final int texH;
    private final String homeName;
    private final Runnable leftAction;
    private final Runnable rightAction;

    HomeButton(int x, int y, int size, Component message, Identifier textureId,
        int texW, int texH, String homeName, Component tooltip,
        Runnable leftAction, Runnable rightAction) {
      super(x, y, size, size, message);
      this.textureId = textureId;
      this.texW = texW;
      this.texH = texH;
      this.homeName = homeName;
      this.leftAction = leftAction;
      this.rightAction = rightAction;
      this.setTooltip(Tooltip.create(tooltip));
    }

    @Override
    public void onPress(InputWithModifiers input) {
      this.leftAction.run();
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
      if (event.button() == 1) {
        this.rightAction.run();
      } else {
        super.onClick(event, doubleClick);
      }
    }

    @Override
    protected boolean isValidClickButton(net.minecraft.client.input.MouseButtonInfo buttonInfo) {
      return buttonInfo.button() == 0 || buttonInfo.button() == 1;
    }

    @Override
    protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
      this.defaultButtonNarrationText(output);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      float aspect = this.texW / (float) this.texH;
      int dw = this.getWidth();
      int dh = this.getHeight();
      if (aspect >= 1.0F) {
        dh = Math.max(1, Math.round(dw / aspect));
      } else {
        dw = Math.max(1, Math.round(dh * aspect));
      }
      int dx = this.getX() + (this.getWidth() - dw) / 2;
      int dy = this.getY() + (this.getHeight() - dh) / 2;
      graphics.blit(RenderPipelines.GUI_TEXTURED, this.textureId,
          dx, dy, 0.0F, 0.0F, dw, dh, this.texW, this.texH, this.texW, this.texH);
      if (this.isHoveredOrFocused()) {
        int x0 = this.getX() - 1;
        int y0 = this.getY() - 1;
        int x1 = this.getX() + this.getWidth() + 1;
        int y1 = this.getY() + this.getHeight() + 1;
        graphics.fill(x0, y0, x1, y0 + 1, 0xFFFFFFFF);
        graphics.fill(x0, y1 - 1, x1, y1, 0xFFFFFFFF);
        graphics.fill(x0, y0, x0 + 1, y1, 0xFFFFFFFF);
        graphics.fill(x1 - 1, y0, x1, y1, 0xFFFFFFFF);
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc != null) {
        String s = this.homeName;
        int maxW = this.getWidth() + 16;
        if (mc.font.width(s) > maxW) {
          s = mc.font.plainSubstrByWidth(s, maxW - 3) + "...";
        }
        int sw = mc.font.width(s);
        graphics.text(mc.font, s, this.getX() + this.getWidth() / 2 - sw / 2,
            this.getY() + this.getHeight() + 2, 0xFFFFFFFF);
      }
    }
  }
}
