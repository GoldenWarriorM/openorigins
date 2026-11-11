package com.openorigins.client;

import com.openorigins.client.mixin.BossBarAccessor;
import com.openorigins.client.util.BadgerCache;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Corner-anchored HUD with the active badger task requirements. */
public final class BadgerHud implements net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement {
  public static final Identifier ID = Identifier.fromNamespaceAndPath("openorigins", "badger_task");

  public record Row(ItemStack icon, Component text, int color) {}

  /** Placeholder layout for the position editor when no task data exists. */
  public static List<Row> sampleRows() {
    List<Row> rows = new ArrayList<>();
    rows.add(new Row(null, Component.literal("Badger Task (streak 13)"), 0xFFFFFFFF));
    rows.add(new Row(null, Component.literal("Red Mushroom 0/2"), 0xFFFFFFFF));
    rows.add(new Row(null, Component.literal("Coal 0/10"), 0xFFFFFFFF));
    rows.add(new Row(null, Component.literal("Lapis Lazuli 0/6"), 0xFFFFFFFF));
    rows.add(new Row(null, styledRow("Basic", "(2/5)"), 0xFFFFFFFF));
    rows.add(new Row(null, Component.translatable("hud.openorigins.badger_timeleft",
        styledValue("7h 56m")), 0xFFAAAAAA));
    rows.add(new Row(null, Component.translatable("hud.openorigins.badger_reset",
        styledValue("6d")), 0xFFAAAAAA));
    return rows;
  }

  /** Category label gray, live value white — stage and timer rows. */
  static Component styledRow(String label, String value) {
    return Component.literal(label + ": ").withStyle(net.minecraft.ChatFormatting.GRAY)
        .append(Component.literal(value).withStyle(net.minecraft.ChatFormatting.WHITE));
  }

  /** Value part of a translatable "X: %s" row. */
  static Component styledValue(String value) {
    return Component.literal(value).withStyle(net.minecraft.ChatFormatting.WHITE);
  }

  /** True while the position editor is open (live HUD stays out of the way). */
  public static boolean previewActive;

  /**
   * HUD rows are rebuilt only when their input changes. Inventory packets and
   * menu clicks mark the counts dirty, so a moved item shows up on the same
   * frame. The periodic rescan is a safety net for changes that bypass those
   * hooks (other mods, direct writes) — it only recomputes counts, not rows.
   */
  private static final long SAFETY_RESCAN_MS = 250;
  /**
   * Login and respawn replace the client player with an empty inventory; the
   * server fills it back in with the container-content packet. Until that
   * packet arrives the counts would read 0, so the last known rows stay up.
   */
  private static final long INVENTORY_SYNC_GRACE_MS = 3000;
  private static volatile boolean inventoryDirty = true;
  private static boolean inventoryPending;
  private static long inventoryPendingSince;
  private static long lastRescanMs;
  private static List<Row> cachedRows = List.of();
  private static int cachedTaskSig;
  private static int cachedSig;
  private static boolean hasCachedRows;

  /** Called from the inventory packet/click hooks. */
  public static void noteInventoryChanged() {
    inventoryDirty = true;
    inventoryPending = false;
  }

  /**
   * Called from the login/respawn hooks: a fresh player has no inventory yet.
   * Rows are held back until the server's inventory packet arrives, so the HUD
   * never flashes "0/3" after a join or a dimension change.
   */
  public static void notePlayerReplaced() {
    inventoryPending = true;
    inventoryPendingSince = System.currentTimeMillis();
  }

  public static List<Row> buildRows(Minecraft mc, Font font) {
    long now = System.currentTimeMillis();
    if (inventoryPending) {
      if (now - inventoryPendingSince < INVENTORY_SYNC_GRACE_MS) {
        return cachedRows;
      }
      inventoryPending = false;
      inventoryDirty = true;
    }
    int taskSig = taskSignature();
    boolean rescan = inventoryDirty || now - lastRescanMs >= SAFETY_RESCAN_MS;
    if (hasCachedRows && taskSig == cachedTaskSig) {
      if (!rescan) {
        return cachedRows;
      }
      if (31 * taskSig + countsHash(mc) == cachedSig) {
        inventoryDirty = false;
        lastRescanMs = now;
        return cachedRows;
      }
    }
    List<Row> rows = buildRows0(mc, font);
    cachedRows = rows;
    cachedTaskSig = taskSig;
    cachedSig = 31 * taskSig + countsHash(mc);
    hasCachedRows = true;
    inventoryDirty = false;
    lastRescanMs = System.currentTimeMillis();
    return rows;
  }

