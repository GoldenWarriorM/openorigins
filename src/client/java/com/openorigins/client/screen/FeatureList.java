package com.openorigins.client.screen;

import com.openorigins.client.ConnectionUtil;
import com.openorigins.client.OpenOriginsConfig;
import com.openorigins.client.OriginFeature;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

public class FeatureList extends ContainerObjectSelectionList<FeatureList.Entry> {
  private static final Identifier RESET_ICON =
      Identifier.fromNamespaceAndPath("openorigins", "reset");
  private static final int ROW_H = 24;
  private int serverStart = -1;
  private ConfigTab jumpTab;
  private double jumpScroll;

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

  public FeatureList(Minecraft minecraft, int width, int contentHeight, int headerHeight,
      ConfigTab tab) {
    super(minecraft, width, contentHeight, headerHeight, ROW_H);
    // Single feed: every tab page contains all sections; the opener tab only
    // decides the initial scroll position (tabs teleport, nothing is hidden).
    for (ConfigTab t : ConfigTab.values()) {
      for (com.openorigins.client.FeatureCategory category : t.getCategories()) {
        this.addEntry(new CategoryEntry(this, category));
      for (OriginFeature feature : OriginFeature.values()) {
        if (feature.getCategory() == category) {
          if (feature.isHidden()) {
            continue;
          }
          this.addEntry(new FeatureEntry(this, feature));
          if (feature == OriginFeature.BADGER_TASK_HUD) {
            this.addEntry(new CycleRow(this, "config.openorigins.position",
                "config.openorigins.hud_position_hint", FeatureList::cornerName,
                FeatureList::cycleCorner, true,
                () -> OpenOriginsConfig.getInstance().setBadgerHudCorner(
                    OpenOriginsConfig.HudCorner.TOP_LEFT),
                () -> OpenOriginsConfig.getInstance().getBadgerHudCorner()
                    == OpenOriginsConfig.HudCorner.TOP_LEFT));
          }
          if (feature == OriginFeature.ENTITY_EXTRAPOLATION) {
            this.addEntry(new StepperRow(this, "config.openorigins.extrap_ms",
                "config.openorigins.extrap_ms_hint", 0, 1000,
                () -> OpenOriginsConfig.getInstance().getExtrapMs(),
                v -> OpenOriginsConfig.getInstance().setExtrapMs(v), 150, true));
          }
        }
      }
      if (category == com.openorigins.client.FeatureCategory.UI) {
        this.addEntry(new StepperRow(this, "config.openorigins.max_slots",
            "config.openorigins.capacity_hint", 0, 15,
            () -> OpenOriginsConfig.getInstance().getHomeCapacity(),
            v -> OpenOriginsConfig.getInstance().setHomeCapacity(v), 5));
      }
      if (category == com.openorigins.client.FeatureCategory.MISC) {
        this.addEntry(new CycleRow(this, "config.openorigins.scan_key",
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
          this.addEntry(new CycleRow(this, h.labelKey(), h.labelKey(),
              () -> com.openorigins.client.HotkeyFeatures.isCapturing(h.mapping()) ? "..."
                  : com.openorigins.client.HotkeyFeatures.keyName(h.mapping()),
              () -> com.openorigins.client.HotkeyFeatures.beginCapture(h.mapping()), false,
              () -> h.mapping().setKey(h.mapping().getDefaultKey()),
              () -> h.mapping().isDefault()));
        }
      }
      if (category == com.openorigins.client.FeatureCategory.SERVERS) {
        this.serverStart = this.children().size();
      }
      }
    }
    this.addServerRows();
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
  public void scrollToSection(ConfigTab tab) {    try {
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

  /** Rebuilds only the Servers section at the end of the feed. */
  public void rebuildServerEntries() {
    try {
      if (this.serverStart < 0) {
        return;
      }
      java.util.List<Entry> kids = this.children();
      while (kids.size() > this.serverStart) {
        this.removeEntry(kids.get(kids.size() - 1));
      }
    } catch (Throwable ignored) {
    }
    this.addServerRows();
  }

  private void addServerRows() {
    this.addEntry(new ServerInfoRow(this));
    this.addEntry(new ToggleRow(this, "config.openorigins.use_handshake",
        "config.openorigins.use_handshake_hint",
        () -> OpenOriginsConfig.getInstance().isUseHandshake(),
        () -> {
          OpenOriginsConfig.getInstance().setUseHandshake(
              !OpenOriginsConfig.getInstance().isUseHandshake());
          this.rebuildServerEntries();
        },
        true,
        v -> {
          OpenOriginsConfig.getInstance().setUseHandshake(v);
          this.rebuildServerEntries();
        }));
    if (!OpenOriginsConfig.getInstance().isUseHandshake()) {
      for (String s : OpenOriginsConfig.getInstance().getServers()) {
        this.addEntry(new ServerRow(this, s));
      }
      this.addEntry(new ServerAddRow(this));
    }
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
    public abstract void refreshEntry();
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
    private final Button toggleButton;
    private final Button resetButton;

    public FeatureEntry(FeatureList list, OriginFeature feature) {
      this.list = list;
      this.feature = feature;
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
          .width(20).sprite(RESET_ICON, 14, 14).withTootip().build();
      this.refreshEntry();
    }

    public void refreshEntry() {
      boolean on = OpenOriginsConfig.getInstance().isEnabled(this.feature);
      this.toggleButton.setMessage(Component.translatable(on ? "options.on" : "options.off")
          .withStyle(on ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED));
      this.resetButton.active = on != this.feature.getDefaultValue();
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
      net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
      graphics.text(font, Component.literal(this.feature.getName()), this.getContentX(),
          this.getContentYMiddle() - 4, 0xFFFFFFFF);
    }

    @Override
    public List<? extends GuiEventListener> children() {
      return List.of(this.toggleButton, this.resetButton);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
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
    private final Button button;
    private final Button resetButton;

    public ToggleRow(FeatureList list, String labelKey, String tooltipKey,
        java.util.function.BooleanSupplier get, Runnable onPress) {
      this(list, labelKey, tooltipKey, get, onPress, false, v -> {});
    }

    public ToggleRow(FeatureList list, String labelKey, String tooltipKey,
        java.util.function.BooleanSupplier get, Runnable onPress,
        boolean defaultValue, java.util.function.Consumer<Boolean> set) {
      this.list = list;
      this.labelKey = labelKey;
      this.tooltipKey = tooltipKey;
      this.get = get;
      this.onPress = onPress;
      this.defaultValue = defaultValue;
      this.set = set;
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
          .width(20).sprite(RESET_ICON, 14, 14).withTootip().build();
      this.refreshEntry();
    }

    public void refreshEntry() {
      boolean on = this.get.getAsBoolean();
      this.button.setMessage(Component.translatable(on ? "options.on" : "options.off")
          .withStyle(on ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED));
      this.resetButton.active = on != this.defaultValue;
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
      net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
      graphics.text(font, Component.translatable(this.labelKey), this.getContentX(),
          this.getContentYMiddle() - 4, 0xFFFFFFFF);
    }

    @Override
    public List<? extends GuiEventListener> children() {
      return List.of(this.button, this.resetButton);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
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
          .width(20).sprite(RESET_ICON, 14, 14).withTootip().build();
      this.refreshEntry();
    }

    public void refreshEntry() {
      this.button.setMessage(Component.literal(this.value.get()));
      this.resetButton.active = this.reset != null && !this.isDefault.getAsBoolean();
    }

    @Override
    public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered,
        float partialTick) {
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
        label = Component.literal("  ").append(label);
      }
      graphics.text(font, label, this.getContentX(),
          this.getContentYMiddle() - 4, 0xFFFFFFFF);
    }

    @Override
    public List<? extends GuiEventListener> children() {
      if (this.reset != null) {
        return List.of(this.button, this.resetButton);
      }
      return List.of(this.button);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
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
          .width(20).sprite(RESET_ICON, 14, 14).withTootip().build();
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
    public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered,
        float partialTick) {
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
        label = Component.literal("  ").append(label);
      }
      graphics.text(font, label, this.getContentX(),
          this.getContentYMiddle() - 4, 0xFFFFFFFF);
    }

    @Override
    public List<? extends GuiEventListener> children() {
      if (this.field != null) {
        return List.of(this.minusButton, this.field, this.plusButton, this.resetButton);
      }
      return List.of(this.minusButton, this.plusButton, this.resetButton);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
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
      this.removeButton = Button.builder(
          Component.literal("-").withStyle(net.minecraft.ChatFormatting.RED), b -> {
            OpenOriginsConfig.getInstance().removeServer(this.address);
            this.list.rebuildServerEntries();
          }).bounds(0, 0, 20, 20).build();
    }

    public void refreshEntry() {
    }

    @Override
    public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered,
        float partialTick) {
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
      return List.of(this.removeButton);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
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
      this.addButton = Button.builder(
          Component.literal("+").withStyle(net.minecraft.ChatFormatting.GREEN), b -> {
            if (this.field != null
                && OpenOriginsConfig.getInstance().addServer(this.field.getValue())) {
              this.field.setValue("");
            }
            this.list.rebuildServerEntries();
          }).bounds(0, 0, 20, 20).build();
    }

    public void refreshEntry() {
    }

    @Override
    public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered,
        float partialTick) {
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
      if (this.field != null) {
        return List.of(this.field, this.addButton);
      }
      return List.of(this.addButton);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
      if (this.field != null) {
        return List.of(this.field, this.addButton);
      }
      return List.of(this.addButton);
    }
  }

}
