package com.openorigins.client.screen;

import com.openorigins.client.ConnectionUtil;
import com.openorigins.client.OpenOriginsConfig;
import com.openorigins.client.OriginFeature;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

public class FeatureList extends ContainerObjectSelectionList<FeatureList.Entry> {
  private static final Identifier RESET_ICON =
      Identifier.fromNamespaceAndPath("openorigins", "reset");
  private static final int ROW_H = 24;
  private ConfigTab jumpTab;
  private double jumpScroll;
  private double smoothTarget = Double.NaN;
  private double lastScroll = Double.NaN;
  private long lastFrameNs = 0;
  private boolean rebuildPending = false;
  private static final double WHEEL_STEP = 24.0;
  private static final double SMOOTH_SPEED = 12.0;
  private static final double GROUP_ANIM_SPEED = 3.2;
  private static final java.util.Map<String, Boolean> EXPANDED = new java.util.HashMap<>();

  private static boolean isExpanded(String group) {
    Boolean b = EXPANDED.get(group);
    return b == null || b;
  }

  /** Structural change (toggle, expand/collapse): rebuild entries next frame. */
  public void requestRebuild() {
    this.rebuildPending = true;
  }

  private static volatile ConfigTab spyTab;
  private static volatile java.util.function.Consumer<ConfigTab> spyListener;

  /** Called by the config screen to follow the visible section with tabs. */
  public static void setSpyListener(java.util.function.Consumer<ConfigTab> listener) {
    spyListener = listener;
    spyTab = null;
  }

  /** Section on screen at the current scroll (accumulates real row heights). */
  private ConfigTab tabAtScroll() {
    double y = 0;
    try {
      y = this.scrollAmount();
    } catch (Throwable ignored) {
    }
    ConfigTab found = null;
    double acc = 0;
    try {
      for (Entry e : this.children()) {
        if (e instanceof CategoryEntry ce) {
          for (ConfigTab t : ConfigTab.values()) {
            if (t.getCategories().contains(ce.category)) {
              found = t;
              break;
            }
          }
        }
        try {
          acc += e.getHeight();
        } catch (Throwable ignored) {
        }
        if (acc > y + 2) {
          break;
        }
      }
    } catch (Throwable ignored) {
    }
    return found;
  }

  @Override
  public void setScrollAmount(double d) {
    super.setScrollAmount(d);
    try {
      if (this.jumpTab != null) {
        double now;
        try {
          now = this.scrollAmount();
        } catch (Throwable ignored) {
          now = d;
        }
        // A programmed jump (possibly clamped): hold its highlight until the
        // user actually scrolls away, otherwise deep sections can never win.
        if (Math.abs(now - this.jumpScroll) < 8) {
          return;
        }
        this.jumpTab = null;
      }
      ConfigTab at = tabAtScroll();
      if (at != null && at != spyTab) {
        spyTab = at;
        java.util.function.Consumer<ConfigTab> listener = spyListener;
        if (listener != null) {
          listener.accept(at);
        }
      }
    } catch (Throwable ignored) {
    }
  }