  private static int taskSignature() {
    int h = 1;
    h = 31 * h + BadgerCache.requiredView().hashCode();
    h = 31 * h + BadgerCache.streakScore();
    h = 31 * h + BadgerCache.daysUntilReset();
    h = 31 * h + Long.hashCode(BadgerCache.taskMinutesLeft());
    h = 31 * h + BadgerCache.tiersView().hashCode();
    // The same task list re-captured from the menu brings the exact item
    // stacks (custom textures); without this a cached row keeps the plain
    // fallback icon until the list itself changes.
    h = 31 * h + BadgerCache.iconsVersion();
    return h;
  }

  private static int countsHash(Minecraft mc) {
    int h = 1;
    for (BadgerCache.Required r : BadgerCache.requiredView()) {
      h = 31 * h + countInInventory(mc, r);
    }
    return h;
  }

  private static List<Row> buildRows0(Minecraft mc, Font font) {
    List<Row> rows = new ArrayList<>();
    List<BadgerCache.Required> req = BadgerCache.required();
    if (req.isEmpty()) {
      return rows;
    }
    rows.add(new Row(null, Component.translatable("hud.openorigins.badger_task",
        BadgerCache.streakScore()), 0xFFFFFFFF));
    for (BadgerCache.Required r : req) {
      ItemStack stack = BadgerCache.iconFor(r);
      if (stack.isEmpty()) {
        continue;
      }
      int have = countInInventory(mc, r);
      boolean done = have >= r.count();
      String labelName = r.displayName().isEmpty() ? stack.getHoverName().getString() : r.displayName();
      rows.add(new Row(stack,
          Component.literal(labelName + " " + have + "/" + r.count()),
          done ? 0xFF84BA4A : 0xFFFFFFFF));
    }
    // Quest stage the player is on, e.g. "Basic: (2/5)" — label gray, value white.
    for (BadgerCache.Tier t : BadgerCache.tiers()) {
      rows.add(new Row(null, styledRow(t.name(), "(" + t.done() + "/" + t.total() + ")"),
          0xFFFFFFFF));
    }
    if (BadgerCache.daysUntilReset() > 0) {
      rows.add(new Row(null, Component.translatable("hud.openorigins.badger_reset",
          styledValue(BadgerCache.daysUntilReset() + "d")), 0xFFAAAAAA));
    }
    if (!BadgerCache.taskTimeLeft().isEmpty()) {
      rows.add(rows.size() - (BadgerCache.daysUntilReset() > 0 ? 1 : 0),
          new Row(null, Component.translatable("hud.openorigins.badger_timeleft",
              styledValue(com.openorigins.client.util.OpenWorldCache.shortDuration(
                  BadgerCache.taskTimeLeft()))), 0xFFAAAAAA));
    }
    return rows;
  }

  public static int maxWidth(Font font, List<Row> rows) {
    int maxW = 0;
    for (Row row : rows) {
      int w = (row.icon() != null ? 19 : 0) + font.width(row.text());
      if (w > maxW) {
        maxW = w;
      }
    }
    return maxW;
  }

  public static int totalHeight(List<Row> rows) {
    if (rows.isEmpty()) {
      return 0;
    }
    int totalH = 11;
    for (int i = 1; i < rows.size(); i++) {
      totalH += rows.get(i).icon() != null ? 20 : 12;
    }
    return totalH;
  }

  /** Box top-left for an anchor corner, offsets and content size. */
  public static int[] boxAt(OpenOriginsConfig.HudCorner corner, int dx, int dy, int maxW,
      int totalH, int sw, int sh) {
    int x = switch (corner) {
      case TOP_RIGHT, BOTTOM_RIGHT -> sw - dx - maxW;
      default -> dx;
    };
    int y = switch (corner) {
      case BOTTOM_LEFT, BOTTOM_RIGHT -> sh - dy - totalH;
      default -> dy;
    };
    return new int[]{x, y};
  }

  public static void drawBox(GuiGraphicsExtractor graphics, Font font, List<Row> rows, int x,
      int y, boolean backdrop) {
    drawBox(graphics, font, rows, x, y, backdrop, maxWidth(font, rows), totalHeight(rows));
  }

