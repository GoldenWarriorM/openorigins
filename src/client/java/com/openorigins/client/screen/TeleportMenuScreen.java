package com.openorigins.client.screen;

import com.openorigins.client.util.HomeFetch;
import com.openorigins.client.util.HomeInfoCache;
import com.openorigins.client.util.HomeListFetcher;
import com.openorigins.client.util.OpenWorldCache;
import com.openorigins.client.util.ServerPackIcons;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Radial teleport menu with icons loaded at runtime from the server resource pack,
 * plus a homes panel docked to the right edge (3 columns, up to 15).
 * The radial part renders instantly; the homes section fills in async.
 */
public class TeleportMenuScreen extends Screen implements HomePanelHost {
  private record Entry(String labelKey, String command, double angleDeg, String packPath, int yOff) {}

  private static final Entry[] ENTRIES = {
      new Entry("menu.openorigins.teleport.blue", "balloon blue-balloon", -90.0,
          "textures/custom_ui/transport_menu/button_balloon_blue.png", 0),
      new Entry("menu.openorigins.teleport.yellow", "balloon yellow-balloon", 0.0,
          "textures/custom_ui/transport_menu/button_balloon_yellow.png", 0),
      new Entry("menu.openorigins.teleport.green", "balloon green-balloon", 180.0,
          "textures/custom_ui/transport_menu/button_balloon_green.png", 0),
      new Entry("menu.openorigins.teleport.realm", "realm tp", 115.0,
          "textures/custom_ui/transport_menu/button_homes.png", 16),
      new Entry("menu.openorigins.teleport.spawn", "spawn", 65.0,
          "textures/custom_ui/transport_menu/button_origin_isles.png", 16),
  };
  private static final int ICON_SIZE = 56;
  private static final int RADIUS = 95;
  static final int HOME_COLS = 3;
  static final int HOME_ICON = 40;
  static final int HOME_GAP = 8;
  static final int HOME_LABEL_H = 12;
  static final int HOME_MAX = 15;

  private List<String> homes;
  private int panelX;
  private int panelY;
  private int panelCount;
  private int worldPanelX;
  private int worldPanelY;
  private int worldPanelCount;
  private long retryAt;
  private int retries;
  private static boolean navigatorProbed;

  /** Radial center: screen center unless a side panel would overlap it. */
  private double radialCx() {
    double cx = this.width / 2.0;
    int panelW = homePanelWidth();
    if (panelW > 0) {
      double panelLeft = this.width - panelW - 10;
      double needRight = RADIUS + 34;
      if (cx + needRight > panelLeft) {
        cx = Math.max(RADIUS + 40, panelLeft - needRight - 6);
      }
    }
    int worldW = worldPanelWidth();
    if (worldW > 0) {
      double panelRight = 10 + worldW;
      double needLeft = RADIUS + 34;
      if (cx - needLeft < panelRight) {
        cx = Math.min(this.width - RADIUS - 40, panelRight + needLeft + 6);
      }
    }
    return cx;
  }

  private int homePanelWidth() {
    List<HomeInfoCache.Cell> cells = this.homes == null ? List.of() : HomeInfoCache.cellsFor(this.homes);
    if (cells.isEmpty()) {
      return 0;
    }
    int cols = Math.min(HOME_COLS, (cells.size() + 4) / 5);
    return cols * (HOME_ICON + HOME_GAP) - HOME_GAP;
  }

  private int worldPanelWidth() {
    int n = com.openorigins.client.util.OpenWorldCache.worlds().size();
    if (n == 0) {
      return 0;
    }
    int cols = Math.min(2, (n + 2) / 3);
    return cols * (HOME_ICON + HOME_GAP) - HOME_GAP;
  }

  public TeleportMenuScreen() {
    super(Component.translatable("menu.openorigins.teleport.title"));
  }

