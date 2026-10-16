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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
  public static final java.util.Map<String, String> RARITY_GLYPHS = Map.of(
      "LIMITED", "ꑶ",
      "COMMON", "ꑲ",
      "RARE", "ꑳ",
      "EPIC", "ꑴ",
      "LEGENDARY", "ꑵ");

  private static final java.util.Map<String, String> GLYPH_RARITY = Map.of(
      "ꑶ", "LIMITED",
      "ꑲ", "COMMON",
      "ꑳ", "RARE",
      "ꑴ", "EPIC",
      "ꑵ", "LEGENDARY");

  /** Rarity from the server badge prefix; plain names get their own bucket. */
  public static String rarityOf(String name) {
    if (name != null && !name.isEmpty()) {
      String g = GLYPH_RARITY.get(name.substring(0, 1));
      if (g != null) {
        return g;
      }
    }
    return "NONE";
  }

  /** Backwards-compatible overload (vanilla component is ignored). */
  public static String rarityOf(String name, String vanillaRarity) {
    return rarityOf(name);
  }

  public static final class Entry {
    public final String key;
    public String name;
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
  private static final long MAX_AGE_MS = 120L * 24 * 60 * 60 * 1000;

  /** Normalized seller nicks excluded from stats. */
  private static final java.util.Set<String> EXCLUDED =
      java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

  /** Nick without the rank badge prefix ("… Zoltraq" -> "zoltraq"). */
  public static String sellerNick(String seller) {
    if (seller == null) {
      return "";
    }
    String s = seller.strip();
    int sp = s.lastIndexOf(' ');
    if (sp >= 0) {
      s = s.substring(sp + 1);
    }
    return s.toLowerCase(Locale.ROOT);
  }

  public static boolean isExcluded(String seller) {
    String nick = sellerNick(seller);
    return !nick.isEmpty() && EXCLUDED.contains(nick);
  }

  public static java.util.Set<String> excludedNicks() {
    return java.util.Set.copyOf(EXCLUDED);
  }

  public static synchronized void setExcluded(String seller, boolean excluded) {
    String nick = sellerNick(seller);
    if (nick.isEmpty()) {
      return;
    }
    if (excluded) {
      EXCLUDED.add(nick);
    } else {
      EXCLUDED.remove(nick);
    }
    save();
  }

  private static final Pattern TIME_LEFT_NUM =
      Pattern.compile("(\\d+)\\s*(day|hour|min)", Pattern.CASE_INSENSITIVE);

  /** Listing end instant from "Time left: 17 hours and 50 minutes", else -1. */
  static long parseEndsAt(List<Component> lines, long now) {
    try {
      for (Component line : lines) {
        String s = line.getString().strip().toLowerCase(Locale.ROOT);
        if (!s.startsWith("time left:")) {
          continue;
        }
        Matcher m = TIME_LEFT_NUM.matcher(s);
        long ms = 0;
        boolean any = false;
        while (m.find()) {
          long v = Long.parseLong(m.group(1));
          String unit = m.group(2).toLowerCase(Locale.ROOT);
          if (unit.startsWith("day")) {
            ms += v * 24L * 60 * 60 * 1000;
          } else if (unit.startsWith("hour")) {
            ms += v * 60L * 60 * 1000;
          } else {
            ms += v * 60L * 1000;
          }
          any = true;
        }
        if (any && ms > 0) {
          return now + ms;
        }
      }
    } catch (Throwable ignored) {
    }
    return -1;
  }

  private static final Pattern PRICE =
      Pattern.compile("(?i)^price:\\s*([\\d,]+(?:\\.\\d+)?)");

  /** Lore lines that describe the listing itself, never the item. */
  private static final List<String> NOISE_CONTAINS = List.of(
      "price:", "seller:", "time left:", "created:", "world:", "position:",
      "click", "visit realm", "pin realm", "players:", "delivered", "expired", "reset");

  /** Normalized display name without the server badge prefix. */
  static String normName(String name) {
    if (name == null) {
      return "";
    }
    String s = name.strip();
    while (!s.isEmpty() && s.codePointAt(0) > 0x2500) {
      s = s.substring(Character.charCount(s.codePointAt(0))).strip();
    }
    return s.toLowerCase(Locale.ROOT);
  }

  /**
   * Identity signals from the full description: normalized name plus the
   * first descriptive line and a hash of all of them. Returns {suffix, label}.
   */
  static String[] variantOf(String name, List<Component> loreLines) {
    List<String> desc = new ArrayList<>();
    for (Component line : loreLines) {
      String s = line.getString().strip();
      if (s.isEmpty()) {
        continue;
      }
      String lower = s.toLowerCase(Locale.ROOT);
      boolean noise = false;
      for (String n : NOISE_CONTAINS) {
        if (lower.contains(n)) {
          noise = true;
          break;
        }
      }
      if (noise) {
        continue;
      }
      // Glyph-only separators carry no identity (badges live in the name).
      if (s.codePointCount(0, s.length()) == 1 && s.codePointAt(0) > 0x2500) {
        continue;
      }
      desc.add(s);
    }
    String nn = normName(name);
    String suffix = "";
    String label = "";
    if (!nn.isEmpty()) {
      suffix = "|n=" + nn;
    }
    if (!desc.isEmpty()) {
      try {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
        byte[] hash =
            md.digest(String.join("\n", desc).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < 5; i++) {
          hex.append(String.format("%02x", hash[i]));
        }
        suffix += "|v=" + hex;
        label = desc.get(0);
        if (label.length() > 30) {
          label = label.substring(0, 30) + "…";
        }
      } catch (Throwable ignored) {
      }
    }
    return new String[]{suffix, label};
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
    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
    Object playerInv = (mc != null && mc.player != null) ? mc.player.getInventory() : null;
    long now = System.currentTimeMillis();
    int n = 0;
    int idx = 0;
    for (Slot slot : menu.slots) {
      idx++;
      try {
        if (!slot.hasItem()) {
          continue;
        }
        if (playerInv != null && slot.container == playerInv) {
          continue;
        }
        ItemStack stack = slot.getItem();
        if (stack.getCount() <= 0) {
          continue;
        }
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null || lore.lines().isEmpty()) {
          continue;
        }
        Double total = null;
        String seller = null;
        List<Component> loreLines = lore.lines();
        for (Component line : loreLines) {
          String s = line.getString().strip();
          Matcher m = PRICE.matcher(s);
          if (m.find()) {
            try {
              total = Double.parseDouble(m.group(1).replace(",", ""));
            } catch (NumberFormatException ignored) {
            }
          } else if (s.toLowerCase(Locale.ROOT).startsWith("seller:")) {
            seller = s.substring("seller:".length()).strip();
            if (seller.isEmpty()) {
              seller = null;
            }
          }
        }
        if (total == null || total <= 0 || seller == null) {
          continue;
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
          continue;
        }
        String row = key + "|" + total + "|" + stack.getCount()
            + "|" + seller.toLowerCase(Locale.ROOT);
        Long last = SEEN.get(row);
        // No end to compare (or first sighting): re-observation window only.
        if (last != null && now - last < REOBSERVE_MS) {
          continue;
        }
        SEEN.put(row, now);
        record(key, hoverName, vanillaId(stack), unit, seller, now,
            endsAt, stack.getCount(), variant[1]);
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
        n++;
      } catch (Throwable ignored) {
      }
    }
    if (n > 0) {
      save();
      DebugLog.info("[ahdb] +{} listings ({} items tracked)", n, ITEMS.size());
    }
    return n;
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
      String seller, long now, long endsAt, int count, String variant) {
    if (key == null || key.isEmpty() || !(unit > 0)) {
      return;
    }
    Entry e = ITEMS.get(key);
    if (e == null) {
      e = new Entry(key, name == null ? key : name, vanillaId);
      e.variant = variant == null ? "" : variant;
      ITEMS.put(key, e);
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
    long totalW = 0;
    double totalV = 0;
    for (Sample s : e.samples) {
      if (isExcluded(s.seller())) {
        continue;
      }
      long w = Math.max(1, s.count());
      vals.add(new double[]{s.unit(), w});
      totalW += w;
      totalV += s.unit() * w;
    }
    vals.sort(java.util.Comparator.comparingDouble(a -> a[0]));
    if (vals.isEmpty() || totalW <= 0) {
      return null;
    }
    long half = (totalW + 1) / 2;
    long acc = 0;
    double med = vals.get(vals.size() - 1)[0];
    for (double[] v : vals) {
      acc += (long) v[1];
      if (acc >= half) {
        med = v[0];
        break;
      }
    }
    return new Stats(totalV / totalW, med, e.samples.size());
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
          || e.key.toLowerCase(Locale.ROOT).contains(q)) {
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
    if (v == Math.floor(v) && !Double.isInfinite(v)) {
      return String.format(Locale.US, "%,d", (long) v);
    }
    return String.format(Locale.US, "%,.2f", v);
  }

  private static Path file() {
    return FabricLoader.getInstance().getConfigDir().resolve("openorigins_ah_prices.json");
  }

  public static synchronized void save() {
    try {
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      var ops = (mc != null && mc.level != null) ? net.minecraft.resources.RegistryOps.create(
          com.mojang.serialization.JsonOps.INSTANCE, mc.level.registryAccess()) : null;
      JsonObject root = new JsonObject();
      JsonArray excl = new JsonArray();
      for (String nick : EXCLUDED) {
        excl.add(nick);
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
      if (root.has("excluded")) {
        try {
          for (JsonElement el : root.getAsJsonArray("excluded")) {
            String nick = el.getAsString().strip().toLowerCase(Locale.ROOT);
            if (!nick.isEmpty()) {
              EXCLUDED.add(nick);
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
    } catch (Exception ex) {
      org.slf4j.LoggerFactory.getLogger("openorigins")
          .warn("[ahdb] load failed: {}", ex.toString());
    }
  }
}
