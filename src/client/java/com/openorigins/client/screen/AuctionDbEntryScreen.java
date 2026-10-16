package com.openorigins.client.screen;

import com.openorigins.client.util.AuctionPriceDb;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Per-entry sale history: time, price and seller of every recorded lot. */
public final class AuctionDbEntryScreen extends Screen {
  private static final DateTimeFormatter WHEN =
      DateTimeFormatter.ofPattern("MM-dd HH:mm");

  private final String key;
  private SaleList list;

  public AuctionDbEntryScreen(String key) {
    super(Component.translatable("menu.openorigins.ahdb.entry_title"));
    this.key = key;
  }

  @Override
  protected void init() {
    int cx = this.width / 2;
    this.list = new SaleList(this.minecraft, 300, this.height - 110, 40, 14);
    this.addRenderableWidget(this.list);
    List<AuctionPriceDb.Sample> samples = new ArrayList<>(AuctionPriceDb.samples(this.key));
    samples.sort((a, b) -> Long.compare(b.ts(), a.ts()));
    this.list.replaceEntries(samples.stream().map(SaleRow::new).toList());
    this.addRenderableWidget(Button.builder(Component.translatable("menu.openorigins.ahdb.back"),
        b -> Minecraft.getInstance().gui.setScreen(new AuctionDbScreen()))
        .bounds(cx - 55, this.height - 30, 110, 20).build());
  }

  private static final class SaleList extends ObjectSelectionList<SaleRow> {
    SaleList(Minecraft mc, int width, int height, int y, int itemHeight) {
      super(mc, width, height, y, itemHeight);
    }
  }

  private static final class SaleRow extends ObjectSelectionList.Entry<SaleRow> {
    private final AuctionPriceDb.Sample sample;

    SaleRow(AuctionPriceDb.Sample sample) {
      this.sample = sample;
    }

    @Override
    public Component getNarration() {
      return Component.literal(this.sample.seller());
    }

    @Override
    public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
        boolean hovered, float partialTick) {
      Minecraft mc = Minecraft.getInstance();
      String when = LocalDateTime.ofInstant(Instant.ofEpochMilli(this.sample.ts()),
          ZoneId.systemDefault()).format(WHEN);
      String line = when + "  " + this.sample.count() + "x @ "
          + AuctionPriceDb.format(this.sample.unit()) + "  "
          + (this.sample.seller().isEmpty() ? "-" : this.sample.seller());
      graphics.text(mc.font, line, this.getContentX() + 2, this.getContentY() + 3, 0xFFFFFFFF);
    }
  }
}
