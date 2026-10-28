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
  private SaleList detailList;
  private ExclList exclList;
  private String rarity = "ALL";
  private String searchText = "";
  private String selectedKey;
  private net.minecraft.client.gui.components.Button clearBtn;
  private long clearArmedAt;
  private AuctionPriceDb.Entry detail;
  private java.util.List<String> detailLines = List.of();
  private final List<net.minecraft.client.gui.components.Button> rarityButtons =
      new java.util.ArrayList<>();

  public AuctionDbScreen() {
    super(Component.translatable("menu.openorigins.ahdb.title"));
  }

  @Override
  protected void init() {
    int cx = this.width / 2;
    int x0 = this.panelLeft();
    this.search = new EditBox(this.font, x0, 30, 280, 20,
        Component.translatable("menu.openorigins.ahdb.search_hint"));
    this.search.setMaxLength(48);
    this.search.setValue(this.searchText);
    this.search.setResponder(q -> {
      this.searchText = q;
      refresh();
    });
    this.addRenderableWidget(this.search);
    this.setInitialFocus(this.search);
    this.rarityButtons.clear();
    int sideX = x0 + 508;
    int y = 30;
    for (String r : AuctionPriceDb.RARITIES) {
      final String rr = r;
      net.minecraft.client.gui.components.Button b =
          net.minecraft.client.gui.components.Button.builder(Component.empty(), btn -> {
            this.rarity = rr;
            refreshRarityButtons();
            refresh();
          }).bounds(sideX, y, 100, 20)
              .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                  Component.translatable("menu.openorigins.ahdb.rarity", rr)))
              .build();
      y += 22;
      this.rarityButtons.add(b);
      this.addRenderableWidget(b);
    }
    refreshRarityButtons();
    this.list = new DbList(this, this.minecraft, 280, this.height - 100, 56, 24);
    this.list.setX(x0);
    this.addRenderableWidget(this.list);
    this.detailList = new SaleList(this.minecraft, 212, this.height - 194, 150, 22);
    this.detailList.setX(x0 + 288);
    this.addRenderableWidget(this.detailList);
    int exX = x0 + 508;
    int exTop = 30 + AuctionPriceDb.RARITIES.size() * 22 + 6;
    this.exclList = new ExclList(this.minecraft, 100,
        Math.max(40, this.height - 44 - (exTop + 22)), exTop + 22, 12);
    this.exclList.setX(exX);
    this.addRenderableWidget(this.exclList);
    refreshExcluded();
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
    if (this.selectedKey != null) {
      for (Row row : this.list.children()) {
        if (row.entry.key.equals(this.selectedKey)) {
          this.list.setSelected(row);
          showDetail(row.entry);
          break;
        }
      }
    }
    // Restore scroll points across re-init (window resize).
    if (this.savedScrolls != null) {
      this.list.setScrollAmount(this.savedScrolls[0]);
      this.detailList.setScrollAmount(this.savedScrolls[1]);
      this.exclList.setScrollAmount(this.savedScrolls[2]);
      this.savedScrolls = null;
    }
  }

  @Override
  public void resize(int width, int height) {
    this.savedScrolls = new double[] {
        this.list == null ? 0 : this.list.scrollAmount(),
        this.detailList == null ? 0 : this.detailList.scrollAmount(),
        this.exclList == null ? 0 : this.exclList.scrollAmount(),
    };
    super.resize(width, height);
  }

  private double[] savedScrolls;

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
    this.selectedKey = entry == null ? null : entry.key;
    this.detailLines = List.of();
    if (entry != null) {
      String desc = AuctionPriceDb.descText(entry.key);
      if (!desc.isEmpty()) {
        List<String> lines = new java.util.ArrayList<>();
        for (String dl : desc.split("\n")) {
          if (lines.size() >= 4) {
            break;
          }
          String line = dl.strip();
          if (line.isEmpty()) {
            continue;
          }
          lines.add(line.length() > 30 ? line.substring(0, 30) : line);
        }
        this.detailLines = List.copyOf(lines);
      }
    }
    int y = 30 + 22;
    if (!this.detailLines.isEmpty()) {
      y += this.detailLines.size() * 10 + 2;
    }
    if (entry != null && AuctionPriceDb.stats(entry.key) != null) {
      y += 14;
    }
    if (this.detailList != null) {
      this.detailList.setY(y + 6);
      this.detailList.setHeight(Math.max(40, this.height - 44 - (y + 6)));
    }
    refreshDetailList();
  }

  private void refreshDetailList() {
    if (this.detailList == null) {
      return;
    }
    if (this.detail == null) {
      this.detailList.replaceEntries(List.of());
      return;
    }
    java.util.List<AuctionPriceDb.Sample> samples =
        new java.util.ArrayList<>(AuctionPriceDb.samples(this.detail.key));
    samples.sort((a, b) -> Long.compare(b.ts(), a.ts()));
    this.detailList.replaceEntries(samples.stream().map(s -> new SaleRow(this, s)).toList());
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
      float partialTick) {
    super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    drawDetailHeader(graphics);
    if (AuctionPriceDb.excludedNicks().isEmpty() && this.minecraft != null
        && this.exclList != null) {
      graphics.text(this.minecraft.font,
          Component.translatable("menu.openorigins.ahdb.excl_hint1"),
          this.exclList.getX() + 4, this.exclList.getY() + 4, 0xFF777777);
      graphics.text(this.minecraft.font,
          Component.translatable("menu.openorigins.ahdb.excl_hint2"),
          this.exclList.getX() + 4, this.exclList.getY() + 14, 0xFF777777);
    }
  }

  /** Left edge of the 616-wide DB grid; at narrow windows it hugs the
   *  screen edge instead of drawing off-screen. */
  private int panelLeft() {
    return Math.max(4, this.width / 2 - 308);
  }

  private void drawDetailHeader(GuiGraphicsExtractor graphics) {
    AuctionPriceDb.Entry e = this.detail;
    if (e == null || this.minecraft == null) {
      return;
    }
    int x = this.panelLeft() + 288;
    int y = 30;
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
    for (String line : this.detailLines) {
      graphics.text(font, line, this.panelLeft() + 288, y, 0xFFAAAAAA);
      y += 10;
    }
    if (!this.detailLines.isEmpty()) {
      y += 2;
    }
    AuctionPriceDb.Stats st = AuctionPriceDb.stats(e.key);
    if (st != null) {
      graphics.text(font,
          "Avg " + AuctionPriceDb.format(st.avg()) + "  Med " + AuctionPriceDb.format(st.med())
              + "  (" + st.n() + ") \ua8a6",
          (this.panelLeft() + 288), y, 0xFFAAAAAA);
    }
  }

  /** Base for the DB's three lists: rows span the full widget width
   *  (vanilla hardcodes 220, clipping the left side). */
  private static abstract class FullRowList<R extends ObjectSelectionList.Entry<R>>
      extends ObjectSelectionList<R> {
    FullRowList(Minecraft mc, int width, int height, int y, int itemHeight) {
      super(mc, width, height, y, itemHeight);
    }

    @Override
    public int getRowWidth() {
      return this.getWidth();
    }

    /** Vanilla draws the bar just right of the rows, i.e. outside the widget,
     *  where it overlaps the next column or leaves the screen. Keep it inside. */
    @Override
    protected int scrollBarX() {
      return this.getX() + this.getWidth() - this.scrollbarWidth();
    }

    /** A press on the bar drags the list; it must not also activate the row
     *  underneath (rows toggle ignore lists on any click). */
    @Override
    public java.util.Optional<net.minecraft.client.gui.components.events.GuiEventListener> getChildAt(
        double x, double y) {
      if (this.isOverScrollbar(x, y)) {
        return java.util.Optional.empty();
      }
      return super.getChildAt(x, y);
    }
  }

  private static final class SaleList extends FullRowList<SaleRow> {
    SaleList(Minecraft mc, int width, int height, int y, int itemHeight) {
      super(mc, width, height, y, itemHeight);
    }
  }

  private static final class ExclList extends FullRowList<ExclRow> {
    ExclList(Minecraft mc, int width, int height, int y, int itemHeight) {
      super(mc, width, height, y, itemHeight);
    }
  }

  private static final class ExclRow extends ObjectSelectionList.Entry<ExclRow> {
    private final AuctionDbScreen parent;
    private final String nick;

    ExclRow(AuctionDbScreen parent, String nick) {
      this.parent = parent;
      this.nick = nick;
    }

    @Override
    public Component getNarration() {
      return Component.literal(this.nick);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,
        boolean doubleClick) {
      if (event.button() != com.openorigins.client.util.VersionCompat.mouseLeft()) {
        return false;
      }
      AuctionPriceDb.setExcluded(this.nick, false);
      this.parent.refreshExcluded();
      this.parent.refreshDetailList();
      this.parent.refresh();
      return true;
    }

    @Override
    public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
        boolean hovered, float partialTick) {
      Minecraft mc = Minecraft.getInstance();
      graphics.text(mc.font, "✕ " + this.nick, this.getContentX() + 2,
          this.getContentY() + 2, 0xFFAAAAAA);
    }
  }

  void refreshExcluded() {
    if (this.exclList == null) {
      return;
    }
    java.util.List<String> nicks =
        new java.util.ArrayList<>(AuctionPriceDb.excludedNicks());
    nicks.sort(String::compareToIgnoreCase);
    this.exclList.replaceEntries(nicks.stream().map(n -> new ExclRow(this, n)).toList());
  }

  private static final class SaleRow extends ObjectSelectionList.Entry<SaleRow> {
    private final AuctionDbScreen parent;
    private final AuctionPriceDb.Sample sample;
    private final String when;
    private final String nick;
    private static final java.time.format.DateTimeFormatter WHEN_FMT =
        java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm");

    SaleRow(AuctionDbScreen parent, AuctionPriceDb.Sample sample) {
      this.parent = parent;
      this.sample = sample;
      this.when = java.time.LocalDateTime
          .ofInstant(java.time.Instant.ofEpochMilli(sample.ts()),
              java.time.ZoneId.systemDefault())
          .format(WHEN_FMT);
      String rawSeller = sample.seller().isEmpty() ? "-" : sample.seller();
      int sp = rawSeller.lastIndexOf(' ');
      this.nick = sp >= 0 ? rawSeller.substring(sp + 1) : rawSeller;
    }

    @Override
    public Component getNarration() {
      return Component.literal(this.sample.seller());
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,
        boolean doubleClick) {
      if (event.button() != com.openorigins.client.util.VersionCompat.mouseLeft()) {
        return false;
      }
      AuctionPriceDb.setExcluded(this.sample.seller(),
          !AuctionPriceDb.isExcluded(this.sample.seller()));
      this.parent.refreshDetailList();
      this.parent.refreshExcluded();
      this.parent.refresh();
      return true;
    }

    @Override
    public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
        boolean hovered, float partialTick) {
      Minecraft mc = Minecraft.getInstance();
      boolean out = AuctionPriceDb.isExcluded(this.sample.seller());
      int main = out ? 0xFF777777 : 0xFFFFFFFF;
      String when = this.when;
      String mark = out ? "✕ " : "";
      graphics.text(mc.font,
          mark + when + "  " + this.sample.count() + "x @ "
              + AuctionPriceDb.format(this.sample.unit()) + " \ua8a6",
          this.getContentX() + 2, this.getContentY() + 1, main);
      graphics.text(mc.font, this.nick,
          this.getContentX() + 10, this.getContentY() + 11, out ? 0xFF777777 : 0xFFAAAAAA);
    }
  }

  private static final class DbList
      extends FullRowList<Row> {
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
  }

  private static final class Row extends ObjectSelectionList.Entry<Row> {
    private final AuctionPriceDb.Entry entry;
    private final ItemStack icon;
    private String subText;
    private Component subName;
    private long subAt;

    Row(AuctionPriceDb.Entry entry) {
      this.entry = entry;
      ItemStack stack = AuctionPriceDb.iconFor(entry.key);
      if (stack.isEmpty()) {
        stack = DbIconCache.vanilla(entry.key, entry.vanillaId);
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
      // One refresh per second: stats() walks every sample, not per frame.
      long now = System.currentTimeMillis();
      if (this.subText == null || now - this.subAt > 1000) {
        this.subAt = now;
        AuctionPriceDb.Stats st = AuctionPriceDb.stats(this.entry.key);
        this.subText = st == null ? "" : "Avg " + AuctionPriceDb.format(st.avg()) + "  Med "
            + AuctionPriceDb.format(st.med()) + "  (" + st.n() + ")";
        Component nm = AuctionPriceDb.styledName(this.entry.key);
        this.subName = nm == null ? Component.literal(this.entry.name) : nm;
      }
      graphics.text(mc.font, this.subName, x + 24, y + 2, 0xFFFFFFFF);
      graphics.text(mc.font, this.subText, x + 24, y + 12, 0xFFAAAAAA);
    }
  }
}