  public static void drawBox(GuiGraphicsExtractor graphics, Font font, List<Row> rows, int x,
      int y, boolean backdrop, int maxW, int totalH) {
    if (backdrop) {
      int pad = 3;
      int cut = 1;
      int bx0 = x - pad;
      int by0 = y - pad;
      int bx1 = x + maxW + pad;
      int by1 = y + totalH + pad;
      int bg = 0x80000000;
      graphics.fill(bx0 + cut, by0, bx1 - cut, by1, bg);
      graphics.fill(bx0, by0 + cut, bx0 + cut, by1 - cut, bg);
      graphics.fill(bx1 - cut, by0 + cut, bx1, by1 - cut, bg);
    }
    graphics.text(font, rows.get(0).text(), x + (rows.get(0).icon() != null ? 19 : 0), y,
        rows.get(0).color());
    y += 11;
    for (int i = 1; i < rows.size(); i++) {
      Row row = rows.get(i);
      if (row.icon() != null) {
        graphics.item(row.icon(), x, y);
        graphics.itemDecorations(font, row.icon(), x, y);
        graphics.text(font, row.text(), x + 19, y + 5, row.color());
      } else {
        graphics.text(font, row.text(), x, y + 1, row.color());
      }
      y += row.icon() != null ? 20 : 12;
    }
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
    Minecraft mc = Minecraft.getInstance();
    // Chest menus draw a dim layer (and a blur) over the HUD layer, which turns
    // these rows into a dark smudge. ScreenHudMixin draws them again above that
    // layer, so skip the copy underneath; every other menu keeps the dimmed HUD.
    if (mc != null && mc.gui != null
        && mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) {
      return;
    }
    draw(graphics);
  }

  /** Draws the corner box: from the HUD layer, or above an open menu's dim layer. */
  public static void draw(GuiGraphicsExtractor graphics) {
    Minecraft mc = Minecraft.getInstance();
    if (mc == null || !OpenOriginsConfig.getInstance().isEnabled(OriginFeature.BADGER_TASK_HUD)
        || !OpenOriginsConfig.getInstance().isBadgerHudVisible()) {
      return;
    }
    if (previewActive || mc.gui.hud.isHidden() || mc.player == null) {
      return;
    }
    Font font = mc.font;
    List<Row> rows = buildRows(mc, font);
    if (rows.isEmpty()) {
      return;
    }
    int maxW = maxWidth(font, rows);
    int totalH = totalHeight(rows);
    int sw = mc.getWindow().getGuiScaledWidth();
    int sh = mc.getWindow().getGuiScaledHeight();
    OpenOriginsConfig.HudCorner corner = OpenOriginsConfig.getInstance().getBadgerHudCorner();
    int dx = OpenOriginsConfig.getInstance().getBadgerHudDX();
    int dy = OpenOriginsConfig.getInstance().getBadgerHudDY();
    int[] box = boxAt(corner, dx, dy, maxW, totalH, sw, sh);
    int x = box[0];
    int y = box[1];
    if ((corner == OpenOriginsConfig.HudCorner.TOP_LEFT
        || corner == OpenOriginsConfig.HudCorner.TOP_RIGHT) && dx + maxW > sw / 2 - 91) {
      try {
        int bars = ((BossBarAccessor) mc.gui.hud.getBossOverlay()).openorigins$getBossEvents()
            .size();
        if (bars > 0) y = 14 + bars * 19;
      } catch (Throwable ignored) {
      }
    }
    drawBox(graphics, font, rows, x, y, OpenOriginsConfig.getInstance().isBadgerHudBackdrop(),
        maxW, totalH);
  }

  private static final java.util.Map<String, net.minecraft.world.item.Item> ITEMS =
      new java.util.concurrent.ConcurrentHashMap<>();

  private static net.minecraft.world.item.Item itemFor(String id) {
    if (id == null || id.isEmpty()) {
      return null;
    }
    return ITEMS.computeIfAbsent(id, k -> {
      net.minecraft.resources.Identifier key = net.minecraft.resources.Identifier.tryParse(k);
      return key == null ? null
          : net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(key);
    });
  }

  private static int countInInventory(Minecraft mc, BadgerCache.Required r) {
    if (mc.player == null) {
      return 0;
    }
    net.minecraft.world.item.Item item = itemFor(r.itemId());
    if (item == null) {
      return 0;
    }
    int total = 0;
    net.minecraft.world.entity.player.Inventory inv = mc.player.getInventory();
    for (int i = 0; i < inv.getContainerSize(); i++) {
      ItemStack stack = inv.getItem(i);
      if (stack.isEmpty() || stack.getItem() != item) {
        continue;
      }
      if (!r.displayName().isEmpty()
          && !stack.getHoverName().getString().equals(r.displayName())) {
        continue;
      }
      total += stack.getCount();
    }
    return total;
  }
}
