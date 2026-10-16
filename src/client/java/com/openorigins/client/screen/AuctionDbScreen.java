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
  private int rarityIdx;

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
    net.minecraft.client.gui.components.Button rarityBtn =
        net.minecraft.client.gui.components.Button.builder(rarityLabel(), b -> {
          this.rarityIdx = (this.rarityIdx + 1) % AuctionPriceDb.RARITIES.size();
          b.setMessage(rarityLabel());
          refresh();
        }).bounds(cx - 110, 52, 220, 20).build();
    this.addRenderableWidget(rarityBtn);
    this.list = new DbList(this.minecraft, 240, this.height - 130, 76, 24);
    this.addRenderableWidget(this.list);
    refresh();
  }

  private Component rarityLabel() {
    String r = AuctionPriceDb.RARITIES.get(this.rarityIdx);
    int n = 0;
    if ("ALL".equals(r)) {
      n = AuctionPriceDb.itemCount();
    } else {
      for (AuctionPriceDb.Entry e : AuctionPriceDb.search("", r)) {
        n++;
      }
    }
    return Component.translatable("menu.openorigins.ahdb.rarity", r, n);
  }

  private void refresh() {
    if (this.list == null) {
      return;
    }
    String q = this.search == null ? "" : this.search.getValue();
    List<AuctionPriceDb.Entry> found =
        AuctionPriceDb.search(q, AuctionPriceDb.RARITIES.get(this.rarityIdx));
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