  @Override
  public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
    try {
      if (scrollY == 0 || !this.isMouseOver(mouseX, mouseY)) {
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
      }
      double max;
      try {
        max = this.maxScrollAmount();
      } catch (Throwable t) {
        max = 0;
      }
      if (max <= 0) {
        return false;
      }
      double cur;
      try {
        cur = this.scrollAmount();
      } catch (Throwable t) {
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
      }
      double base = Double.isNaN(this.smoothTarget) ? cur : this.smoothTarget;
      this.smoothTarget = Math.max(0, Math.min(max, base - scrollY * WHEEL_STEP));
      this.lastScroll = cur;
      return true;
    } catch (Throwable t) {
      return false;
    }
  }

  @Override
  public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
      float partialTick) {
    long now = System.nanoTime();
    if (this.lastFrameNs == 0) {
      this.lastFrameNs = now;
    }
    double dt = Math.min(0.05, Math.max(0, (now - this.lastFrameNs) / 1_000_000_000.0));
    this.lastFrameNs = now;
    if (this.rebuildPending) {
      this.rebuildPending = false;
      try {
        this.rebuildNow();
      } catch (Throwable ignored) {
      }
    }
    this.advanceGroupAnims(dt);
    try {
      double max = this.maxScrollAmount();
      if (this.scrollAmount() > max) {
        this.setScrollAmount(max);
      }
    } catch (Throwable ignored) {
    }
    this.advanceSmoothScroll(dt);
    super.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
  }

  private static int animHeight(double p) {
    double e = p <= 0 ? 0 : p >= 1 ? 1
        : (p < 0.5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2);
    return (int) Math.round(ROW_H * e);
  }

  private double groupOpenTarget(String group) {
    OpenOriginsConfig cfg = OpenOriginsConfig.getInstance();
    return switch (group) {
      case "badger" ->
        cfg.isEnabled(OriginFeature.BADGER_TASK_HUD) && isExpanded("badger") ? 1 : 0;
      case "extrap" ->
        cfg.isEnabled(OriginFeature.ENTITY_EXTRAPOLATION) && isExpanded("extrap") ? 1 : 0;
      case "servers" -> !cfg.isUseHandshake() && isExpanded("servers") ? 1 : 0;
      default -> 1;
    };
  }

  /** Snaps every collapsible row to its target (open/rebuild/tab switch). */
  public void snapGroups() {
    try {
      for (Entry e : this.children()) {
        if (e.collapseGroup() != null) {
          double t = groupOpenTarget(e.collapseGroup());
          e.animProgress = t;
          try {
            e.setHeight(animHeight(t));
          } catch (Throwable ignored) {
          }
        }
      }
    } catch (Throwable ignored) {
    }
  }

  private void advanceGroupAnims(double dt) {
    try {
      for (Entry e : this.children()) {
        String g = e.collapseGroup();
        if (g == null) {
          continue;
        }
        double target = groupOpenTarget(g);
        double cur = e.animProgress;
        if (cur == target) {
          continue;
        }
        double next = cur < target ? Math.min(target, cur + dt * GROUP_ANIM_SPEED)
            : Math.max(target, cur - dt * GROUP_ANIM_SPEED);
        e.animProgress = next;
        try {
          e.setHeight(animHeight(next));
        } catch (Throwable ignored) {
        }
      }
    } catch (Throwable ignored) {
    }
  }

  /** Framerate-independent glide toward the wheel target (softer than Cloth). */
  private void advanceSmoothScroll(double dt) {
    double cur;
    try {
      cur = this.scrollAmount();
    } catch (Throwable t) {
      return;
    }
    if (Double.isNaN(this.smoothTarget)) {
      this.lastScroll = cur;
      return;
    }
    if (!Double.isNaN(this.lastScroll) && Math.abs(cur - this.lastScroll) > 0.75) {
      // Scrollbar drag, key scroll or a programmed jump moved us: yield.
      this.smoothTarget = Double.NaN;
      this.lastScroll = cur;
      return;
    }
    double diff = this.smoothTarget - cur;
    if (Math.abs(diff) < 0.5) {
      try {
        this.setScrollAmount(this.smoothTarget);
      } catch (Throwable ignored) {
      }
      this.smoothTarget = Double.NaN;
      try {
        this.lastScroll = this.scrollAmount();
      } catch (Throwable ignored) {
      }
      return;
    }
    double next = cur + diff * (1.0 - Math.exp(-dt * SMOOTH_SPEED));
    try {
      this.setScrollAmount(next);
    } catch (Throwable ignored) {
      this.smoothTarget = Double.NaN;
      return;
    }
    try {
      this.lastScroll = this.scrollAmount();
    } catch (Throwable ignored) {
    }
  }

  public FeatureList(Minecraft minecraft, int width, int contentHeight, int headerHeight,
      ConfigTab tab) {
    super(minecraft, width, contentHeight, headerHeight, ROW_H);
    for (Entry e : buildFeed()) {
      this.addEntry(e);
    }
    this.snapGroups();
  }

  private static String groupFor(OriginFeature feature) {
    if (feature == OriginFeature.BADGER_TASK_HUD) {
      return "badger";
    }
    if (feature == OriginFeature.ENTITY_EXTRAPOLATION) {
      return "extrap";
    }
    return null;
  }

  /**
   * Full feed in order. Hidden group children are omitted entirely (not
   * zero-height), so scrollbar, spy and scroll math stay exact.
   */
  private java.util.List<Entry> buildFeed() {
    java.util.List<Entry> out = new java.util.ArrayList<>();
    // Single feed: every tab page contains all sections; the opener tab only
    // decides the initial scroll position (tabs teleport, nothing is hidden).
    for (ConfigTab t : ConfigTab.values()) {
      for (com.openorigins.client.FeatureCategory category : t.getCategories()) {
        out.add(new CategoryEntry(this, category));
      for (OriginFeature feature : OriginFeature.values()) {
        if (feature.getCategory() == category) {
          if (feature.isHidden()) {
            continue;
          }
          out.add(new FeatureEntry(this, feature, groupFor(feature)));
          if (feature == OriginFeature.BADGER_TASK_HUD) {
            CycleRow posRow = new CycleRow(this, "config.openorigins.position",
                "config.openorigins.hud_position_hint", FeatureList::cornerName,
                FeatureList::cycleCorner, true,
                () -> OpenOriginsConfig.getInstance().setBadgerHudCorner(
                    OpenOriginsConfig.HudCorner.TOP_LEFT),
                () -> OpenOriginsConfig.getInstance().getBadgerHudCorner()
                    == OpenOriginsConfig.HudCorner.TOP_LEFT);
            posRow.setCollapseGroup("badger");
            out.add(posRow);
          }
          if (feature == OriginFeature.ENTITY_EXTRAPOLATION) {
            StepperRow extrapRow = new StepperRow(this, "config.openorigins.extrap_ms",
                "config.openorigins.extrap_ms_hint", 0, 1000,
                () -> OpenOriginsConfig.getInstance().getExtrapMs(),
                v -> OpenOriginsConfig.getInstance().setExtrapMs(v), 150, true);
            extrapRow.setCollapseGroup("extrap");
            out.add(extrapRow);
          }
        }
      }
      if (category == com.openorigins.client.FeatureCategory.UI) {
        out.add(new StepperRow(this, "config.openorigins.max_slots",
            "config.openorigins.capacity_hint", 0, 15,
            () -> OpenOriginsConfig.getInstance().getHomeCapacity(),
            v -> OpenOriginsConfig.getInstance().setHomeCapacity(v), 5));
      }
      if (category == com.openorigins.client.FeatureCategory.MISC) {
        out.add(new CycleRow(this, "config.openorigins.scan_key",
            "config.openorigins.scan_key_hint",
            () -> com.openorigins.client.HotkeyFeatures.isScanCaptureArmed() ? "..."
                : com.openorigins.client.HotkeyFeatures.scanKeyName(),
            () -> com.openorigins.client.HotkeyFeatures.beginScanCapture(), false,
            () -> {
              OpenOriginsConfig.getInstance().resetScanKey();
              com.openorigins.client.HotkeyFeatures.reloadScanKey();
            },
            () -> OpenOriginsConfig.getInstance().isScanKeyDefault()));
        for (com.openorigins.client.HotkeyFeatures.Hotkey h
            : com.openorigins.client.HotkeyFeatures.debugHotkeys()) {
          out.add(new CycleRow(this, h.labelKey(), h.labelKey(),
              () -> com.openorigins.client.HotkeyFeatures.isCapturing(h.mapping()) ? "..."
                  : com.openorigins.client.HotkeyFeatures.keyName(h.mapping()),
              () -> com.openorigins.client.HotkeyFeatures.beginCapture(h.mapping()), false,
              () -> h.mapping().setKey(h.mapping().getDefaultKey()),
              () -> h.mapping().isDefault()));
        }
      }
      }
    }
    addServerRows(out);
    return out;
  }

  /** Rows fit the list: wide enough for long labels, never wider (scissor). */
  @Override
  public int getRowWidth() {
    try {
      return Math.max(220, Math.min(380, this.getWidth() - 24));
    } catch (Throwable t) {
      return 340;
    }
  }

  /** Jumps the feed to the section a tab points at (real row heights, not estimates). */
  public void scrollToSection(ConfigTab tab) {
    this.smoothTarget = Double.NaN;
    try {
      for (com.openorigins.client.FeatureCategory category : tab.getCategories()) {
        int px = 0;
        for (Entry e : this.children()) {
          if (e instanceof CategoryEntry ce && ce.category == category) {
            this.jumpTab = tab;
            this.setScrollAmount(px);
            try {
              this.jumpScroll = this.scrollAmount();
            } catch (Throwable ignored) {
              this.jumpScroll = px;
            }
            spyTab = tab;
            java.util.function.Consumer<ConfigTab> listener = spyListener;
            if (listener != null) {
              listener.accept(tab);
            }
            return;
          }
          try {
            px += e.getHeight();
          } catch (Throwable ignored) {
          }
        }
      }
    } catch (Throwable ignored) {
    }
  }

  /**
   * Full feed rebuild with scroll anchored to the top visible row, so
   * collapsing a group above the viewport does not jump the view.
   */
  public void rebuildNow() {
    double top = 0;
    try {
      top = this.scrollAmount();
    } catch (Throwable ignored) {
    }
    String anchor = null;
    double anchorOff = 0;
    try {
      double acc = 0;
      for (Entry e : this.children()) {
        double h = ROW_H;
        try {
          h = e.getHeight();
        } catch (Throwable ignored) {
        }
        if (acc + h > top + 1) {
          anchor = e.key();
          anchorOff = top - acc;
          break;
        }
        acc += h;
      }
    } catch (Throwable ignored) {
    }
    try {
      this.replaceEntries(buildFeed());
    } catch (Throwable t) {
      return;
    }
    this.snapGroups();
    try {
      if (anchor != null) {
        double acc = 0;
        for (Entry e : this.children()) {
          if (anchor.equals(e.key())) {
            this.setScrollAmount(acc + anchorOff);
            break;
          }
          try {
            acc += e.getHeight();
          } catch (Throwable ignored) {
          }
        }
      } else {
        this.setScrollAmount(top);
      }
    } catch (Throwable ignored) {
    }
    this.refreshEntries();
  }

  private void addServerRows(java.util.List<Entry> out) {
    out.add(new ServerInfoRow(this));
    out.add(new ToggleRow(this, "config.openorigins.use_handshake",
        "config.openorigins.use_handshake_hint",
        () -> OpenOriginsConfig.getInstance().isUseHandshake(),
        () -> {
          OpenOriginsConfig.getInstance().setUseHandshake(
              !OpenOriginsConfig.getInstance().isUseHandshake());
          this.refreshEntries();
        },
        true,
        v -> {
          OpenOriginsConfig.getInstance().setUseHandshake(v);
          this.refreshEntries();
        }, "servers", () -> !OpenOriginsConfig.getInstance().isUseHandshake()));
    for (String s : OpenOriginsConfig.getInstance().getServers()) {
      out.add(new ServerRow(this, s));
    }
    out.add(new ServerAddRow(this));
  }

  private static String cornerName() {
    OpenOriginsConfig.HudCorner corner = OpenOriginsConfig.getInstance().getBadgerHudCorner();
    return net.minecraft.network.chat.Component.translatable(
        "config.openorigins.corner." + corner.name().toLowerCase()).getString();
  }

  private static void cycleCorner() {
    OpenOriginsConfig.getInstance().cycleBadgerHudCorner();
  }

  public void refreshEntries() {
    this.children().forEach(FeatureList.Entry::refreshEntry);
  }

  public abstract static class Entry extends ContainerObjectSelectionList.Entry<Entry> {
    private String collapseGroup;
    private double animProgress = 1.0;

    public abstract void refreshEntry();

    /** Stable id used to anchor the scroll across feed rebuilds. */
    public String key() {
      return "entry";
    }

    /** Dependent-subgroup id, or null for static rows. */
    public String collapseGroup() {
      return this.collapseGroup;
    }

    public void setCollapseGroup(String group) {
      this.collapseGroup = group;
    }

    public boolean isCollapsed() {
      return this.collapseGroup != null && this.animProgress <= 0.0;
    }
  }

  public static class CategoryEntry extends Entry {    private final FeatureList list;
    private final com.openorigins.client.FeatureCategory category;

    public CategoryEntry(FeatureList list, com.openorigins.client.FeatureCategory category) {
      this.list = list;
      this.category = category;
    }

    public void refreshEntry() {
    }

    @Override
    public String key() {
      return "cat:" + this.category.name();
    }

    @Override
    public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered,
        float partialTick) {
      net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
      boolean experimental =
          this.category == com.openorigins.client.FeatureCategory.EXPERIMENTAL;
      Component name = Component.translatable(this.category.getTranslationKey())
          .withStyle(experimental ? net.minecraft.ChatFormatting.YELLOW
              : net.minecraft.ChatFormatting.WHITE,
              net.minecraft.ChatFormatting.BOLD);
      int w = font.width(name);
      int rowW = this.list.getRowRight() - this.getContentX();
      int rowTop = this.getContentYMiddle() - 12;
      graphics.fill(this.getContentX(), rowTop, this.list.getRowRight(), rowTop + 24,
          0x40000000);
      graphics.text(font, name, this.getContentX() + (rowW - w) / 2,
          this.getContentYMiddle() - 4, 0xFFFFFFFF);
    }

    @Override
    public List<? extends GuiEventListener> children() {
      return List.of();
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
      return List.of();
    }
  }

  public static class FeatureEntry extends Entry {
    private final FeatureList list;
    private final OriginFeature feature;
    private final String group;
    private final Button toggleButton;
    private final Button resetButton;
    private final GlyphToggleButton expandButton;

    public FeatureEntry(FeatureList list, OriginFeature feature, String group) {
      this.list = list;
      this.feature = feature;
      this.group = group;
      this.toggleButton = Button.builder(Component.empty(), button -> {
        OpenOriginsConfig config = OpenOriginsConfig.getInstance();
        config.setEnabled(feature, !config.isEnabled(feature));
        if (feature == com.openorigins.client.OriginFeature.ENHANCED_COSMETICS) {
          net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
          ReconnectToast.show(mc.gui.toastManager(), Component.literal("OpenOrigins"),
              Component.translatable("config.openorigins.reconnect_note"));
        }
        list.refreshEntries();
      }).bounds(0, 0, 100, 20)
          .tooltip(net.minecraft.client.gui.components.Tooltip.create(
              featureTooltip(feature)))
          .build();
      this.resetButton = SpriteIconButton.builder(
          Component.translatable("config.openorigins.reset_row"), button -> {
        OpenOriginsConfig config = OpenOriginsConfig.getInstance();
        config.setEnabled(feature, feature.getDefaultValue());
        list.refreshEntries();
      }, true)
          .width(20).sprite(RESET_ICON, 8, 8).withTootip().build();
      if (group != null) {
        this.expandButton = new GlyphToggleButton(() -> {
          EXPANDED.put(group, !isExpanded(group));
          list.refreshEntries();
        }, Component.translatable("config.openorigins.expand"));
      } else {
        this.expandButton = null;
      }
      this.refreshEntry();
    }

    public void refreshEntry() {
      boolean on = OpenOriginsConfig.getInstance().isEnabled(this.feature);
      this.toggleButton.setMessage(Component.translatable(on ? "options.on" : "options.off")
          .withStyle(on ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED));
      this.resetButton.active = on != this.feature.getDefaultValue();
      if (this.expandButton != null) {
        boolean exp = isExpanded(this.group);
        this.expandButton.setMessage(Component.literal(exp ? "-" : "+"));
        this.expandButton.setTooltip(Tooltip.create(Component.translatable(
            exp ? "config.openorigins.collapse" : "config.openorigins.expand")));
        this.expandButton.active = on;
      }
    }

    @Override
    public String key() {
      return "feat:" + this.feature.name();
    }

    private static Component featureTooltip(OriginFeature feature) {
      Component desc = Component.translatable(feature.getDescriptionKey());
      if (feature == OriginFeature.HELMET_COSMETIC_SLOT
          || feature == OriginFeature.STRICT_RECIPE_MATCH
          || feature == OriginFeature.ENTITY_EXTRAPOLATION) {
        return desc.copy().append("\n")
            .append(Component.translatable("label.openorigins.experimental")
                .withStyle(net.minecraft.ChatFormatting.YELLOW))
            .append("\n")
            .append(Component.translatable(
                "feature.openorigins." + feature.name().toLowerCase() + ".hint")
                .withStyle(net.minecraft.ChatFormatting.ITALIC));
      }
      return desc;
    }

    @Override
    public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered,
        float partialTick) {
      this.resetButton.setX(this.list.getRowRight() - this.resetButton.getWidth());
      this.resetButton.setY(this.getContentY() + 2);
      this.toggleButton.setX(this.resetButton.getX() - 4 - this.toggleButton.getWidth());
      this.toggleButton.setY(this.getContentY() + 2);
      this.toggleButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
      this.resetButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
      int labelX = this.getContentX();
      if (this.expandButton != null) {
        this.expandButton.setX(labelX);
        this.expandButton.setY(this.getContentY() + 2);
        this.expandButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
        labelX += 20;
      }
      net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
      graphics.text(font, Component.literal(this.feature.getName()), labelX,
          this.getContentYMiddle() - 4, 0xFFFFFFFF);
    }

    @Override
    public List<? extends GuiEventListener> children() {
      if (this.expandButton != null) {
        return List.of(this.expandButton, this.toggleButton, this.resetButton);
      }
      return List.of(this.toggleButton, this.resetButton);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
      if (this.expandButton != null) {
        return List.of(this.expandButton, this.toggleButton, this.resetButton);
      }
      return List.of(this.toggleButton, this.resetButton);
    }
  }

  /** Label left, green/red ON/OFF toggle right. */
  public static class ToggleRow extends Entry {
    private final FeatureList list;
    private final String labelKey;
    private final String tooltipKey;
    private final java.util.function.BooleanSupplier get;
    private final Runnable onPress;
    private final boolean defaultValue;
    private final java.util.function.Consumer<Boolean> set;
    private final String group;
    private final java.util.function.BooleanSupplier groupActive;
    private final Button button;
    private final Button resetButton;
    private final GlyphToggleButton expandButton;

    public ToggleRow(FeatureList list, String labelKey, String tooltipKey,
        java.util.function.BooleanSupplier get, Runnable onPress) {
      this(list, labelKey, tooltipKey, get, onPress, false, v -> {}, null, null);
    }

    public ToggleRow(FeatureList list, String labelKey, String tooltipKey,
        java.util.function.BooleanSupplier get, Runnable onPress,
        boolean defaultValue, java.util.function.Consumer<Boolean> set) {
      this(list, labelKey, tooltipKey, get, onPress, defaultValue, set, null, null);
    }

    public ToggleRow(FeatureList list, String labelKey, String tooltipKey,
        java.util.function.BooleanSupplier get, Runnable onPress,
        boolean defaultValue, java.util.function.Consumer<Boolean> set,
        String group, java.util.function.BooleanSupplier groupActive) {
      this.list = list;
      this.labelKey = labelKey;
      this.tooltipKey = tooltipKey;
      this.get = get;
      this.onPress = onPress;
      this.defaultValue = defaultValue;
      this.set = set;
      this.group = group;
      this.groupActive = groupActive;
      this.button = Button.builder(Component.empty(), b -> {
        this.onPress.run();
        this.list.refreshEntries();
      }).bounds(0, 0, 100, 20)
          .tooltip(net.minecraft.client.gui.components.Tooltip.create(
              Component.translatable(this.tooltipKey)))
          .build();
      this.resetButton = SpriteIconButton.builder(
          Component.translatable("config.openorigins.reset_row"), b -> {
        this.set.accept(this.defaultValue);
        this.list.refreshEntries();
      }, true)
          .width(20).sprite(RESET_ICON, 8, 8).withTootip().build();
      if (group != null) {
        this.expandButton = new GlyphToggleButton(() -> {
          EXPANDED.put(group, !isExpanded(group));
          list.refreshEntries();
        }, Component.translatable("config.openorigins.expand"));
      } else {
        this.expandButton = null;
      }
      this.refreshEntry();
    }

    public void refreshEntry() {
      boolean on = this.get.getAsBoolean();
      this.button.setMessage(Component.translatable(on ? "options.on" : "options.off")
          .withStyle(on ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED));
      this.resetButton.active = on != this.defaultValue;
      if (this.expandButton != null) {
        boolean exp = isExpanded(this.group);
        this.expandButton.setMessage(Component.literal(exp ? "-" : "+"));
        this.expandButton.setTooltip(Tooltip.create(Component.translatable(
            exp ? "config.openorigins.collapse" : "config.openorigins.expand")));
        this.expandButton.active = this.groupActive == null || this.groupActive.getAsBoolean();
      }
    }

    @Override
    public String key() {
      return "toggle:" + this.labelKey;
    }

    @Override
    public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered,
        float partialTick) {
      this.resetButton.setX(this.list.getRowRight() - this.resetButton.getWidth());
      this.resetButton.setY(this.getContentY() + 2);
      this.button.setX(this.resetButton.getX() - 4 - this.button.getWidth());
      this.button.setY(this.getContentY() + 2);
      this.button.extractRenderState(graphics, mouseX, mouseY, partialTick);
      this.resetButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
      int labelX = this.getContentX();
      if (this.expandButton != null) {
        this.expandButton.setX(labelX);
        this.expandButton.setY(this.getContentY() + 2);
        this.expandButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
        labelX += 20;
      }
      net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
      graphics.text(font, Component.translatable(this.labelKey), labelX,
          this.getContentYMiddle() - 4, 0xFFFFFFFF);
    }

    @Override
    public List<? extends GuiEventListener> children() {
      if (this.expandButton != null) {
        return List.of(this.expandButton, this.button, this.resetButton);
      }
      return List.of(this.button, this.resetButton);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
      if (this.expandButton != null) {
        return List.of(this.expandButton, this.button, this.resetButton);
      }
      return List.of(this.button, this.resetButton);
    }
  }

  /** Label left, single value button right (cycles on click). */
  public static class CycleRow extends Entry {
    private final FeatureList list;
    private final String labelKey;
    private final String tooltipKey;
    private final boolean indented;
    private final java.util.function.Supplier<String> value;
    private final Runnable cycle;
    private final Button button;
    private final Runnable reset;
    private final java.util.function.BooleanSupplier isDefault;
    private final Button resetButton;

    public CycleRow(FeatureList list, String labelKey, String tooltipKey,
        java.util.function.Supplier<String> value, Runnable cycle, boolean indented) {
      this(list, labelKey, tooltipKey, value, cycle, indented, null, () -> true);
    }

    public CycleRow(FeatureList list, String labelKey, String tooltipKey,
        java.util.function.Supplier<String> value, Runnable cycle, boolean indented,
        Runnable reset, java.util.function.BooleanSupplier isDefault) {
      this.list = list;
      this.labelKey = labelKey;
      this.tooltipKey = tooltipKey;
      this.indented = indented;
      this.value = value;
      this.cycle = cycle;
      this.reset = reset;
      this.isDefault = isDefault;
      this.button = Button.builder(Component.empty(), b -> {
        this.cycle.run();
        this.list.refreshEntries();
      }).bounds(0, 0, 100, 20)
          .tooltip(net.minecraft.client.gui.components.Tooltip.create(
              Component.translatable(this.tooltipKey)))
          .build();
      this.resetButton = SpriteIconButton.builder(
          Component.translatable("config.openorigins.reset_row"), b -> {
        if (this.reset != null) {
          this.reset.run();
        }
        this.list.refreshEntries();
      }, true)
          .width(20).sprite(RESET_ICON, 8, 8).withTootip().build();
      this.refreshEntry();
    }

    public void refreshEntry() {
      this.button.setMessage(Component.literal(this.value.get()));
      this.resetButton.active = this.reset != null && !this.isDefault.getAsBoolean();
    }

    @Override
    public String key() {
      return "cycle:" + this.labelKey;
    }

    @Override
    public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered,
        float partialTick) {
      if (this.isCollapsed()) {
        return;
      }
      if (this.reset != null) {
        this.resetButton.setX(this.list.getRowRight() - this.resetButton.getWidth());
        this.resetButton.setY(this.getContentY() + 2);
        this.resetButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.button.setX(this.resetButton.getX() - 4 - this.button.getWidth());
      } else {
        this.button.setX(this.list.getRowRight() - this.button.getWidth());
      }
      this.button.setY(this.getContentY() + 2);
      this.button.extractRenderState(graphics, mouseX, mouseY, partialTick);
      net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
      Component label = Component.translatable(this.labelKey);
      if (this.indented) {
        label = Component.literal("    ").append(label);
      }
      graphics.text(font, label, this.getContentX(),
          this.getContentYMiddle() - 4, 0xFFFFFFFF);
    }

    @Override
    public List<? extends GuiEventListener> children() {
      if (this.isCollapsed()) {
        return List.of();
      }
      if (this.reset != null) {
        return List.of(this.button, this.resetButton);
      }
      return List.of(this.button);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
      if (this.isCollapsed()) {
        return List.of();
      }
      if (this.reset != null) {
        return List.of(this.button, this.resetButton);
      }
      return List.of(this.button);
    }
  }

  /** Label left, [-] value [+] stepper right. */

  /** Label left, [-] [field] [+] value control right. */
  public static class StepperRow extends Entry {
    private final FeatureList list;
    private final String labelKey;
    private final int min;
    private final int max;
    private final java.util.function.IntSupplier get;
    private final java.util.function.IntConsumer set;
    private final Button minusButton;
    private final Button plusButton;
    private final int defaultValue;
    private final Button resetButton;
    private final boolean indented;
    private net.minecraft.client.gui.components.EditBox field;

    public StepperRow(FeatureList list, String labelKey, String tooltipKey, int min, int max,
        java.util.function.IntSupplier get, java.util.function.IntConsumer set, int defaultValue) {
      this(list, labelKey, tooltipKey, min, max, get, set, defaultValue, false);
    }

    public StepperRow(FeatureList list, String labelKey, String tooltipKey, int min, int max,
        java.util.function.IntSupplier get, java.util.function.IntConsumer set, int defaultValue,
        boolean indented) {
      this.list = list;
      this.labelKey = labelKey;
      this.indented = indented;
      this.min = min;
      this.max = max;
      this.get = get;
      this.set = set;
      this.defaultValue = defaultValue;
      this.minusButton = Button.builder(Component.literal("-"), b -> {
        this.set.accept(Math.max(this.min, this.get.getAsInt() - 1));
        this.list.refreshEntries();
      }).bounds(0, 0, 20, 20).build();
      this.plusButton = Button.builder(Component.literal("+"), b -> {
        this.set.accept(Math.min(this.max, this.get.getAsInt() + 1));
        this.list.refreshEntries();
      }).bounds(0, 0, 20, 20).build();
      this.resetButton = SpriteIconButton.builder(
          Component.translatable("config.openorigins.reset_row"), b -> {
        this.set.accept(this.defaultValue);
        this.list.refreshEntries();
      }, true)
          .width(20).sprite(RESET_ICON, 8, 8).withTootip().build();
    }

    public void refreshEntry() {
      if (this.field != null) {
        String v = Integer.toString(this.get.getAsInt());
        if (!this.field.getValue().equals(v)) {
          this.field.setValue(v);
        }
      }
      this.resetButton.active = this.get.getAsInt() != this.defaultValue;
    }

    @Override
    public String key() {
      return "step:" + this.labelKey;
    }

    @Override
    public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered,
        float partialTick) {
      if (this.isCollapsed()) {
        return;
      }
      int right = this.list.getRowRight();
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      if (this.field == null && mc != null) {
        this.field = new net.minecraft.client.gui.components.EditBox(mc.font, 0, 0, 52, 20,
            Component.translatable(this.labelKey));
        this.field.setTextColor(0x00000000);
        this.field.setMaxLength(Math.max(2, Integer.toString(this.max).length()));
        this.field.setValue(Integer.toString(this.get.getAsInt()));
        this.field.setResponder(s -> {
          String digits = s.replaceAll("[^0-9]", "");
          if (!digits.equals(s)) {
            this.field.setValue(digits);
            return;
          }
          if (!digits.isEmpty()) {
            try {
              int v = Integer.parseInt(digits);
              if (v >= this.min && v <= this.max) {
                this.set.accept(v);
                this.list.refreshEntries();
              }
            } catch (NumberFormatException ignored) {
            }
          }
        });
      }
      this.plusButton.setX(right - 20);
      this.plusButton.setY(this.getContentY() + 2);
      this.resetButton.setX(right - this.resetButton.getWidth());
      this.resetButton.setY(this.getContentY() + 2);
      this.plusButton.setX(this.resetButton.getX() - 4 - this.plusButton.getWidth());
      if (this.field != null) {
        this.field.setX(this.plusButton.getX() - 4 - 52);
        this.field.setY(this.getContentY() + 2);
        try {
          // Our value is drawn manually centered with invisible field text;
          // pin the selection to the cursor so its white box never shows.
          this.field.setHighlightPos(this.field.getCursorPosition());
        } catch (Throwable ignored) {
        }
        this.field.extractRenderState(graphics, mouseX, mouseY, partialTick);
        net.minecraft.client.gui.Font ff = net.minecraft.client.Minecraft.getInstance().font;
        String fv = this.field.getValue();
        int fw = ff.width(fv);
        graphics.text(ff, fv, this.field.getX() + (52 - fw) / 2,
            this.getContentYMiddle() - 4, 0xFFFFFFFF);
      }
      this.minusButton.setX(this.field != null
          ? this.field.getX() - 4 - this.minusButton.getWidth()
          : this.plusButton.getX() - 4 - this.minusButton.getWidth());
      this.minusButton.setY(this.getContentY() + 2);
      this.plusButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
      this.minusButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
      this.resetButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
      net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
      Component label = Component.translatable(this.labelKey);
      if (this.indented) {
        label = Component.literal("    ").append(label);
      }
      graphics.text(font, label, this.getContentX(),
          this.getContentYMiddle() - 4, 0xFFFFFFFF);
    }

    @Override
    public List<? extends GuiEventListener> children() {
      if (this.isCollapsed()) {
        return List.of();
      }
      if (this.field != null) {
        return List.of(this.minusButton, this.field, this.plusButton, this.resetButton);
      }
      return List.of(this.minusButton, this.plusButton, this.resetButton);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
      if (this.isCollapsed()) {
        return List.of();
      }
      if (this.field != null) {
        return List.of(this.minusButton, this.field, this.plusButton, this.resetButton);
      }
      return List.of(this.minusButton, this.plusButton, this.resetButton);
    }
  }

  /** Current-server readout for the Servers tab. */
  public static class ServerInfoRow extends Entry {
    private final FeatureList list;
    private String value = "";

    public ServerInfoRow(FeatureList list) {
      this.list = list;
      this.refreshEntry();
    }

    public void refreshEntry() {
      String id = ConnectionUtil.currentServerId();
      this.value = id.isEmpty() ? "-" : id;
    }

    @Override
    public String key() {
      return "servers:info";
    }

    @Override
    public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered,
        float partialTick) {
      net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
      graphics.text(font, Component.translatable("config.openorigins.servers_current"),
          this.getContentX(), this.getContentYMiddle() - 4, 0xFFFFFFFF);
      int w = font.width(this.value);
      graphics.text(font, Component.literal(this.value),
          this.list.getRowRight() - w, this.getContentYMiddle() - 4,
          ConnectionUtil.isConnected() ? 0xFF55FF55 : 0xFFFF5555);
    }

    @Override
    public List<? extends GuiEventListener> children() {
      return List.of();
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
      return List.of();
    }
  }

  /** Comma-separated server allowlist editor for the Servers tab. */
  /** One allowlisted address with a red remove button. */
  public static class ServerRow extends Entry {
    private final FeatureList list;
    private final String address;
    private final Button removeButton;

    public ServerRow(FeatureList list, String address) {
      this.list = list;
      this.address = address;
      this.setCollapseGroup("servers");
      this.removeButton = Button.builder(
          Component.literal("-").withStyle(net.minecraft.ChatFormatting.RED), b -> {
            OpenOriginsConfig.getInstance().removeServer(this.address);
            this.list.requestRebuild();
          }).bounds(0, 0, 20, 20).build();
    }

    public void refreshEntry() {
    }

    @Override
    public String key() {
      return "server:" + this.address;
    }

    @Override
    public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered,
        float partialTick) {
      if (this.isCollapsed()) {
        return;
      }
      int right = this.list.getRowRight();
      this.removeButton.setX(right - 20);
      this.removeButton.setY(this.getContentY() + 2);
      this.removeButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
      net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
      graphics.text(font, Component.literal(this.address), this.getContentX(),
          this.getContentYMiddle() - 4, 0xFFFFFFFF);
    }

    @Override
    public List<? extends GuiEventListener> children() {
      if (this.isCollapsed()) {
        return List.of();
      }
      return List.of(this.removeButton);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
      if (this.isCollapsed()) {
        return List.of();
      }
      return List.of(this.removeButton);
    }
  }

  /** Empty input field with a green plus button appending a new address. */
  public static class ServerAddRow extends Entry {
    private final FeatureList list;
    private final Button addButton;
    private net.minecraft.client.gui.components.EditBox field;

    public ServerAddRow(FeatureList list) {
      this.list = list;
      this.setCollapseGroup("servers");
      this.addButton = Button.builder(
          Component.literal("+").withStyle(net.minecraft.ChatFormatting.GREEN), b -> {
            if (this.field != null
                && OpenOriginsConfig.getInstance().addServer(this.field.getValue())) {
              this.field.setValue("");
            }
            this.list.requestRebuild();
          }).bounds(0, 0, 20, 20).build();
    }

    public void refreshEntry() {
    }

    @Override
    public String key() {
      return "servers:add";
    }

    @Override
    public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered,
        float partialTick) {
      if (this.isCollapsed()) {
        return;
      }
      int right = this.list.getRowRight();
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      if (this.field == null && mc != null) {
        this.field = new net.minecraft.client.gui.components.EditBox(mc.font, 0, 0, 220, 20,
            Component.translatable("config.openorigins.servers"));
        this.field.setMaxLength(128);
        this.field.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.translatable("config.openorigins.servers_hint")));
      }
      this.addButton.setX(right - 20);
      this.addButton.setY(this.getContentY() + 2);
      if (this.field != null) {
        this.field.setX(right - 20 - 4 - 220);
        this.field.setY(this.getContentY() + 2);
        this.field.extractRenderState(graphics, mouseX, mouseY, partialTick);
      }
      this.addButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
      net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
      graphics.text(font, Component.translatable("config.openorigins.servers_add"),
          this.getContentX(), this.getContentYMiddle() - 4, 0xFFFFFFFF);
    }

    @Override
    public List<? extends GuiEventListener> children() {
      if (this.isCollapsed()) {
        return List.of();
      }
      if (this.field != null) {
        return List.of(this.field, this.addButton);
      }
      return List.of(this.addButton);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
      if (this.isCollapsed()) {
        return List.of();
      }
      if (this.field != null) {
        return List.of(this.field, this.addButton);
      }
      return List.of(this.addButton);
    }
  }

}
