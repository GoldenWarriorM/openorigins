package com.openorigins.client.screen;

import com.openorigins.client.util.AuctionPriceDb;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Searchable browser for observed auction prices (average + median). */
public final class AuctionDbScreen extends Screen {
  private EditBox search;
  private DbList list;
  private String rarity = "ALL";
  private net.minecraft.client.gui.components.Button clearBtn;
  private long clearArmedAt;
  private AuctionPriceDb.Entry detail;
  private final List<net.minecraft.client.gui.components.Button> rarityButtons =
      new java.util.ArrayList<>();

  public AuctionDbScreen() {
    super(Component.translatable("menu.openorigins.ahdb.title"));
  }

  @Override
  protected void init() {
    int cx = this.width / 2;
    this.search = new EditBox(this.font, cx - 110, 30, 220, 20,
        Component.translatable("menu.openorigins.ahdb.search_hint"));
    this.search.setMaxLength(48);
    this.search.setResponder(q -> refresh());
    this.addRenderableWidget(this.search);
    this.setInitialFocus(this.search);
    this.rarityButtons.clear();
    int sideX = cx - 260 + 438;
    int y = 56;
    for (String r : AuctionPriceDb.RARITIES) {
      final String rr = r;
      net.minecraft.client.gui.components.Button b =
          net.minecraft.client.gui.components.Button.builder(Component.empty(), btn -> {
            this.rarity = rr;
            refreshRarityButtons();
            refresh();
          }).bounds(sideX, y, 72, 20)
              .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                  Component.literal(rr)))
              .build();
      y += 22;
      this.rarityButtons.add(b);
      this.addRenderableWidget(b);
    }
    refreshRarityButtons();
    this.list = new DbList(this, this.minecraft, 230, this.height - 136, 56, 24);
    this.list.setX(cx - 260);
    this.addRenderableWidget(this.list);
    net.minecraft.client.gui.components.Button delBtn =
        net.minecraft.client.gui.components.Button.builder(
            Component.translatable("menu.openorigins.ahdb.delete"), b -> {
              Row sel = this.list.getSelected();
              if (sel != null) {
                AuctionPriceDb.remove(sel.entry.key);
                refreshRarityButtons();
                refresh();
              }
            }).bounds(cx - 112, this.height - 30, 110, 20).build();
    this.addRenderableWidget(delBtn);
    this.clearBtn = net.minecraft.client.gui.components.Button.builder(
        Component.translatable("menu.openorigins.ahdb.clear"), b -> {
          long now = System.currentTimeMillis();
          if (now - this.clearArmedAt < 5000) {
            AuctionPriceDb.clear();
            this.clearArmedAt = 0;
            this.clearBtn.setMessage(
                Component.translatable("menu.openorigins.ahdb.clear"));
            refreshRarityButtons();
            refresh();
          } else {
            this.clearArmedAt = now;
            this.clearBtn.setMessage(
                Component.translatable("menu.openorigins.ahdb.clear_sure"));
          }
        }).bounds(cx + 2, this.height - 30, 110, 20).build();
    this.addRenderableWidget(this.clearBtn);
    refresh();
  }

  private void refreshRarityButtons() {
    java.util.Map<String, Integer> counts = new java.util.HashMap<>();
    for (AuctionPriceDb.Entry e : AuctionPriceDb.search("", "ALL")) {
      counts.merge(e.rarity, 1, Integer::sum);
    }
    for (int i = 0; i < this.rarityButtons.size(); i++) {
      String r = AuctionPriceDb.RARITIES.get(i);
      int c = "ALL".equals(r) ? AuctionPriceDb.itemCount() : counts.getOrDefault(r, 0);
      String glyph = AuctionPriceDb.RARITY_GLYPHS.getOrDefault(r, "");
      String text = glyph.isEmpty() ? r + " " + c : glyph + " " + c;
      if (r.equals(this.rarity)) {
        text = "[" + text + "]";
      }
      this.rarityButtons.get(i).setMessage(Component.literal(text));
    }
  }

  private void refresh() {
    if (this.list == null) {
      return;
    }
    String q = this.search == null ? "" : this.search.getValue();
    List<AuctionPriceDb.Entry> found = AuctionPriceDb.search(q, this.rarity);
    this.list.replaceEntries(found.stream().map(Row::new).toList());
  }

  void showDetail(AuctionPriceDb.Entry entry) {
    this.detail = entry;
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
      float partialTick) {
    super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    drawDetail(graphics);
  }

  private void drawDetail(GuiGraphicsExtractor graphics) {
    AuctionPriceDb.Entry e = this.detail;
    if (e == null || this.minecraft == null) {
      return;
    }
    int x = this.width / 2 - 260 + 238;
    int y = 56;
    net.minecraft.client.gui.Font font = this.minecraft.font;
    ItemStack icon = AuctionPriceDb.iconFor(e.key);
    if (!icon.isEmpty()) {
      graphics.item(icon, x, y);
      x += 20;
    }
    Component titleName = AuctionPriceDb.styledName(e.key);
    graphics.text(font, titleName != null ? titleName
        : net.minecraft.network.chat.Component.literal(e.name), x, y + 4, 0xFFFFFFFF);
    y += 22;
    if (e.variant != null && !e.variant.isEmpty()) {
      graphics.text(font, e.variant, this.width / 2 - 260 + 238, y, 0xFFAAAAAA);
      y += 12;
    }
    AuctionPriceDb.Stats st = AuctionPriceDb.stats(e.key);
    if (st != null) {
      graphics.text(font,
          "Avg " + AuctionPriceDb.format(st.avg()) + "  Med " + AuctionPriceDb.format(st.med())
              + "  (" + st.n() + ")",
          this.width / 2 - 260 + 238, y, 0xFFAAAAAA);
      y += 14;
    }
    java.util.List<AuctionPriceDb.Sample> samples =
        new java.util.ArrayList<>(AuctionPriceDb.samples(e.key));
    samples.sort((a, b) -> Long.compare(b.ts(), a.ts()));
    int shown = 0;
    for (AuctionPriceDb.Sample s : samples) {
      if (shown >= 8 || y > this.height - 44) {
        break;
      }
      String when = java.time.LocalDateTime
          .ofInstant(java.time.Instant.ofEpochMilli(s.ts()), java.time.ZoneId.systemDefault())
          .format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"));
      graphics.text(font, when + "  " + s.count() + "x @ " + AuctionPriceDb.format(s.unit()),
          this.width / 2 - 260 + 238, y, 0xFFFFFFFF);
      y += 10;
      graphics.text(font, s.seller().isEmpty() ? "-" : s.seller(),
          this.width / 2 - 260 + 246, y, 0xFFAAAAAA);
      y += 12;
      shown++;
    }
  }

  @Override
  public void tick() {
  }

  private static final class DbList
      extends ObjectSelectionList<Row> {
    private final AuctionDbScreen parent;

    DbList(AuctionDbScreen parent, Minecraft mc, int width, int height, int y, int itemHeight) {
      super(mc, width, height, y, itemHeight);
      this.parent = parent;
    }

    @Override
    public void setSelected(Row row) {
      super.setSelected(row);
      this.parent.showDetail(row == null ? null : row.entry);
    }

    @Override
    protected int scrollBarX() {
      return this.getX() + this.getWidth() - 6;
    }
  }

  private static final class Row extends ObjectSelectionList.Entry<Row> {
    private final AuctionPriceDb.Entry entry;
    private final ItemStack icon;

    Row(AuctionPriceDb.Entry entry) {
      this.entry = entry;
      ItemStack stack = AuctionPriceDb.iconFor(entry.key);
      if (stack.isEmpty()) {
        try {
          net.minecraft.resources.Identifier id =
              net.minecraft.resources.Identifier.tryParse(entry.vanillaId);
          if (id != null) {
            net.minecraft.world.item.Item item =
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id);
            stack = new ItemStack(item, 1);
          }
        } catch (Throwable ignored) {
        }
      }
      this.icon = stack;
    }

    @Override
    public Component getNarration() {
      return Component.literal(this.entry.name);
    }

    @Override
    public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
        boolean hovered, float partialTick) {
      Minecraft mc = Minecraft.getInstance();
      int x = this.getContentX();
      int y = this.getContentY();
      if (!this.icon.isEmpty()) {
        graphics.item(this.icon, x + 2, y + 2);
      }
      AuctionPriceDb.Stats st = AuctionPriceDb.stats(this.entry.key);
      String sub = st == null ? "" : "Avg " + AuctionPriceDb.format(st.avg()) + "  Med "
          + AuctionPriceDb.format(st.med()) + "  (" + st.n() + ")";
      Component name = AuctionPriceDb.styledName(this.entry.key);
      if (name == null) {
        name = Component.literal(this.entry.name);
      }
      graphics.text(mc.font, name, x + 24, y + 2, 0xFFFFFFFF);
      graphics.text(mc.font, sub, x + 24, y + 12, 0xFFAAAAAA);
    }
  }
}
