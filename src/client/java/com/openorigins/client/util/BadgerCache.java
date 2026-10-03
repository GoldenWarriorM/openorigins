package com.openorigins.client.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * Active badger task parsed from the server streaks menu.
 * Required items, streak score, reset days and tier progress.
 */
public final class BadgerCache {
  private BadgerCache() {}

  public record Required(String itemId, String displayName, int count) {}
  public record Tier(String name, int done, int total) {}

  private static final List<Required> REQUIRED = new java.util.concurrent.CopyOnWriteArrayList<>();
  private static final List<Tier> TIERS = new java.util.concurrent.CopyOnWriteArrayList<>();
  private static final java.util.Map<String, ItemStack> ICONS = new java.util.concurrent.ConcurrentHashMap<>();
  private static volatile int streakScore;
  private static volatile int highscore;
  private static volatile int daysUntilReset;

  private static final Pattern SCORE = Pattern.compile("current streak score:\\s*(\\d+)");
  private static final Pattern HIGH = Pattern.compile("streak highscore:\\s*(\\d+)");
  private static final Pattern DAYS = Pattern.compile("days until streak resets:\\s*(\\d+)");
  private static final Pattern TIER = Pattern.compile("(.+?):\\s*\\((\\d+)/(\\d+)\\)");

  public static List<Required> required() {
    return List.copyOf(REQUIRED);
  }

  private static String iconKey(String itemId, String displayName) {
    return itemId + "\n" + displayName;
  }

  /** Exact menu stack copy (keeps custom model/name) or a plain reconstruction. */
  public static ItemStack iconFor(Required r) {
    ItemStack exact = ICONS.get(iconKey(r.itemId(), r.displayName()));
    if (exact != null && !exact.isEmpty()) {
      return exact;
    }
    Identifier id = Identifier.tryParse(r.itemId());
    if (id == null) {
      return ItemStack.EMPTY;
    }
    net.minecraft.world.item.Item item =
        net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id);
    return new ItemStack(item, 1);
  }

  public static int streakScore() {
    return streakScore;
  }

  public static int daysUntilReset() {
    return daysUntilReset;
  }

  public static boolean hasTask() {
    return !REQUIRED.isEmpty();
  }

  /** Scans any open container; parses only when it looks like the streaks menu. */
  public static void scan(AbstractContainerScreen<?> screen) {
    if (screen == null || screen.getMenu() == null) {
      return;
    }
    Minecraft mc = Minecraft.getInstance();
    Object playerInv = (mc != null && mc.player != null) ? mc.player.getInventory() : null;
    List<Required> req = new ArrayList<>();
    List<Tier> tiers = new ArrayList<>();
    int score = -1;
    int high = -1;
    int days = -1;
    for (Slot slot : screen.getMenu().slots) {
      if (!slot.hasItem()) {
        continue;
      }
      if (playerInv != null && slot.container == playerInv) {
        continue;
      }
      ItemStack stack = slot.getItem();
      String name = stack.getHoverName().getString();
      ItemLore lore = stack.get(DataComponents.LORE);
      List<Component> lines = lore == null ? List.of() : lore.lines();
      String lower = name.toLowerCase(Locale.ROOT);
      Matcher m = SCORE.matcher(lower);
      if (m.find()) {
        score = Integer.parseInt(m.group(1));
      }
      Matcher d = DAYS.matcher(lower);
      if (d.find()) {
        days = Integer.parseInt(d.group(1));
      }
      if (lower.contains("go to badger to accept") && !lines.isEmpty()) {
        Matcher t = TIER.matcher(lines.get(0).getString());
        if (t.find()) {
          tiers.add(new Tier(t.group(1).trim(),
              Integer.parseInt(t.group(2)), Integer.parseInt(t.group(3))));
        }
      }
      for (Component line : lines) {
        String s = line.getString().toLowerCase(Locale.ROOT);
        Matcher h = HIGH.matcher(s);
        if (h.find()) {
          high = Integer.parseInt(h.group(1));
        }
      }
      if (lines.isEmpty() && stack.getCount() > 1 && req.size() < 6) {
        Identifier id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!id.getPath().equals("air")) {
          req.add(new Required(id.toString(), name, stack.getCount()));
          ItemStack icon = stack.copy();
          icon.setCount(1);
          ICONS.put(iconKey(id.toString(), name), icon);
        }
      }
    }
    if (score < 0) {
      return;
    }
    boolean changed = score != streakScore || high != highscore || days != daysUntilReset
        || !req.equals(REQUIRED) || !tiers.equals(TIERS);
    streakScore = score;
    if (high >= 0) {
      highscore = high;
    }
    if (days >= 0) {
      daysUntilReset = days;
    }
    REQUIRED.clear();
    REQUIRED.addAll(req);
    TIERS.clear();
    TIERS.addAll(tiers);
    if (changed) {
      save();
      org.slf4j.LoggerFactory.getLogger("openorigins").info(
          "[badger] task: {} streak={} resetDays={} tiers={}", REQUIRED, score, days, TIERS);
    }
  }

  private static Path file() {
    return FabricLoader.getInstance().getConfigDir().resolve("openorigins_badger.json");
  }

  public static synchronized void save() {
    try {
      JsonObject root = new JsonObject();
      root.addProperty("score", streakScore);
      root.addProperty("highscore", highscore);
      root.addProperty("days", daysUntilReset);
      JsonArray arr = new JsonArray();
      for (Required r : REQUIRED) {
        JsonObject o = new JsonObject();
        o.addProperty("id", r.itemId());
        o.addProperty("name", r.displayName());
        o.addProperty("count", r.count());
        arr.add(o);
      }
      root.add("required", arr);
      JsonArray ta = new JsonArray();
      for (Tier t : TIERS) {
        JsonObject o = new JsonObject();
        o.addProperty("name", t.name());
        o.addProperty("done", t.done());
        o.addProperty("total", t.total());
        ta.add(o);
      }
      root.add("tiers", ta);
      Files.writeString(file(), root.toString(), StandardCharsets.UTF_8);
    } catch (Exception ex) {
      org.slf4j.LoggerFactory.getLogger("openorigins").warn("[badger] save failed: {}", ex.toString());
    }
  }

  public static synchronized void load() {
    try {
      Path f = file();
      if (!Files.isRegularFile(f)) {
        return;
      }
      JsonObject root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
      streakScore = root.has("score") ? root.get("score").getAsInt() : 0;
      highscore = root.has("highscore") ? root.get("highscore").getAsInt() : 0;
      daysUntilReset = root.has("days") ? root.get("days").getAsInt() : 0;
      REQUIRED.clear();
      JsonArray arr = root.getAsJsonArray("required");
      if (arr != null) {
        for (JsonElement el : arr) {
          JsonObject o = el.getAsJsonObject();
          String nm = o.has("name") ? o.get("name").getAsString() : "";
          REQUIRED.add(new Required(o.get("id").getAsString(), nm, o.get("count").getAsInt()));
        }
      }
      TIERS.clear();
      JsonArray ta = root.getAsJsonArray("tiers");
      if (ta != null) {
        for (JsonElement el : ta) {
          JsonObject o = el.getAsJsonObject();
          TIERS.add(new Tier(o.get("name").getAsString(), o.get("done").getAsInt(), o.get("total").getAsInt()));
        }
      }
      org.slf4j.LoggerFactory.getLogger("openorigins")
          .info("[badger] loaded {} required from disk", REQUIRED.size());
    } catch (Exception ex) {
      org.slf4j.LoggerFactory.getLogger("openorigins").warn("[badger] load failed: {}", ex.toString());
    }
  }
}
