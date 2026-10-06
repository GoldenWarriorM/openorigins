package com.openorigins.client.screen;

import com.openorigins.client.OpenOriginsConfig;
import com.openorigins.client.OriginFeature;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

public class FeatureList extends ContainerObjectSelectionList<FeatureList.Entry> {
  public FeatureList(Minecraft minecraft, int width, int contentHeight, int headerHeight,
      ConfigTab tab) {
    super(minecraft, width, contentHeight, headerHeight, 24);
    for (com.openorigins.client.FeatureCategory category : tab.getCategories()) {
      this.addEntry(new CategoryEntry(this, category));
      for (OriginFeature feature : OriginFeature.values()) {
        if (feature.getCategory() == category) {
          this.addEntry(new FeatureEntry(this, feature));
          if (feature == OriginFeature.BADGER_TASK_HUD) {
            this.addEntry(new CycleRow(this, "config.openorigins.position",
                "config.openorigins.hud_position_hint", FeatureList::cornerName,
                FeatureList::cycleCorner, true));
          }
        }
      }
      if (category == com.openorigins.client.FeatureCategory.UI) {
        this.addEntry(new StepperRow(this, "config.openorigins.max_slots",
            "config.openorigins.capacity_hint", 0, 15,
            () -> OpenOriginsConfig.getInstance().getHomeCapacity(),
            v -> OpenOriginsConfig.getInstance().setHomeCapacity(v)));
      }
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

  @Override
  public int getRowWidth() {
    return 340;
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
      Component name = Component.translatable(this.category.getTranslationKey())
          .withStyle(net.minecraft.ChatFormatting.GOLD);
      int w = font.width(name);
      int rowW = this.list.getRowRight() - this.getContentX();
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
      this.refreshEntry();
    }

    public void refreshEntry() {
      boolean on = OpenOriginsConfig.getInstance().isEnabled(this.feature);
      this.toggleButton.setMessage(Component.translatable(on ? "options.on" : "options.off")
          .withStyle(on ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED));
    }

    private static Component featureTooltip(OriginFeature feature) {
      return Component.translatable(feature.getDescriptionKey());
    }

    @Override
    public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered,
        float partialTick) {
      this.toggleButton.setX(this.list.getRowRight() - this.toggleButton.getWidth());
      this.toggleButton.setY(this.getContentY() + 2);
      this.toggleButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
      net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
      graphics.text(font, Component.literal(this.feature.getName()), this.getContentX(),
          this.getContentYMiddle() - 4, 0xFFFFFFFF);
    }

    @Override
    public List<? extends GuiEventListener> children() {
      return List.of(this.toggleButton);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
      return List.of(this.toggleButton);
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

    public CycleRow(FeatureList list, String labelKey, String tooltipKey,
        java.util.function.Supplier<String> value, Runnable cycle, boolean indented) {
      this.list = list;
      this.labelKey = labelKey;
      this.tooltipKey = tooltipKey;
      this.indented = indented;
      this.value = value;
      this.cycle = cycle;
      this.button = Button.builder(Component.empty(), b -> {
        this.cycle.run();
        this.list.refreshEntries();
      }).bounds(0, 0, 100, 20)
          .tooltip(net.minecraft.client.gui.components.Tooltip.create(
              Component.translatable(this.tooltipKey)))
          .build();
      this.refreshEntry();
    }

    public void refreshEntry() {
      this.button.setMessage(Component.literal(this.value.get()));
    }

    @Override
    public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered,
        float partialTick) {
      this.button.setX(this.list.getRowRight() - this.button.getWidth());
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
      return List.of(this.button);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
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
    private net.minecraft.client.gui.components.EditBox field;

    public StepperRow(FeatureList list, String labelKey, String tooltipKey, int min, int max,
        java.util.function.IntSupplier get, java.util.function.IntConsumer set) {
      this.list = list;
      this.labelKey = labelKey;
      this.min = min;
      this.max = max;
      this.get = get;
      this.set = set;
      this.minusButton = Button.builder(Component.literal("-"), b -> {
        this.set.accept(Math.max(this.min, this.get.getAsInt() - 1));
        this.list.refreshEntries();
      }).bounds(0, 0, 20, 20).build();
      this.plusButton = Button.builder(Component.literal("+"), b -> {
        this.set.accept(Math.min(this.max, this.get.getAsInt() + 1));
        this.list.refreshEntries();
      }).bounds(0, 0, 20, 20).build();
    }

    public void refreshEntry() {
      if (this.field != null) {
        String v = Integer.toString(this.get.getAsInt());
        if (!this.field.getValue().equals(v)) {
          this.field.setValue(v);
        }
      }
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
        this.field.setMaxLength(2);
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
              }
            } catch (NumberFormatException ignored) {
            }
          }
        });
      }
      this.plusButton.setX(right - 20);
      this.plusButton.setY(this.getContentY() + 2);
      if (this.field != null) {
        this.field.setX(right - 20 - 4 - 52);
        this.field.setY(this.getContentY() + 2);
        this.field.extractRenderState(graphics, mouseX, mouseY, partialTick);
        net.minecraft.client.gui.Font ff = net.minecraft.client.Minecraft.getInstance().font;
        String fv = this.field.getValue();
        int fw = ff.width(fv);
        graphics.text(ff, fv, right - 20 - 4 - 52 + (52 - fw) / 2,
            this.getContentYMiddle() - 4, 0xFFFFFFFF);
      }
      this.minusButton.setX(right - 20 - 4 - 52 - 4 - 20);
      this.minusButton.setY(this.getContentY() + 2);
      this.plusButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
      this.minusButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
      net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
      graphics.text(font, Component.translatable(this.labelKey), this.getContentX(),
          this.getContentYMiddle() - 4, 0xFFFFFFFF);
    }

    @Override
    public List<? extends GuiEventListener> children() {
      if (this.field != null) {
        return List.of(this.minusButton, this.field, this.plusButton);
      }
      return List.of(this.minusButton, this.plusButton);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
      if (this.field != null) {
        return List.of(this.minusButton, this.field, this.plusButton);
      }
      return List.of(this.minusButton, this.plusButton);
    }
  }

}
