package com.openorigins.client.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

  public record Sample(double unit, long ts, String seller, long endsAt) {}

  public record Stats(double avg, double med, int n) {}

  /** Rarity order for the picker (ALL first). */
  public static final List<String> RARITIES =
      List.of("ALL", "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "LIMITED");

  private static final java.util.Map<String, String> GLYPH_RARITY = Map.of(
      "ꑶ", "LIMITED",
      "ꑲ", "COMMON",
      "ꑳ", "RARE",
      "ꑴ", "EPIC",
      "ꑵ", "LEGENDARY");

  /** Rarity from the vanilla component, else the server badge glyph, else COMMON. */
  public static String rarityOf(String name, String vanillaRarity) {
    if (vanillaRarity != null && !vanillaRarity.isBlank()) {
      String v = vanillaRarity.strip().toUpperCase(Locale.ROOT);
      if (RARITIES.contains(v)) {
        return v;
      }
    }
    if (name != null && !name.isEmpty()) {
      String g = GLYPH_RARITY.get(name.substring(0, 1));
      if (g != null) {
        return g;
      }
    }
    return "COMMON";
  }

  public static final class Entry {
    public final String key;
    public String name;
    public String vanillaId;
    public String rarity;
    final List<Sample> samples = new ArrayList<>();
    volatile ItemStack icon;
    volatile JsonElement iconRaw;

    Entry(String key, String name, String vanillaId) {
      this.key = key;
      this.name = name;
      this.vanillaId = vanillaId;
      this.rarity = rarityOf(name, null);
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
        long endBucket = endsAt < 0 ? -1 : endsAt / 300000L;
        String key = itemKey(stack);
        String row = windowId + "|" + idx + "|" + key + "|" + total + "|" + stack.getCount()
            + "|" + seller.toLowerCase(Locale.ROOT) + "|" + endBucket;
        Long last = SEEN.get(row);
        // Same computed end: the same standing lot, no matter how old.
        // No end: fall back to the re-observation window.
        if (last != null && (endBucket != -1 || now - last < REOBSERVE_MS)) {
          continue;
        }
        SEEN.put(row, now);
        double unit = total / stack.getCount();
        record(key, stack.getHoverName().getString(), vanillaId(stack), unit, seller, now,
            endsAt);
        try {
          Entry e = ITEMS.get(key);
          if (e != null) {
            if (!stack.isEmpty()) {
              ItemStack icon = stack.copy();
              icon.setCount(1);
              e.icon = icon;
            }
            try {
              net.minecraft.world.item.Rarity rarity =
                  stack.get(DataComponents.RARITY);
              if (rarity != null) {
                e.rarity = rarityOf(e.name, rarity.name());
              }
            } catch (Throwable ignored) {
            }
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

  private static String vanillaId(ItemStack stack) {
    try {
      return net.minecraft.core.registries.BuiltInRegistries.ITEM
          .getKey(stack.getItem()).toString();
    } catch (Throwable t) {
      return "minecraft:air";
    }
  }

  static synchronized void record(String key, String name, String vanillaId, double unit,
      String seller, long now, long endsAt) {
    if (key == null || key.isEmpty() || !(unit > 0)) {
      return;
    }
    Entry e = ITEMS.get(key);
    if (e == null) {
      e = new Entry(key, name == null ? key : name, vanillaId);
      ITEMS.put(key, e);
    } else if (name != null && !name.isBlank()) {
      e.name = name;
    }
    e.samples.add(new Sample(unit, now, seller == null ? "" : seller, endsAt));
    while (e.samples.size() > MAX_SAMPLES) {
      e.samples.remove(0);
    }
  }

  public static Stats stats(String key) {
    Entry e = key == null ? null : ITEMS.get(key);
    if (e == null || e.samples.isEmpty()) {
      return null;
    }
    List<Double> vals = new ArrayList<>(e.samples.size());
    for (Sample s : e.samples) {
      vals.add(s.unit());
    }
    Collections.sort(vals);
    double sum = 0;
    for (double v : vals) {
      sum += v;
    }
    double med;
    int n = vals.size();
    if (n % 2 == 1) {
      med = vals.get(n / 2);
    } else {
      med = (vals.get(n / 2 - 1) + vals.get(n / 2)) / 2.0;
    }
    return new Stats(sum / n, med, n);
  }

  /** Case-insensitive name/id search plus rarity filter, most-sampled first. */
  public static List<Entry> search(String query, String rarity) {
    String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
    List<Entry> out = new ArrayList<>();
    for (Entry e : ITEMS.values()) {
      if (rarity != null && !rarity.equals("ALL") && !rarity.equals(e.rarity)) {
        continue;
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
      for (Entry e : ITEMS.values()) {
        if (e.samples.isEmpty()) {
          continue;
        }
        JsonObject o = new JsonObject();
        o.addProperty("name", e.name);
        o.addProperty("vanilla", e.vanillaId);
        o.addProperty("rarity", e.rarity);
        JsonArray arr = new JsonArray();
        for (Sample s : e.samples) {
          JsonArray tuple = new JsonArray();
          tuple.add(s.unit());
          tuple.add(s.ts());
          tuple.add(s.seller());
          tuple.add(s.endsAt());
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
      for (Map.Entry<String, JsonElement> en : root.entrySet()) {
        try {
          JsonObject o = en.getValue().getAsJsonObject();
          Entry e = new Entry(en.getKey(), o.has("name") ? o.get("name").getAsString() : en.getKey(),
              o.has("vanilla") ? o.get("vanilla").getAsString() : "minecraft:paper");
          if (o.has("rarity")) {
            try {
              String r = o.get("rarity").getAsString();
              e.rarity = AuctionPriceDb.RARITIES.contains(r) ? r : rarityOf(e.name, null);
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
              if (unit > 0 && now - ts < MAX_AGE_MS) {
                e.samples.add(new Sample(unit, ts, seller, endsAt));
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
