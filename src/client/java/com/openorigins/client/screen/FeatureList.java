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
  public FeatureList(Minecraft minecraft, int width, int contentHeight, int headerHeight) {
    super(minecraft, width, contentHeight, headerHeight, 24);
    for (OriginFeature feature : OriginFeature.values()) {
      this.addEntry(new FeatureEntry(this, feature));
    }
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
      Component desc = Component.translatable(feature.getDescriptionKey());
      if (feature == OriginFeature.GHOST_BLOCK_FIX) {
        return desc.copy().append("\n").append(Component.translatable("label.openorigins.experimental")
            .withStyle(net.minecraft.ChatFormatting.YELLOW));
      }
      return desc;
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
}
