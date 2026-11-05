package com.openorigins.client.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * Auction price database: every observed /ah listing (content-detected via
 * the server's own "Price:" + "Seller:" lore) contributes its per-unit price.
 * Powers average/median stats in tooltips and the database browser.
 */
public final class AuctionPriceDb {
  private AuctionPriceDb() {}

  public record Sample(double unit, long ts, String seller, long endsAt, int count) {}

  public record Stats(double avg, double med, int n) {}

  /** Rarity order for the picker (ALL first). Server badges only. */
  public static final List<String> RARITIES =
      List.of("ALL", "COMMON", "LIMITED", "RARE", "EPIC", "LEGENDARY", "NONE", "RENAMED");

  /** Server badge glyph (name prefix) per rarity. */
  public static final java.util.Map<String, String> RARITY_GLYPHS = AuctionParse.RARITY_GLYPHS;

  /** Rarity from the server badge prefix; plain names get their own bucket. */
  public static String rarityOf(String name) {
    return AuctionParse.rarityOf(name);
  }

  /** Backwards-compatible overload (vanilla component is ignored). */
  public static String rarityOf(String name, String vanillaRarity) {
    return rarityOf(name);
  }

  public static final class Entry {
    public final String key;
    public String name;
    public String descText = "";
    public String vanillaId;
    public String rarity;
    public String variant = "";
    public boolean renamed;
    volatile Component styledName;
    volatile String styledRaw;
    final List<Sample> samples = new ArrayList<>();
    volatile ItemStack icon;
    volatile JsonElement iconRaw;

    Entry(String key, String name, String vanillaId) {
      this.key = key;
      this.name = name;
      this.vanillaId = vanillaId;
      this.rarity = rarityOf(name);
    }
  }

  private static final Map<String, Entry> ITEMS = new ConcurrentHashMap<>();
  /** Already-recorded rows with last-seen time: re-scans within the window
   * are ignored, genuinely standing lots re-observe after it. */
  private static final Map<String, Long> SEEN = new LinkedHashMap<>() {
    @Override
    protected boolean removeEldestEntry(Map.Entry<String, Long> e) {
      return size() > 2000;
    }
  };

  /** A row counts as new when unseen for this long (re-lists pass through). */
  private static final long REOBSERVE_MS = 30L * 60 * 1000;

  private static final int MAX_SAMPLES = 2000;
  /** Hard cap on tracked items: the JSON is rewritten whole on save. */
  private static final int MAX_ITEMS = 2000;
  /** Minimum spacing between whole-file saves while browsing. */
  private static final int SAVE_COOLDOWN_MS = 10_000;
  private static final long MAX_AGE_MS = 120L * 24 * 60 * 60 * 1000;

  /** Normalized seller nicks excluded from stats (value: display case). */
  private static final java.util.Map<String, String> EXCLUDED = new ConcurrentHashMap<>();

  /** Nick without the rank badge prefix ("… Zoltraq" -> "zoltraq"). */
  public static String sellerNick(String seller) {
    return AuctionParse.sellerNick(seller);
  }

  public static boolean isExcluded(String seller) {
    String nick = sellerNick(seller);
    return !nick.isEmpty() && EXCLUDED.containsKey(nick);
  }

  public static java.util.List<String> excludedNicks() {
    List<String> out = new ArrayList<>(EXCLUDED.values());
    out.sort(String::compareToIgnoreCase);
    return out;
  }

  public static synchronized void setExcluded(String seller, boolean excluded) {
    String nick = sellerNick(seller);
    if (nick.isEmpty()) {
      return;
    }
    if (excluded) {
      String display = seller.strip();
      int sp = display.lastIndexOf(' ');
      EXCLUDED.put(nick, sp >= 0 ? display.substring(sp + 1) : display);
    } else {
      EXCLUDED.remove(nick);
    }
    save();
  }

  /** Listing end instant from "Time left: 17 hours and 50 minutes", else -1. */
  static long parseEndsAt(List<Component> lines, long now) {
    return AuctionParse.parseEndsAt(strings(lines), now);
  }

  /** "Recently sold" rows in the same menu carry a "Sold: … ago" line. */
  static boolean isSoldRow(List<Component> lines) {
    return AuctionParse.isSoldRow(strings(lines));
  }

  /** Stored description of a recently-sold row (legacy polluted entries). */
  static boolean isSoldDesc(String desc) {
    return AuctionParse.isSoldDesc(desc);
  }

  private static List<String> strings(List<Component> lines) {
    List<String> out = new ArrayList<>(lines.size());
    for (Component line : lines) {
      out.add(line == null ? "" : line.getString());
    }
    return out;
  }

  /** Normalized display name without the server badge prefix. */
  static String normName(String name) {
    return AuctionParse.normName(name);
  }

  /**
   * Identity signals from the full description: normalized name plus the
   * first descriptive line and a hash of all of them. Returns {suffix, label}.
   */
  static String[] variantOf(String name, List<Component> loreLines) {
    return AuctionParse.variantOf(name, strings(loreLines));
  }

  /** Scans a menu (background or visible) for auction listings. */
  public static synchronized int scanMenu(AbstractContainerMenu menu, int windowId) {
    if (menu == null) {
      return 0;
    }
    if (!com.openorigins.client.OpenOriginsConfig.getInstance()
        .isEnabled(com.openorigins.client.OriginFeature.AH_PRICE_DB)) {
      return 0;
    }
    long now = System.currentTimeMillis();
    int[] n = {0};
    MenuLore.forEachTop(menu, (idx, stack) -> {
      try {
        if (stack.getCount() <= 0) {
          return;
        }
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null || lore.lines().isEmpty()) {
          return;
        }
        Double total = null;
        String seller = null;
        List<Component> loreLines = lore.lines();
        // "Recently sold" rows have Price/Seller too, but a Sold: timestamp:
        // they are past sales, not listings, and must not enter the DB.
        if (isSoldRow(loreLines)) {
          return;
        }
        for (Component line : loreLines) {
          String s = line.getString().strip();
          Double parsed = AuctionParse.parsePrice(s);
          if (parsed != null) {
            total = parsed;
          } else if (s.toLowerCase(Locale.ROOT).startsWith("seller:")) {
            seller = s.substring("seller:".length()).strip();
            if (seller.isEmpty()) {
              seller = null;
            }
          }
        }
        if (total == null || total <= 0 || seller == null) {
          return;
        }
        long endsAt = parseEndsAt(loreLines, now);
        String hoverName = stack.getHoverName().getString();
        Component hoverComp = stack.getHoverName();
        String[] variant = variantOf(hoverName, loreLines);
        String key = itemKey(stack) + variant[0];
        double unit = total / stack.getCount();
        // Same lot check against recorded samples: identical terms plus
        // listing end within tolerance (display minutes shift as time passes,
        // the true end doesn't). Page moves and re-opens can't duplicate.
        if (isSameLot(key, unit, stack.getCount(), seller, endsAt)) {
          return;
        }
        String row = key + "|" + total + "|" + stack.getCount()
            + "|" + seller.toLowerCase(Locale.ROOT);
        Long last = SEEN.get(row);
        // No end to compare (or first sighting): re-observation window only.
        if (last != null && now - last < REOBSERVE_MS) {
          return;
        }
        SEEN.put(row, now);
        record(key, hoverName, vanillaId(stack), unit, seller, now,
            endsAt, stack.getCount(), variant[1], variant.length > 2 ? variant[2] : "");
        try {
          Entry e = ITEMS.get(key);
          if (e != null) {
            e.styledName = hoverComp;
            e.styledRaw = encodeComponent(hoverComp);
            if (isRenamed(hoverComp)) {
              e.renamed = true;
            }
            if (!stack.isEmpty()) {
              ItemStack icon = stack.copy();
              icon.setCount(1);
              e.icon = icon;
            }
            e.rarity = rarityOf(e.name);
          }
        } catch (Throwable ignored) {
        }
        n[0]++;
      } catch (Throwable ignored) {
      }
    });
    if (n[0] > 0) {
      scheduleSave();
      DebugLog.info("[ahdb] +{} listings ({} items tracked)", n[0], ITEMS.size());
    }
    flushIfDue();
    return n[0];
  }

  private static String itemKey(ItemStack stack) {
    try {
      Optional<String> id = CustomItemIds.resolve(stack);
      if (id.isPresent()) {
        return id.get();
      }
    } catch (Throwable ignored) {
    }
    return vanillaId(stack);
  }

  /** Full database key: base id + normalized name + description hash. */
  static String fullKey(ItemStack stack) {
    try {
      String base = itemKey(stack);
      ItemLore lore = stack.get(DataComponents.LORE);
      List<Component> lines = lore == null ? List.of() : lore.lines();
      return base + variantOf(stack.getHoverName().getString(), lines)[0];
    } catch (Throwable t) {
      return itemKey(stack);
    }
  }

  private static String vanillaId(ItemStack stack) {    try {
      return net.minecraft.core.registries.BuiltInRegistries.ITEM
          .getKey(stack.getItem()).toString();
    } catch (Throwable t) {
      return "minecraft:air";
    }
  }

  /** True when the same lot (terms + end within tolerance) is already stored. */
  static boolean isSameLot(String key, double unit, int count, String seller, long endsAt) {
    if (endsAt < 0) {
      return false;
    }
    Entry e = key == null ? null : ITEMS.get(key);
    if (e == null) {
      return false;
    }
    for (Sample s : e.samples) {
      if (s.endsAt() < 0 || s.unit() != unit || s.count() != count) {
        continue;
      }
      if (!s.seller().equalsIgnoreCase(seller)) {
        continue;
      }
      if (Math.abs(s.endsAt() - endsAt) < 360000L) {
        return true;
      }
    }
    return false;
  }

  static synchronized void record(String key, String name, String vanillaId, double unit,
      String seller, long now, long endsAt, int count, String variant, String descFull) {
    if (key == null || key.isEmpty() || !(unit > 0)) {
      return;
    }
    Entry e = ITEMS.get(key);
    if (e == null) {
      e = new Entry(key, name == null ? key : name, vanillaId);
      e.variant = variant == null ? "" : variant;
      String desc = descFull == null ? "" : descFull;
      e.descText = desc.length() > 400 ? desc.substring(0, 400) : desc;
      ITEMS.put(key, e);
      enforceItemCap();
    } else if (name != null && !name.isBlank()) {
      e.name = name;
    }
    e.samples.add(new Sample(unit, now, seller == null ? "" : seller, endsAt, Math.max(1, count)));
    while (e.samples.size() > MAX_SAMPLES) {
      e.samples.remove(0);
    }
  }

  public static Stats stats(String key) {
    Entry e = key == null ? null : ITEMS.get(key);
    if (e == null || e.samples.isEmpty()) {
      return null;
    }
    // Volume-weighted: a 128-stack lot moves the market more than a single.
    List<double[]> vals = new ArrayList<>(e.samples.size());
    for (Sample s : e.samples) {
      if (isExcluded(s.seller())) {
        continue;
      }
      vals.add(new double[]{s.unit(), Math.max(1, s.count())});
    }
    AuctionParse.Aggregate agg = AuctionParse.aggregate(vals);
    if (agg == null) {
      return null;
    }
    return new Stats(agg.avg(), agg.med(), e.samples.size());
  }

  /** Case-insensitive name/id search plus rarity filter, most-sampled first. */
  public static List<Entry> search(String query, String rarity) {
    String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
    List<Entry> out = new ArrayList<>();
    for (Entry e : ITEMS.values()) {
      if (rarity != null && !rarity.equals("ALL")) {
        if ("RENAMED".equals(rarity)) {
          if (!e.renamed) {
            continue;
          }
        } else if (e.renamed || !rarity.equals(e.rarity)) {
          continue;
        }
      }
      if (q.isEmpty() || e.name.toLowerCase(Locale.ROOT).contains(q)
          || e.key.toLowerCase(Locale.ROOT).contains(q)
          || (e.descText != null && e.descText.toLowerCase(Locale.ROOT).contains(q))) {
        out.add(e);
      }
    }
    out.sort((a, b) -> {
      int c = Integer.compare(b.samples.size(), a.samples.size());
      return c != 0 ? c : a.name.compareToIgnoreCase(b.name);
    });
    return out;
  }

  public static int itemCount() {
    return ITEMS.size();
  }

  /** Copy of an entry's samples (newest last). */
  public static List<Sample> samples(String key) {
    Entry e = key == null ? null : ITEMS.get(key);
    return e == null ? List.of() : List.copyOf(e.samples);
  }

  /** Full description text for the detail panel. */
  public static String descText(String key) {
    Entry e = key == null ? null : ITEMS.get(key);
    return e == null || e.descText == null ? "" : e.descText;
  }

  /** Styled display name, or null when unknown. */
  public static Component styledName(String key) {
    Entry e = key == null ? null : ITEMS.get(key);
    return e == null ? null : e.styledName;
  }

  public static synchronized void remove(String key) {
    if (key != null && ITEMS.remove(key) != null) {
      save();
    }
  }

  public static synchronized void clear() {
    ITEMS.clear();
    SEEN.clear();
    save();
  }

  public static synchronized void clearWindows() {
    SEEN.clear();
  }

  /** Display icon: live snapshot, else disk snapshot decoded once a level exists. */
  public static ItemStack iconFor(String key) {
    Entry e = key == null ? null : ITEMS.get(key);
    if (e == null) {
      return ItemStack.EMPTY;
    }
    if (e.icon != null && !e.icon.isEmpty()) {
      return e.icon;
    }
    if (e.iconRaw != null) {
      try {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc != null && mc.level != null) {
          var ops = net.minecraft.resources.RegistryOps.create(
              com.mojang.serialization.JsonOps.INSTANCE, mc.level.registryAccess());
          ItemStack decoded = net.minecraft.world.item.ItemStack.OPTIONAL_CODEC
              .parse(ops, e.iconRaw).getOrThrow();
          e.iconRaw = null;
          if (!decoded.isEmpty()) {
            e.icon = decoded;
            return decoded;
          }
        }
      } catch (Throwable ignored) {
        e.iconRaw = null;
      }
    }
    return ItemStack.EMPTY;
  }

  private static String encodeComponent(Component c) {
    io.netty.buffer.ByteBuf buf = io.netty.buffer.Unpooled.buffer();
    try {
      net.minecraft.network.chat.ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC
          .encode(buf, c);
      byte[] b = new byte[buf.readableBytes()];
      buf.readBytes(b);
      return Base64.getEncoder().encodeToString(b);
    } finally {
      buf.release();
    }
  }

  private static Component decodeComponent(String s) {
    byte[] b = Base64.getDecoder().decode(s);
    io.netty.buffer.ByteBuf buf = io.netty.buffer.Unpooled.wrappedBuffer(b);
    try {
      return net.minecraft.network.chat.ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC
          .decode(buf);
    } finally {
      buf.release();
    }
  }

  /** Player-renamed items carry italic names; server names never do. */
  static boolean isRenamed(Component hoverName) {
    try {
      return hoverName != null && hoverName.getStyle().isItalic();
    } catch (Throwable t) {
      return false;
    }
  }

  public static String format(double v) {
    return AuctionParse.formatUnit(v);
  }

  private static Path file() {
    return FabricLoader.getInstance().getConfigDir().resolve("openorigins_ah_prices.json");
  }

  private static volatile boolean dirty;
  private static volatile long lastSaveMs;

  /** Marks data changed; the whole-file write happens at the next flush point. */
  public static synchronized void scheduleSave() {
    dirty = true;
  }

  /** Writes at most once per cooldown, when a flush point passes by. */
  public static synchronized void flushIfDue() {
    if (!dirty) {
      return;
    }
    long now = System.currentTimeMillis();
    if (now - lastSaveMs < SAVE_COOLDOWN_MS) {
      return;
    }
    dirty = false;
    lastSaveMs = now;
    save();
  }

  /** Unconditional flush: end of session, or user-initiated change. */
  public static synchronized void flushNow() {
    if (!dirty) {
      return;
    }
    dirty = false;
    lastSaveMs = System.currentTimeMillis();
    save();
  }

  /** Trims the item map to MAX_ITEMS by oldest last-observed sample. */
  private static void enforceItemCap() {
    while (ITEMS.size() > MAX_ITEMS) {
      String oldest = null;
      long oldestTs = Long.MAX_VALUE;
      for (Entry e : ITEMS.values()) {
        long t = e.samples.isEmpty() ? 0 : e.samples.get(e.samples.size() - 1).ts();
        if (t < oldestTs) {
          oldestTs = t;
          oldest = e.key;
        }
      }
      if (oldest == null) {
        break;
      }
      ITEMS.remove(oldest);
    }
  }

  public static synchronized void save() {
    try {
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      var ops = (mc != null && mc.level != null) ? net.minecraft.resources.RegistryOps.create(
          com.mojang.serialization.JsonOps.INSTANCE, mc.level.registryAccess()) : null;
      JsonObject root = new JsonObject();
      JsonArray excl = new JsonArray();
      for (String display : EXCLUDED.values()) {
        excl.add(display);
      }
      root.add("excluded", excl);
      for (Entry e : ITEMS.values()) {
        if (e.samples.isEmpty()) {
          continue;
        }
        JsonObject o = new JsonObject();
        o.addProperty("name", e.name);
        o.addProperty("vanilla", e.vanillaId);
        o.addProperty("rarity", e.rarity);
        o.addProperty("variant", e.variant);
        o.addProperty("desc", e.descText == null ? "" : e.descText);
        o.addProperty("renamed", e.renamed);
        try {
          if (e.styledRaw != null) {
            o.addProperty("styledName", e.styledRaw);
          } else if (e.styledName != null) {
            o.addProperty("styledName", encodeComponent(e.styledName));
          }
        } catch (Throwable ignored) {
        }
        JsonArray arr = new JsonArray();
        for (Sample s : e.samples) {
          JsonArray tuple = new JsonArray();
          tuple.add(s.unit());
          tuple.add(s.ts());
          tuple.add(s.seller());
          tuple.add(s.endsAt());
          tuple.add(s.count());
          arr.add(tuple);
        }
        o.add("samples", arr);
        try {
          if (e.icon != null && !e.icon.isEmpty() && ops != null) {
            o.add("icon", net.minecraft.world.item.ItemStack.OPTIONAL_CODEC
                .encodeStart(ops, e.icon).getOrThrow());
          } else if (e.iconRaw != null) {
            o.add("icon", e.iconRaw);
          }
        } catch (Throwable ignored) {
        }
        root.add(e.key, o);
      }
      Files.writeString(file(), root.toString(), StandardCharsets.UTF_8);
    } catch (Exception ex) {
      org.slf4j.LoggerFactory.getLogger("openorigins")
          .warn("[ahdb] save failed: {}", ex.toString());
    }
  }

  public static synchronized void load() {
    try {
      Path f = file();
      if (!Files.isRegularFile(f)) {
        return;
      }
      JsonObject root =
          JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
      long now = System.currentTimeMillis();
      int n = 0;
      int purged = 0;
      if (root.has("excluded")) {
        try {
          for (JsonElement el : root.getAsJsonArray("excluded")) {
            String display = el.getAsString().strip();
            String nick = sellerNick(display);
            if (!nick.isEmpty()) {
              EXCLUDED.put(nick, display);
            }
          }
        } catch (Exception ignored) {
        }
      }
      for (Map.Entry<String, JsonElement> en : root.entrySet()) {
        if (en.getKey().equals("excluded")) {
          continue;
        }
        try {
          JsonObject o = en.getValue().getAsJsonObject();
          Entry e = new Entry(en.getKey(), o.has("name") ? o.get("name").getAsString() : en.getKey(),
              o.has("vanilla") ? o.get("vanilla").getAsString() : "minecraft:paper");
          // Server badges only: recompute (drops legacy vanilla rarities).
          e.rarity = rarityOf(e.name);
          if (o.has("renamed")) {
            try {
              e.renamed = o.get("renamed").getAsBoolean();
            } catch (Exception ignored) {
            }
          }
          if (o.has("styledName")) {
            try {
              String raw = o.get("styledName").getAsString();
              e.styledRaw = raw;
              e.styledName = decodeComponent(raw);
            } catch (Exception ignored) {
            }
          }
          if (o.has("variant")) {
            try {
              e.variant = o.get("variant").getAsString();
            } catch (Exception ignored) {
            }
          }
          if (o.has("desc")) {
            try {
              e.descText = o.get("desc").getAsString();
            } catch (Exception ignored) {
            }
          }
          // Legacy pollution: entries recorded from the "recently sold" view.
          // Their key was built from the Sold: line, so nothing valid is lost.
          if (isSoldDesc(e.descText)) {
            purged++;
            dirty = true;
            continue;
          }
          if (o.has("icon")) {
            e.iconRaw = o.get("icon");
          }
          JsonArray arr = o.getAsJsonArray("samples");
          if (arr != null) {
            for (JsonElement el : arr) {
              JsonArray tuple = el.getAsJsonArray();
              double unit = tuple.get(0).getAsDouble();
              long ts = tuple.get(1).getAsLong();
              String seller = tuple.size() > 2 ? tuple.get(2).getAsString() : "";
              long endsAt = tuple.size() > 3 ? tuple.get(3).getAsLong() : -1;
              int count = tuple.size() > 4 ? tuple.get(4).getAsInt() : 1;
              if (unit > 0 && now - ts < MAX_AGE_MS) {
                e.samples.add(new Sample(unit, ts, seller, endsAt, Math.max(1, count)));
              }
            }
          }
          if (!e.samples.isEmpty()) {
            while (e.samples.size() > MAX_SAMPLES) {
              e.samples.remove(0);
            }
            ITEMS.put(e.key, e);
            n++;
          }
        } catch (Exception ignored) {
        }
      }
      org.slf4j.LoggerFactory.getLogger("openorigins")
          .info("[ahdb] loaded {} items from disk", n);
      if (purged > 0) {
        org.slf4j.LoggerFactory.getLogger("openorigins")
            .info("[ahdb] purged {} recently-sold rows", purged);
        save();
      }
    } catch (Exception ex) {
      org.slf4j.LoggerFactory.getLogger("openorigins")
          .warn("[ahdb] load failed: {}", ex.toString());
    }
  }
}
