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
    int sideX = cx + 128;
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
    this.list = new DbList(this.minecraft, 240, this.height - 116, 56, 24);
    this.addRenderableWidget(this.list);
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

  @Override
  public void tick() {
  }

  private static final class DbList
      extends ObjectSelectionList<Row> {
    DbList(Minecraft mc, int width, int height, int y, int itemHeight) {
      super(mc, width, height, y, itemHeight);
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
      graphics.text(mc.font, this.entry.name, x + 24, y + 2, 0xFFFFFFFF);
      graphics.text(mc.font, sub, x + 24, y + 12, 0xFFAAAAAA);
    }
  }
}