  @Override
  protected void init() {
    HomeInfoCache.backfillResets();
    if (!navigatorProbed && this.minecraft != null && this.minecraft.player != null) {
      navigatorProbed = true;
      HomeListFetcher.fetchRaw("/navigator ", false, subs ->
          org.slf4j.LoggerFactory.getLogger("openorigins")
              .info("[openworld] navigator subs: {}", subs));
    }
    if (this.homes == null && this.minecraft != null && this.minecraft.player != null) {
      this.homes = new ArrayList<>(HomeInfoCache.names());
    }
    this.buildAll();
    HomeFetch.ensureFresh();
  }

  @Override
  public void rebuildHomeWidgets() {
    this.clearWidgets();
    this.buildAll();
  }

  @Override
  public void forgetHome(String name) {
    if (this.homes != null) {
      this.homes.removeIf(n -> n.equalsIgnoreCase(name));
    }
    this.clearWidgets();
    this.buildAll();
  }

  @Override
  public void addHome(String name) {
    if (this.homes == null) {
      this.homes = new ArrayList<>();
    }
    boolean known = false;
    for (String n : this.homes) {
      if (n.equalsIgnoreCase(name)) {
        known = true;
        break;
      }
    }
    if (!known) {
      this.homes.add(name);
    }
    this.clearWidgets();
    this.buildAll();
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

  private void buildAll() {
    double cx = this.radialCx();
    double cy = this.height / 2.0;
    for (Entry entry : ENTRIES) {
      double angle = Math.toRadians(entry.angleDeg);
      int x = (int) Math.round(cx + Math.cos(angle) * RADIUS) - ICON_SIZE / 2;
      int y = (int) Math.round(cy + Math.sin(angle) * RADIUS) - ICON_SIZE / 2 + entry.yOff;
      Optional<ServerPackIcons.LoadedIcon> icon = ServerPackIcons.get(entry.packPath);
      if (icon.isPresent()) {
        ServerPackIcons.LoadedIcon li = icon.get();
        this.addRenderableWidget(new PackIconButton(x, y, ICON_SIZE,
            Component.translatable(entry.labelKey), li.textureId(), li.width(), li.height(), () -> {
              if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.connection.sendCommand(entry.command);
              }
              this.onClose();
            }));
      } else {
        this.addRenderableWidget(Button.builder(Component.translatable(entry.labelKey), button -> {
          if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.connection.sendCommand(entry.command);
          }
          this.onClose();
        }).bounds(x, y, 96, 20).build());
      }
    }
    this.buildHomePanel();
    this.buildWorldPanel();
  }

  private void buildHomePanel() {
    this.panelCount = 0;
    java.util.List<HomeInfoCache.Cell> cells;
    if (this.homes == null) {
      return;
    } else if (this.homes.isEmpty()) {
      cells = HomeInfoCache.cellsFor(this.homes);
      if (cells.isEmpty()) {
        cells = new ArrayList<>(List.of(new HomeInfoCache.Cell("", "", true)));
      }
    } else {
      cells = HomeInfoCache.cellsFor(this.homes);
    }
    int manual = com.openorigins.client.OpenOriginsConfig.getInstance().getHomeCapacity();
    int target = manual > 0 ? Math.min(manual, 15)
        : HomeInfoCache.capacity() > 0 ? HomeInfoCache.capacity()
        : Math.max(5, cells.size());
    while (cells.size() < target && cells.size() < 15) {
      cells.add(new HomeInfoCache.Cell("", "", true));
    }
    if (cells.isEmpty()) {
      return;
    }
    java.util.Map<String, ServerPackIcons.LoadedIcon> icons = new java.util.HashMap<>();
    for (HomeInfoCache.Cell c : cells) {
      if (!c.plus()) {
        ServerPackIcons.get(HomeInfoCache.iconPath(c.label())).ifPresent(li -> icons.put(c.label(), li));
      }
    }
    ServerPackIcons.get(HomeInfoCache.PLUS_ICON).ifPresent(li -> icons.put("\0plus", li));
    int cellW = HOME_ICON + HOME_GAP;
    int cols = Math.min(HOME_COLS, (cells.size() + 4) / 5);
    int rows = Math.min(5, cells.size());
    int panelW = cols * cellW - HOME_GAP;
    int panelH = rows * (HOME_ICON + HOME_LABEL_H + HOME_GAP) - HOME_GAP;
    this.panelX = this.width - panelW - 10;
    this.panelY = Math.max(48, (this.height - panelH) / 2);
    this.panelCount = cells.size();
    com.openorigins.client.util.DebugLog.info("[homes] teleport cells: {}",
        cells.stream().map(c -> c.plus() ? "+" : c.label() + "->" + c.command())
            .collect(java.util.stream.Collectors.joining(", ")));
    for (int i = 0; i < cells.size(); i++) {
      HomeInfoCache.Cell cell = cells.get(i);
      int col = i / 5;
      int row = i % 5;
      int x = this.panelX + col * cellW;
      int y = this.panelY + row * (HOME_ICON + HOME_LABEL_H + HOME_GAP);
      if (!cell.plus()) {
        String name = cell.label();
        String cmd = cell.command();
        this.addRenderableWidget(HomesMenuScreen.makeHomeCell(this.minecraft, x, y, HOME_ICON, name,
            () -> teleportHome(cmd), () -> deleteHome(cmd)));
      } else {
        this.addRenderableWidget(HomesMenuScreen.makePlusCell(x, y, HOME_ICON, () -> {
          if (this.minecraft != null) {
            SetHomeScreen.returnTo = TeleportMenuScreen::new;
            this.minecraft.gui.setScreen(new SetHomeScreen());
          }
        }));
      }
    }
  }

  private void buildWorldPanel() {
    this.worldPanelCount = 0;
    java.util.List<com.openorigins.client.util.OpenWorldCache.WorldEntry> worlds =
        com.openorigins.client.util.OpenWorldCache.worlds();
    if (worlds.isEmpty()) {
      return;
    }
    com.openorigins.client.util.OpenWorldCache.ensureWorlds();
    int cellW = HOME_ICON + HOME_GAP;
    int cols = Math.min(2, (worlds.size() + 2) / 3);
    int rows = Math.min(3, worlds.size());
    int panelW = cols * cellW - HOME_GAP;
    int panelH = rows * (HOME_ICON + HOME_LABEL_H + HOME_GAP) - HOME_GAP;
    this.worldPanelX = 10;
    this.worldPanelY = Math.max(48, (this.height - panelH) / 2);
    this.worldPanelCount = worlds.size();
    for (int i = 0; i < worlds.size(); i++) {
      com.openorigins.client.util.OpenWorldCache.WorldEntry w = worlds.get(i);
      int col = i / 3;
      int row = i % 3;
      int x = this.worldPanelX + col * cellW;
      int y = this.worldPanelY + row * (HOME_ICON + HOME_LABEL_H + HOME_GAP);
      String shortName = w.name().replace(" Open World", "");
      String cd = com.openorigins.client.util.OpenWorldCache.countdown(w.color());
      long left = com.openorigins.client.util.OpenWorldCache.remaining(w.color());
      int subColor = 0xFFAAAAAA;
      if (left > 0 && left < 30 * 60000L) {
        subColor = 0xFFFF5555;
      } else if (left > 0 && left < 60 * 60000L) {
        subColor = 0xFFFFAA00;
      }
      String sub = cd != null ? cd : "...";
      net.minecraft.network.chat.Component tip = net.minecraft.network.chat.Component.literal(w.name());
      if (cd != null) {
        tip = tip.copy().append(net.minecraft.network.chat.Component.literal("\n"))
            .append(net.minecraft.network.chat.Component.translatable(
                "menu.openorigins.homes.reset", cd).withStyle(net.minecraft.ChatFormatting.GRAY));
      }
      final String color = w.color();
      Optional<ServerPackIcons.LoadedIcon> icon = ServerPackIcons.get(
          "textures/custom/ui/homes/open_world_" + color + ".png");
      if (icon.isPresent()) {
        ServerPackIcons.LoadedIcon li = icon.get();
        this.addRenderableWidget(new HomeIconButton(x, y, HOME_ICON,
            Component.literal(shortName), li.textureId(), li.width(), li.height(),
            false, shortName, sub, subColor, tip,
            () -> {
              com.openorigins.client.util.OpenWorldCache.teleportTo(color);
              this.onClose();
            },
            () -> {
              com.openorigins.client.util.OpenWorldCache.teleportTo(color);
              this.onClose();
            }));
      }
    }
  }

  private void teleportHome(String name) {
    if (this.minecraft != null && this.minecraft.player != null) {
      this.minecraft.player.connection.sendCommand("home " + name);
    }
    this.onClose();
  }

  private void deleteHome(String name) {
    if (this.minecraft != null && this.minecraft.player != null) {
      this.minecraft.player.connection.sendCommand("delhome " + name);
      com.openorigins.client.util.HomeListFetcher.noteMutation();
    }
    com.openorigins.client.util.HomeFetch.requestIdleRefresh();
    if (this.minecraft != null) {
      this.minecraft.gui.setScreen(new TeleportMenuScreen());
    }
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor extractor,
      int mouseX, int mouseY, float partialTick) {
    extractor.fill(0, 0, this.width, this.height, 0xA0000000);
    super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    int titleWidth = this.font.width(this.title);
    double tcx = this.radialCx();
    extractor.text(this.font, this.title, (int) tcx - titleWidth / 2, 20, 0xFFFFFFFF);
    if (this.panelCount > 0) {
      Component header = Component.translatable("menu.openorigins.homes.title");
      int hw = this.font.width(header);
      int cols = Math.min(HOME_COLS, (this.panelCount + 4) / 5);
      int gridW = cols * (HOME_ICON + HOME_GAP) - HOME_GAP;
      extractor.text(this.font, header, this.panelX + gridW / 2 - hw / 2, this.panelY - 16, 0xFFFFFFFF);
    }
    if (this.worldPanelCount > 0) {
      Component wheader = Component.translatable("menu.openorigins.worlds.title");
      int hww = this.font.width(wheader);
      int wcols = Math.min(2, (this.worldPanelCount + 2) / 3);
      int wgridW = wcols * (HOME_ICON + HOME_GAP) - HOME_GAP;
      extractor.text(this.font, wheader, this.worldPanelX + wgridW / 2 - hww / 2,
          this.worldPanelY - 16, 0xFFFFFFFF);
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

  private static final class PackIconButton extends AbstractButton {
    private final Identifier textureId;
    private final int texW;
    private final int texH;
    private final Runnable action;

    PackIconButton(int x, int y, int size, Component message, Identifier textureId,
        int texW, int texH, Runnable action) {
      super(x, y, size, size, message);
      this.textureId = textureId;
      this.texW = texW;
      this.texH = texH;
      this.action = action;
      this.setTooltip(Tooltip.create(message));
    }

    @Override
    public void onPress(InputWithModifiers input) {
      this.action.run();
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
        graphics.fill(x0, y0, x0 + 1, y1, 0xFFFFFFFF);
        graphics.fill(x1 - 1, y0, x1, y1, 0xFFFFFFFF);
        graphics.fill(x0, y1 - 1, x1, y1, 0xFFFFFFFF);
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc != null) {
        String s = this.getMessage().getString();
        int tw = mc.font.width(s);
        graphics.text(mc.font, s, this.getX() + this.getWidth() / 2 - tw / 2,
            this.getY() + this.getHeight() + 3, 0xFFFFFFFF);
      }
    }
  }
}
