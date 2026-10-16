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

  public record Sample(double unit, long ts) {}

  public record Stats(double avg, double med, int n) {}

  public static final class Entry {
    public final String key;
    public String name;
    public String vanillaId;
    final List<Sample> samples = new ArrayList<>();

    Entry(String key, String name, String vanillaId) {
      this.key = key;
      this.name = name;
      this.vanillaId = vanillaId;
    }
  }

  private static final Map<String, Entry> ITEMS = new ConcurrentHashMap<>();
  /** Already-recorded rows per menu window (ids recycle, so this stays tiny). */
  private static final Map<Integer, java.util.Set<String>> SEEN = new LinkedHashMap<>() {
    @Override
    protected boolean removeEldestEntry(Map.Entry<Integer, java.util.Set<String>> e) {
      return size() > 12;
    }
  };

  private static final int MAX_SAMPLES = 100;
  private static final long MAX_AGE_MS = 120L * 24 * 60 * 60 * 1000;

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
    java.util.Set<String> seen =
        SEEN.computeIfAbsent(windowId, k -> java.util.Collections.newSetFromMap(
            new java.util.concurrent.ConcurrentHashMap<>()));
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
        boolean seller = false;
        for (Component line : lore.lines()) {
          String s = line.getString().strip();
          Matcher m = PRICE.matcher(s);
          if (m.find()) {
            try {
              total = Double.parseDouble(m.group(1).replace(",", ""));
            } catch (NumberFormatException ignored) {
            }
          } else if (s.toLowerCase(Locale.ROOT).startsWith("seller:")) {
            seller = true;
          }
        }
        if (total == null || total <= 0 || !seller) {
          continue;
        }
        String key = itemKey(stack);
        String row = idx + "|" + key + "|" + total + "|" + stack.getCount();
        if (!seen.add(row)) {
          continue;
        }
        double unit = total / stack.getCount();
        record(key, stack.getHoverName().getString(), vanillaId(stack), unit);
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

  static synchronized void record(String key, String name, String vanillaId, double unit) {
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
    e.samples.add(new Sample(unit, System.currentTimeMillis()));
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

  /** Case-insensitive name/id search, most-sampled first. */
  public static List<Entry> search(String query) {
    String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
    List<Entry> out = new ArrayList<>();
    for (Entry e : ITEMS.values()) {
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
      JsonObject root = new JsonObject();
      for (Entry e : ITEMS.values()) {
        if (e.samples.isEmpty()) {
          continue;
        }
        JsonObject o = new JsonObject();
        o.addProperty("name", e.name);
        o.addProperty("vanilla", e.vanillaId);
        JsonArray arr = new JsonArray();
        for (Sample s : e.samples) {
          JsonArray pair = new JsonArray();
          pair.add(s.unit());
          pair.add(s.ts());
          arr.add(pair);
        }
        o.add("samples", arr);
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
          JsonArray arr = o.getAsJsonArray("samples");
          if (arr != null) {
            for (JsonElement el : arr) {
              JsonArray pair = el.getAsJsonArray();
              double unit = pair.get(0).getAsDouble();
              long ts = pair.get(1).getAsLong();
              if (unit > 0 && now - ts < MAX_AGE_MS) {
                e.samples.add(new Sample(unit, ts));
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
