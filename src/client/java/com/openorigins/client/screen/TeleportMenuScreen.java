package com.openorigins.client.screen;

import com.openorigins.client.util.CommandDebounce;
import com.openorigins.client.util.HomeFetch;
import com.openorigins.client.util.HomeInfoCache;
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
  private record Entry(String labelKey, String command, double angleDeg, String packPath, int yOff,
      int rgb) {}

  /** Hue of each entry: the hover fill is tinted with the colour of the
   *  teleport it belongs to. Realm takes purple, the one colour no balloon
   *  and no open world uses here. */
  private static final Entry[] ENTRIES = {
      new Entry("menu.openorigins.teleport.blue", "balloon blue-balloon", -90.0,
          "textures/custom_ui/transport_menu/button_balloon_blue.png", 0, 0x3F7BFF),
      new Entry("menu.openorigins.teleport.yellow", "balloon yellow-balloon", 0.0,
          "textures/custom_ui/transport_menu/button_balloon_yellow.png", 0, 0xFFC61A),
      new Entry("menu.openorigins.teleport.green", "balloon green-balloon", 180.0,
          "textures/custom_ui/transport_menu/button_balloon_green.png", 0, 0x46C25A),
      new Entry("menu.openorigins.teleport.realm", "realm tp", 115.0,
          "textures/custom_ui/transport_menu/button_homes.png", 16, 0xA972FF),
      new Entry("menu.openorigins.teleport.spawn", "spawn", 65.0,
          "textures/custom_ui/transport_menu/button_origin_isles.png", 16, 0xFF4A3F),
  };
  private static final int ICON_SIZE = 56;
  private static final int RADIUS = 95;
  static final int HOME_COLS = 3;
  static final int HOME_ICON = 40;
  static final int HOME_GAP = 8;
  static final int HOME_LABEL_H = 12;
  static final int HOME_MAX = 15;
  /** Inner edge of the fill, outside the centre: a sector spans up to ninety
   *  degrees here, so a wedge reaching the middle converges to a point and
   *  reads as a diamond. A band that starts away from the centre keeps the
   *  curved shape of the icon ring. */
  private static final double FILL_INNER = 56.0;
  /** Invisible click circle: starts exactly where the fill starts, so the
   *  empty middle of the ring never teleports, and ends before the side panels
   *  (their nearest edge stays outside HIT_RADIUS). */
  private static final double HIT_INNER = FILL_INNER;
  /** Cap for the click circle; the real radius shrinks to clear the panels. */
  private static final double HIT_RADIUS_MAX = RADIUS + ICON_SIZE;
  /** Alpha of the hover fill; its hue comes from the hovered entry. */
  private static final int HOVER_ALPHA = 0x3A;
  /** Pixel size of the hover fill: the sector sits on a coarse grid, so its
   *  border reads as chunky pixels instead of a smooth vector fan. */
  private static final int HOVER_PIXEL = 3;
  /** Coverage steps: quantised alpha plus a per-pixel dither, no smooth ramp. */
  private static final double ALPHA_STEPS = 4.0;
  /** Visible fade at the border, in pixels: inner arc, outer arc, radial edges.
   *  Kept short: a long ramp paints concentric alpha rings around the centre
   *  and eats the border that the rim light is supposed to sit on. */
  private static final double INNER_RAMP = 6.0;
  private static final double OUTER_RAMP = 2.0;
  private static final double EDGE_RAMP = 4.0;
  /** Rim light: brightest on the outline itself and fading inward over RIM_PX.
   *  The light has to sit on the border, otherwise the sheen reads as a stripe
   *  floating beside it; the falloff keeps the edge convex, not framed. */
  private static final double RIM_PX = 12.0;
  /** Only the outermost pixels next to a radial side lose the sheen: the light
   *  has to run along the straight edges too, or the outline reads as broken. */
  private static final double RIM_SIDE_FADE = 2.5;
  private static final int RIM_ALPHA = 0x5A;
  /** Barely visible grain: a few alpha units and a few RGB units per pixel. */
  private static final double NOISE_ALPHA = 3.0;
  private static final double NOISE_TINT = 5.0;
  /** Fade of the hover fill, both ways: the sector grows out of nothing under
   *  the cursor and melts away again when it leaves. Switching sectors
   *  restarts the fade-in. */
  private static final long HOVER_FADE_MS = 140L;

  private List<String> homes;
  private int panelX;
  private int panelY;
  private int panelCount;
  private int worldPanelX;
  private int worldPanelY;
  private int worldPanelCount;
  private long retryAt;
  private int retries;
  /** Home teleport the player clicked, held until the background fetch ends. */
  private String pendingTeleport;
  /** Radial centre in screen space, so sector clicks follow the drawn icons. */
  private double radialX;
  private double radialY;
  /** Sector the running fade belongs to; null when nothing is drawn at all. */
  private Entry fadeEntry;
  private long fadeStartMs;
  /** True while the sector fades out after the cursor left it. */
  private boolean fadingOut;
  /** Eased 0..1 fade of the hover fill for the frame being drawn. */
  private float hoverAlpha = 1.0F;
  /** Fill of the sector being drawn, hue of the hovered entry. */
  private int fillColor = (HOVER_ALPHA << 24) | 0xFF8C00;
  /** Lightened version of the same hue, used for the rim sheen. */
  private int rimTint = 0xFFD9A0;

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
    if (this.homes == null && this.minecraft != null && this.minecraft.player != null) {
      this.homes = new ArrayList<>(HomeInfoCache.names());
    }
    this.buildAll();
    HomeFetch.ensureFresh();
    // Audit 38: arm the retry clock when the panel is empty, so a failed or
    // lag-swallowed silent fetch retries itself instead of leaving the
    // placeholder grid until a manual reopen. Capped to avoid loops.
    if (HomeInfoCache.names().isEmpty()) {
      if (this.retries < 3) {
        this.retryAt = System.currentTimeMillis() + 5000L;
      }
    } else {
      this.retries = 0;
    }
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
    // The panel stays open while the press waits: the bottom line tells the
    // player what is happening, and closing happens only once it is sent.
    if (this.pendingTeleport != null
        && !com.openorigins.client.util.BackgroundMenu.hasQueuedUserCommand()) {
      this.pendingTeleport = null;
      this.onClose();
      return;
    }
    if (this.retryAt > 0 && System.currentTimeMillis() >= this.retryAt) {
      this.retryAt = 0;
      this.retries++;
      this.homes = null;
      HomeFetch.forceRefresh();
      this.init();
    }
  }

  private void buildAll() {
    double cx = this.radialCx();
    double cy = this.height / 2.0;
    this.radialX = cx;
    this.radialY = cy;
    // Added first, so the icons stay on top of the click disc.
    int disc = (int) Math.round(this.hitRadius());
    this.addRenderableWidget(new SectorCatcher((int) Math.round(cx) - disc,
        (int) Math.round(cy) - disc, disc * 2, disc * 2));
    for (Entry entry : ENTRIES) {
      double angle = Math.toRadians(entry.angleDeg);
      int x = (int) Math.round(cx + Math.cos(angle) * RADIUS) - ICON_SIZE / 2;
      int y = (int) Math.round(cy + Math.sin(angle) * RADIUS) - ICON_SIZE / 2 + entry.yOff;
      Optional<ServerPackIcons.LoadedIcon> icon = ServerPackIcons.get(entry.packPath);
      if (icon.isPresent()) {
        ServerPackIcons.LoadedIcon li = icon.get();
        this.addRenderableWidget(new PackIconButton(x, y, ICON_SIZE,
            Component.translatable(entry.labelKey), li.textureId(), li.width(), li.height(), () -> {
              this.activate(entry.command, "icon");
            }, (mx, my) -> this.entryAt(mx, my) == entry && this.sectorClicksEnabled()));
      } else {
        this.addRenderableWidget(Button.builder(Component.translatable(entry.labelKey), button -> {
          this.activate(entry.command, "icon");
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
        this.addRenderableWidget(makeHomeCell(this.minecraft, x, y, HOME_ICON, name,
            () -> teleportHome(cmd), () -> deleteHome(cmd)));
      } else {
        this.addRenderableWidget(makePlusCell(x, y, HOME_ICON, () -> {
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
      final String color = w.color();
      // Read per frame: the panel can stay open for hours and an answer can
      // land after it was built, so a captured string would freeze (or keep
      // the "..." placeholder forever).
      java.util.function.Supplier<String> sub =
          () -> countdownText(color);
      java.util.function.IntSupplier subColor = () -> countdownColor(color);
      String cd = countdownText(color);
      net.minecraft.network.chat.Component tip = net.minecraft.network.chat.Component.literal(w.name());
      if (cd != null) {
        tip = tip.copy().append(net.minecraft.network.chat.Component.literal("\n"))
            .append(net.minecraft.network.chat.Component.translatable(
                "menu.openorigins.homes.reset", cd).withStyle(net.minecraft.ChatFormatting.GRAY));
      }
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

  /** Eased 0..1 progress of the fade started at fadeStartMs. */
  private float fadeCurve() {
    float t = (System.currentTimeMillis() - this.fadeStartMs) / (float) HOVER_FADE_MS;
    t = Math.max(0.0F, Math.min(1.0F, t));
    return t * t * (3.0F - 2.0F * t);
  }

  /** Live world countdown, "..." while no timer is known. */
  private static String countdownText(String color) {
    String cd = com.openorigins.client.util.OpenWorldCache.countdown(color);
    return cd != null ? cd : "...";
  }

  /** Grey normally, orange under an hour, red under half an hour left. */
  private static int countdownColor(String color) {
    long left = com.openorigins.client.util.OpenWorldCache.remaining(color);
    if (left > 0 && left < 30 * 60000L) {
      return 0xFFFF5555;
    }
    if (left > 0 && left < 60 * 60000L) {
      return 0xFFFFAA00;
    }
    return 0xFFAAAAAA;
  }

  private void teleportHome(String name) {
    if (this.minecraft == null || this.minecraft.player == null) {
      this.onClose();
      return;
    }
    // Server home ids use underscores; display names use spaces.
    String command = "home " + name.replace(' ', '_');
    if (!CommandDebounce.accept(command)) {
      return;
    }
    if (com.openorigins.client.util.BackgroundMenu.sendOrQueueUserCommand(command)) {
      this.onClose();
    } else {
      this.pendingTeleport = command;
    }
  }

  private void deleteHome(String name) {
    if (this.minecraft != null && this.minecraft.player != null) {
      this.minecraft.player.connection.sendCommand("delhome " + name.replace(' ', '_'));
      com.openorigins.client.util.OutboxStats.record("menu /delhome " + name);
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
    this.drawHoverHighlight(extractor, mouseX, mouseY);
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
    Component status = this.statusLine();
    if (status != null) {
      extractor.text(this.font, status, 10, this.height - 16, 0xFFAAAAAA);
    }
  }

  /** Bottom-left progress line: the background work the panel is waiting on. */
  private Component statusLine() {
    String inFlight = com.openorigins.client.util.BackgroundMenu.inFlightCommand();
    if (inFlight != null) {
      return Component.translatable("menu.openorigins.status.fetching", inFlight);
    }
    if (this.pendingTeleport != null) {
      return Component.translatable("menu.openorigins.status.teleporting");
    }
    return null;
  }

  /** Runs a radial command, held when a background fetch is in flight. */
  private void activate(String command, String source) {
    if (this.minecraft == null || this.minecraft.player == null) {
      this.onClose();
      return;
    }
    if (!CommandDebounce.accept(command)) {
      return;
    }
    com.openorigins.client.util.DebugLog.info("[homes] {} '{}' clicked", source, command);
    if (com.openorigins.client.util.BackgroundMenu.sendOrQueueUserCommand(command)) {
      this.onClose();
    } else {
      this.pendingTeleport = command;
    }
  }

  /** Nearest radial entry to the cursor, or null outside the invisible circle. */
  private Entry entryAt(double mx, double my) {
    double dx = mx - this.radialX;
    double dy = my - this.radialY;
    double dist = Math.sqrt(dx * dx + dy * dy);
    if (dist < HIT_INNER || dist > this.hitRadius()) {
      return null;
    }
    double angle = Math.toDegrees(Math.atan2(dy, dx));
    Entry best = null;
    double bestDiff = 361.0;
    for (Entry e : ENTRIES) {
      double diff = Math.abs(angleDiff(angle, e.angleDeg()));
      if (diff < bestDiff) {
        bestDiff = diff;
        best = e;
      }
    }
    return best;
  }

  private static double angleDiff(double a, double b) {
    double d = (a - b) % 360.0;
    if (d > 180.0) {
      d -= 360.0;
    } else if (d < -180.0) {
      d += 360.0;
    }
    return d;
  }

  /**
   * A click anywhere in a sector counts as that button. Checked before the
   * widget pass: a screen-level handler that runs after super never sees the
   * empty space between widgets, only the widgets themselves.
   */
  @Override
  public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,
      boolean doubleClick) {
    if (event.button() == 0) {
      Entry entry = this.entryAt(event.x(), event.y());
      if (entry != null) {
        boolean over = this.overWidget(event.x(), event.y());
        com.openorigins.client.util.DebugLog.info(
            "[homes] click ({}, {}) sector='{}' widget={}", (int) event.x(), (int) event.y(),
            entry.command(), over);
        if (!over) {
          this.activate(entry.command(), "sector");
          return true;
        }
      }
    }
    return super.mouseClicked(event, doubleClick);
  }

  /**
   * Clickable circle: the icon ring plus its size, clamped so it always ends
   * before the homes and worlds panels (whatever the window width).
   */
  private double hitRadius() {
    double r = HIT_RADIUS_MAX;
    int panelW = this.homePanelWidth();
    if (panelW > 0) {
      r = Math.min(r, this.width - panelW - 10.0 - this.radialX - 6.0);
    }
    int worldW = this.worldPanelWidth();
    if (worldW > 0) {
      r = Math.min(r, this.radialX - 10.0 - worldW - 6.0);
    }
    r = Math.min(r, this.radialX - 6.0);
    r = Math.min(r, this.width - this.radialX - 6.0);
    r = Math.min(r, this.radialY - 6.0);
    r = Math.min(r, this.height - this.radialY - 6.0);
    return Math.max(HIT_INNER + 1.0, r);
  }

  /** Sector clicks (and their outline) exist everywhere except in "Off". */
  private boolean sectorClicksEnabled() {
    return com.openorigins.client.OpenOriginsConfig.getInstance().getRadialFill()
        != com.openorigins.client.OpenOriginsConfig.RadialFill.OFF;
  }

  /** True when the cursor is on a button: those keep their own handling. */
  private boolean overWidget(double mx, double my) {
    for (net.minecraft.client.gui.components.events.GuiEventListener child : this.children()) {
      if (child.isMouseOver(mx, my)) {
        return true;
      }
    }
    return false;
  }

  /** Hovered sector: soft fill, dithered edges, rim light just inside the edge. */
  private void drawHoverHighlight(GuiGraphicsExtractor g, int mouseX, int mouseY) {
    if (com.openorigins.client.OpenOriginsConfig.getInstance().getRadialFill()
        != com.openorigins.client.OpenOriginsConfig.RadialFill.ON) {
      return;
    }
    Entry hovered = this.entryAt(mouseX, mouseY);
    Entry shown;
    if (hovered != null) {
      if (hovered != this.fadeEntry || this.fadingOut) {
        this.fadeEntry = hovered;
        this.fadeStartMs = System.currentTimeMillis();
        this.fadingOut = false;
      }
      this.hoverAlpha = this.fadeCurve();
      shown = hovered;
    } else if (this.fadeEntry == null) {
      return;
    } else {
      // Cursor left the sector: keep drawing the last one while it melts.
      if (!this.fadingOut) {
        this.fadingOut = true;
        this.fadeStartMs = System.currentTimeMillis();
      }
      this.hoverAlpha = 1.0F - this.fadeCurve();
      if (this.hoverAlpha <= 0.0F) {
        this.fadeEntry = null;
        this.fadingOut = false;
        return;
      }
      shown = this.fadeEntry;
    }
    // Colour of the hovered teleport: blue balloon lights up blue, spawn red.
    this.fillColor = (HOVER_ALPHA << 24) | shown.rgb();
    this.rimTint = mixRgb(shown.rgb(), 0xFFFFFF, 0.45);
    double[] bounds = this.sectorBounds(shown);
    double r0 = HIT_INNER;
    double r1 = this.hitRadius();
    // Normals of the two radial edges: distance to them fades the sector sides.
    double a0 = Math.toRadians(bounds[0]);
    double a1 = Math.toRadians(bounds[1]);
    double n0x = -Math.sin(a0);
    double n0y = Math.cos(a0);
    double n1x = -Math.sin(a1);
    double n1y = Math.cos(a1);
    int steps = Math.max(2, (int) Math.ceil((bounds[1] - bounds[0]) / 2.0));
    double[][] poly = new double[(steps + 1) * 2][];
    int n = 0;
    for (int i = 0; i <= steps; i++) {
      double rad = Math.toRadians(bounds[0] + (bounds[1] - bounds[0]) * i / (double) steps);
      poly[n++] = new double[] {this.radialX + Math.cos(rad) * r0,
          this.radialY + Math.sin(rad) * r0};
    }
    for (int i = steps; i >= 0; i--) {
      double rad = Math.toRadians(bounds[0] + (bounds[1] - bounds[0]) * i / (double) steps);
      poly[n++] = new double[] {this.radialX + Math.cos(rad) * r1,
          this.radialY + Math.sin(rad) * r1};
    }
    double minY = Double.MAX_VALUE;
    double maxY = -Double.MAX_VALUE;
    for (double[] p : poly) {
      minY = Math.min(minY, p[1]);
      maxY = Math.max(maxY, p[1]);
    }
    double[] xs = new double[poly.length];
    int px = HOVER_PIXEL;
    int rowEnd = (int) Math.ceil(maxY / px) * px;
    for (int by = (int) Math.floor(minY / px) * px; by < rowEnd; by += px) {
      double scan = by + px / 2.0;
      int count = 0;
      for (int i = 0; i < poly.length; i++) {
        double[] a = poly[i];
        double[] b = poly[(i + 1) % poly.length];
        if ((a[1] <= scan) == (b[1] <= scan)) {
          continue;
        }
        xs[count++] = a[0] + (scan - a[1]) * (b[0] - a[0]) / (b[1] - a[1]);
      }
      java.util.Arrays.sort(xs, 0, count);
      for (int i = 0; i + 1 < count; i += 2) {
        this.drawHoverBand(g, by, xs[i], xs[i + 1], FILL_INNER, r1, n0x, n0y, n1x, n1y);
      }
    }
  }

  /**
   * One row of hover pixels. Coverage comes from the arcs and the radial edges,
   * is quantised to a few alpha steps, jittered by a stable per-pixel hash and
   * lifted by the rim light near the border. Neighbours of equal colour go out
   * as one fill.
   */
  private void drawHoverBand(GuiGraphicsExtractor g, int by, double left, double right,
      double r0, double r1, double n0x, double n0y, double n1x, double n1y) {
    int px = HOVER_PIXEL;
    int xStart = (int) Math.floor(left / px) * px;
    int xEnd = (int) Math.ceil(right / px) * px;
    int runStart = -1;
    int runColor = 0;
    for (int x = xStart; x <= xEnd; x += px) {
      int color = x < xEnd ? this.hoverColor(x, by, r0, r1, n0x, n0y, n1x, n1y) : 0;
      if (color == runColor) {
        continue;
      }
      if (runStart >= 0) {
        g.fill(runStart, by, x, by + px, runColor);
      }
      runStart = color == 0 ? -1 : x;
      runColor = color;
    }
  }

  /** Colour of the hover pixel whose top-left corner is (bx, by). */
  private int hoverColor(int bx, int by, double r0, double r1,
      double n0x, double n0y, double n1x, double n1y) {
    double px = HOVER_PIXEL;
    double dx = bx + px / 2.0 - this.radialX;
    double dy = by + px / 2.0 - this.radialY;
    double dist = Math.sqrt(dx * dx + dy * dy);
    double edge = Math.min(Math.abs(dx * n0x + dy * n0y), Math.abs(dx * n1x + dy * n1y));
    double coverage = Math.min(1.0, edge / EDGE_RAMP);
    coverage = Math.min(coverage, (r1 - dist) / OUTER_RAMP);
    coverage = Math.min(coverage, (dist - r0) / INNER_RAMP);
    if (coverage <= 0.0) {
      return 0;
    }
    // Pixel silhouette: a few coarse alpha steps instead of a vector ramp.
    coverage = Math.round(Math.min(1.0, coverage) * ALPHA_STEPS) / ALPHA_STEPS;
    if (coverage <= 0.0) {
      return 0;
    }
    int hash = pixelHash(bx / HOVER_PIXEL, by / HOVER_PIXEL);
    int fill = this.fillColor;
    int alpha = (int) Math.round(((fill >>> 24) & 0xFF) * coverage)
        + (int) Math.round(((hash & 0xFF) - 127.5) / 127.5 * NOISE_ALPHA);
    int rgb = fill & 0xFFFFFF;
    // Rim light: a smooth sheen standing back from the arc. It is added,
    // never stamped over the fill, so the edge keeps its soft falloff.
    // Distance to the nearest border of any kind: an arc or a radial edge.
    double border = Math.min(Math.min(r1 - dist, dist - r0), edge);
    double rim = Math.max(0.0, Math.min(1.0, 1.0 - border / RIM_PX));
    rim = rim * rim * (3.0 - 2.0 * rim);
    // Fades just at the very corner where a radial edge meets an arc.
    rim *= Math.min(1.0, edge / RIM_SIDE_FADE);
    if (rim > 0.0) {
      // Not scaled by coverage: the inner arc has only a short fade, and tying
      // the sheen to it dimmed that arc into nothing.
      alpha += (int) Math.round(RIM_ALPHA * rim);
      rgb = mixRgb(rgb, this.rimTint, 0.75 * rim);
    }
    rgb = shade(rgb, (int) Math.round(
        (((hash >>> 8) & 0xFF) - 127.5) / 127.5 * NOISE_TINT));
    alpha = Math.round(alpha * this.hoverAlpha);
    alpha = Math.max(0, Math.min(255, alpha));
    if (alpha <= 2) {
      return 0;
    }
    return (alpha << 24) | (rgb & 0xFFFFFF);
  }

  /** Mixes two RGB colours; t runs from the first to the second. */
  private static int mixRgb(int from, int to, double t) {
    double k = Math.max(0.0, Math.min(1.0, t));
    int r = (int) Math.round(((from >>> 16) & 0xFF) + (((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * k);
    int g = (int) Math.round(((from >>> 8) & 0xFF) + (((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * k);
    int b = (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * k);
    return (r << 16) | (g << 8) | b;
  }

  /** Adds a brightness delta to an RGB colour, channels clamped. */
  private static int shade(int rgb, int delta) {
    int r = Math.max(0, Math.min(255, ((rgb >>> 16) & 0xFF) + delta));
    int g = Math.max(0, Math.min(255, ((rgb >>> 8) & 0xFF) + delta));
    int b = Math.max(0, Math.min(255, (rgb & 0xFF) + delta));
    return (r << 16) | (g << 8) | b;
  }

  /** Stable per-pixel noise: the fill must not shimmer between frames. */
  private static int pixelHash(int x, int y) {
    int h = x * 374761393 + y * 668265263;
    h = (h ^ (h >>> 13)) * 1274126177;
    return h ^ (h >>> 16);
  }

  /** Angular span of the hovered button: halfway to its neighbours. */
  private double[] sectorBounds(Entry entry) {
    double[] angles = new double[ENTRIES.length];
    for (int i = 0; i < ENTRIES.length; i++) {
      angles[i] = normDeg(ENTRIES[i].angleDeg());
    }
    java.util.Arrays.sort(angles);
    double self = normDeg(entry.angleDeg());
    int idx = 0;
    double best = 361.0;
    for (int i = 0; i < angles.length; i++) {
      double diff = Math.abs(angleDiff(angles[i], self));
      if (diff < best) {
        best = diff;
        idx = i;
      }
    }
    double prev = idx == 0 ? angles[angles.length - 1] - 360.0 : angles[idx - 1];
    double next = idx == angles.length - 1 ? angles[0] + 360.0 : angles[idx + 1];
    return new double[] {(prev + angles[idx]) / 2.0, (angles[idx] + next) / 2.0};
  }

  private static double normDeg(double deg) {
    double d = deg % 360.0;
    return d < 0.0 ? d + 360.0 : d;
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
    // Any screen blocks a background fetch, so the panel cannot refresh the
    // world timers it drew as "..." while it was open: ask once we are out.
    com.openorigins.client.util.OpenWorldCache.ensureWorlds();
  }

  /**
   * Invisible disc under the icons: a click anywhere in a sector runs that
   * button, and the widget path is used because it is the one the screen
   * definitely dispatches (the buttons themselves answer the same way).
   */
  private final class SectorCatcher extends net.minecraft.client.gui.components.AbstractWidget {
    SectorCatcher(int x, int y, int w, int h) {
      super(x, y, w, h, Component.empty());
    }

    @Override
    public boolean isMouseOver(double mx, double my) {
      // "Off" takes the sector clicks away with the fill; "hidden" keeps them.
      if (!TeleportMenuScreen.this.sectorClicksEnabled()) {
        return false;
      }
      return TeleportMenuScreen.this.entryAt(mx, my) != null;
    }

    @Override
    public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
      Entry entry = TeleportMenuScreen.this.entryAt(event.x(), event.y());
      if (entry != null) {
        TeleportMenuScreen.this.activate(entry.command(), "sector");
      }
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
        float partialTick) {
      // Nothing to draw: the hover fill and the icons are drawn elsewhere.
    }

    @Override
    protected void updateWidgetNarration(
        net.minecraft.client.gui.narration.NarrationElementOutput output) {
      this.defaultButtonNarrationText(output);
    }
  }

  private static final class PackIconButton extends AbstractButton {
    private final Identifier textureId;
    private final int texW;
    private final int texH;
    private final Runnable action;
    /** True while the cursor is anywhere in this button's sector, not just on
     *  the icon: the frame marks the whole activation area, the tooltip does
     *  not (that one stays on the icon rectangle). */
    private final java.util.function.BiPredicate<Double, Double> sectorHover;

    PackIconButton(int x, int y, int size, Component message, Identifier textureId,
        int texW, int texH, Runnable action,
        java.util.function.BiPredicate<Double, Double> sectorHover) {
      super(x, y, size, size, message);
      this.textureId = textureId;
      this.texW = texW;
      this.texH = texH;
      this.action = action;
      this.sectorHover = sectorHover;
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
      com.openorigins.client.util.VersionCompat.blitFull(graphics, this.textureId, dx, dy, 0.0F, 0.0F, dw, dh, this.texW, this.texH, this.texW, this.texH);
      boolean inSector = this.sectorHover != null
          && this.sectorHover.test((double) mouseX, (double) mouseY);
      if (this.isHoveredOrFocused() || inSector) {
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

  static AbstractButton makeHomeCell(
      Minecraft mc, int x, int y, int size, String name,
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

  static AbstractButton makePlusCell(
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
}