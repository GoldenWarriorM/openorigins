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
  /** One lazy save at the end of a capture batch instead of one per scan. */
  private static final Runnable SAVER = BadgerCache::save;

  private static void flushSave() {
    SaveBatch.mark(SAVER);
  }
  private BadgerCache() {}

  public record Required(String itemId, String displayName, int count) {}
  public record Tier(String name, int done, int total) {}

  private static final List<Required> REQUIRED = new java.util.concurrent.CopyOnWriteArrayList<>();
  private static final List<Tier> TIERS = new java.util.concurrent.CopyOnWriteArrayList<>();
  private static final java.util.Map<String, ItemStack> ICONS = new java.util.concurrent.ConcurrentHashMap<>();
  private static volatile int streakScore;
  private static volatile int highscore;
  private static volatile int daysUntilReset;
  private static volatile String taskTimeLeft = "";

  private static final Pattern SCORE = Pattern.compile("current streak score:\\s*(\\d+)");
  private static final Pattern HIGH = Pattern.compile("streak highscore:\\s*(\\d+)");
  private static final Pattern DAYS = Pattern.compile("days until streak resets:\\s*(\\d+)");
  private static final Pattern TIER = Pattern.compile("(.+?):\\s*\\((\\d+)/(\\d+)\\)");

  public static List<Required> required() {
    return List.copyOf(REQUIRED);
  }

  /** Server-menu captions that leaked into REQUIRED before the sentinel
   *  fix; never legitimate badger task items. */
  private static boolean isMenuJunk(String name) {
    if (name == null || name.isBlank()) {
      return true;
    }
    String s = name.toLowerCase(java.util.Locale.ROOT).strip();
    return s.equals("your listings") || s.equals("refresh") || s.equals("shrink view")
        || s.equals("← back") || s.equals("back") || s.equals("events")
        || s.equals("your profile") || s.equals("badges") || s.equals("realms")
        || s.equals("social") || s.equals("close") || s.equals("learn more");
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

  public static String taskTimeLeft() {
    return taskTimeLeft;
  }

  public static boolean hasTask() {
    return !REQUIRED.isEmpty();
  }

  private static volatile String lastTaskClickKey;
  private static volatile int lastTaskClickWindow = -1;
  private static volatile long lastTaskClickMs;

  /** Called when a task item is clicked in a server menu. */
  public static void noteTaskClick(int windowId, String itemId, String displayName) {
    lastTaskClickKey = itemId + "\n" + displayName;
    lastTaskClickWindow = windowId;
    lastTaskClickMs = System.currentTimeMillis();
  }

  /**
   * Server closed a menu right after a task-item click: the delivery went
   * through but the menu closed before the Delivered! state synced.
   */
  public static void noteServerClose(int windowId) {
    if (lastTaskClickKey == null
        || windowId != lastTaskClickWindow
        || System.currentTimeMillis() - lastTaskClickMs > 15000) {
      return;
    }
    String key = lastTaskClickKey;
    lastTaskClickKey = null;
    boolean removed = REQUIRED.removeIf(
        r -> (r.itemId() + "\n" + r.displayName()).equals(key));
    if (removed) {
      flushSave();
      org.slf4j.LoggerFactory.getLogger("openorigins").info(
          "[badger] delivered on close: {}", key.replace('\n', ' '));
    }
  }

  private static volatile boolean hadTask;
  private static volatile boolean deliveredRefreshSent;
  private static volatile boolean refreshPending;
  private static volatile long forceAcceptUntil;

  /** True once when a previously seen task fully empties (all delivered). */
  public static boolean needsDeliveredRefresh() {
    if (!REQUIRED.isEmpty()) {
      hadTask = true;
      deliveredRefreshSent = false;
      return false;
    }
    return hadTask && !deliveredRefreshSent;
  }

  public static void markDeliveredRefreshSent() {
    deliveredRefreshSent = true;
  }

  /** External event (e.g. quest-complete chat) invalidated the task list. */
  public static void requestRefresh() {
    hadTask = true;
    deliveredRefreshSent = false;
    // Single deliveries leave REQUIRED non-empty, so the empty-driven fetch
    // below never fires: schedule an explicit one, and accept its (smaller)
    // result instead of mistaking it for lag.
    refreshPending = true;
    forceAcceptUntil = System.currentTimeMillis() + 60000L;
    com.openorigins.client.util.BackgroundMenu.clearManualCooldown();
    com.openorigins.client.util.DebugLog.info("[badger] refresh requested");
  }

  /** True once when an explicit refresh fetch should be sent. */
  public static boolean consumeRefreshPending() {
    if (!refreshPending) {
      return false;
    }
    refreshPending = false;
    return true;
  }

  /** Peek without consuming (consume only after tryArm succeeds). */
  public static boolean hasRefreshPending() {
    return refreshPending;
  }

  /** Scans any open container; parses only when it looks like the streaks menu. */
  public static void scan(AbstractContainerScreen<?> screen) {
    if (screen == null || screen.getMenu() == null) return;
    scanMenu(screen.getMenu());
  }

  public static void scanMenu(net.minecraft.world.inventory.AbstractContainerMenu menu) {
    if (menu == null) return;
    SaveBatch.begin();
    try {
      scanMenu0(menu);
    } finally {
      SaveBatch.end();
    }
  }

  private static void scanMenu0(net.minecraft.world.inventory.AbstractContainerMenu menu) {
    final List<Required> req = new ArrayList<>();
    final List<Tier> tiers = new ArrayList<>();
    // Lambda captures need holders: score (0), high (1), days (2), delivered (3).
    // Sentinels restored (-1 = "not found"): the score<0 guard below must
    // reject menus that aren't the badger streaks menu. Regression from the
    // MenuLore refactor had zero-init, committing every menu's items.
    final int[] counters = {-1, -1, -1, 0};
    final String[] timeLeftRef = {null};
    MenuLore.forEachTop(menu, (idx, stack) -> {
      String name = stack.getHoverName().getString();
      ItemLore lore = stack.get(DataComponents.LORE);
      List<Component> lines = lore == null ? List.of() : lore.lines();
      String lower = name.toLowerCase(Locale.ROOT);
      if (lower.equals("delivered!")) {
        counters[3]++;
      }
      Matcher m = SCORE.matcher(lower);
      if (m.find()) {
        counters[0] = Integer.parseInt(m.group(1));
      }
      Matcher d = DAYS.matcher(lower);
      if (d.find()) {
        counters[2] = Integer.parseInt(d.group(1));
      }
      if (lower.startsWith("time left:")) {
        timeLeftRef[0] = name.substring(Math.min(name.length(), "time left:".length())).trim();
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
          counters[1] = Integer.parseInt(h.group(1));
        }
      }
      if ((lines.isEmpty() || CustomItemIds.resolve(stack).isPresent()) && req.size() < 6) {
        String ln = name.toLowerCase(Locale.ROOT);
        boolean ui = ln.contains("quit") || ln.contains("learn more") || ln.contains("go to badger")
            || ln.contains("streak score") || ln.contains("streak resets") || ln.contains("upcoming rewards")
            || ln.contains("close") || ln.contains("time left") || ln.contains("delivered");
        if (!ui) {
          Identifier id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
          if (!id.getPath().equals("air")) {
            req.add(new Required(id.toString(), name, stack.getCount()));
            ItemStack icon = stack.copy();
            icon.setCount(1);
            ICONS.put(iconKey(id.toString(), name), icon);
          }
        }
      }
    });
    int score = counters[0];
    int high = counters[1];
    int days = counters[2];
    int delivered = counters[3];
    String timeLeft = timeLeftRef[0];
    if (score < 0) {
      return;
    }
    boolean changed = score != streakScore || high != highscore || days != daysUntilReset
        || !req.equals(REQUIRED) || !tiers.equals(TIERS)
        || (timeLeft != null && !timeLeft.equals(taskTimeLeft));
    streakScore = score;
    if (high >= 0) {
      highscore = high;
    }
    if (days >= 0) {
      daysUntilReset = days;
    }
    if (timeLeft != null) {
      taskTimeLeft = timeLeft;
    }
    if (req.isEmpty() && !REQUIRED.isEmpty()) {
      if (delivered > 0) {
        // Everything turned in: the shrinkage is real (Delivered! markers),
        // not a half-loaded menu. Clearing schedules a fresh-task fetch.
        forceAcceptUntil = 0;
        REQUIRED.clear();
        com.openorigins.client.util.DebugLog.info("[badger] all delivered, task cleared");
      } else {
        com.openorigins.client.util.DebugLog.info("[badger] ignoring empty scan (lag?)");
      }
    } else if (!REQUIRED.isEmpty() && !req.isEmpty() && req.size() < REQUIRED.size()
        && req.stream().allMatch(r -> REQUIRED.stream().anyMatch(
            o -> o.itemId().equals(r.itemId()) && o.displayName().equals(r.displayName())))) {
      if (delivered > 0) {
        forceAcceptUntil = 0;
        REQUIRED.clear();
        REQUIRED.addAll(req);
        com.openorigins.client.util.DebugLog.info("[badger] accepted delivery scan ({} delivered)",
            delivered);
      } else if (System.currentTimeMillis() < forceAcceptUntil) {
        forceAcceptUntil = 0;
        REQUIRED.clear();
        REQUIRED.addAll(req);
        com.openorigins.client.util.DebugLog.info("[badger] accepted partial scan after delivery");
      } else {
        com.openorigins.client.util.DebugLog.info("[badger] ignoring partial scan (lag?)");
      }
    } else {
      forceAcceptUntil = 0;
      REQUIRED.clear();
      REQUIRED.addAll(req);
    }
    TIERS.clear();
    TIERS.addAll(tiers);
    if (changed) {
      SaveBatch.mark(SAVER);
      com.openorigins.client.util.DebugLog.info(
          "[badger] task: {} streak={} resetDays={} tiers={}", REQUIRED, score, days, TIERS);
    }
  
  }


  private static Path file() {
    return FabricLoader.getInstance().getConfigDir().resolve("openorigins_badger.json");
  }

  /** Trims capture-session leftovers on disconnect. Icons for the current
   *  task stay (the HUD needs them); orphans from long-past tasks go. */
  public static void noteDisconnect() {
    // Icons for the current task stay (the HUD needs them); orphans from
    // long-past tasks go.
    java.util.Set<String> kept = new java.util.HashSet<>();
    for (Required r : REQUIRED) {
      kept.add(iconKey(r.itemId(), r.displayName()));
    }
    ICONS.keySet().removeIf(k -> !kept.contains(k));
  }

  public static synchronized void save() {
    try {
      JsonObject root = new JsonObject();
      root.addProperty("score", streakScore);
      root.addProperty("highscore", highscore);
      root.addProperty("days", daysUntilReset);
      root.addProperty("timeleft", taskTimeLeft);
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
      com.openorigins.client.util.DebugLog.warn("[badger] save failed: {}", ex.toString());
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
      taskTimeLeft = root.has("timeleft") ? root.get("timeleft").getAsString() : "";
      REQUIRED.clear();
      JsonArray arr = root.getAsJsonArray("required");
      boolean junkDropped = false;
      if (arr != null) {
        for (JsonElement el : arr) {
          JsonObject o = el.getAsJsonObject();
          String nm = o.has("name") ? o.get("name").getAsString() : "";
          if (isMenuJunk(nm)) {
            // Heals a saved list polluted by a foreign-menu scan.
            junkDropped = true;
            continue;
          }
          REQUIRED.add(new Required(o.get("id").getAsString(), nm, o.get("count").getAsInt()));
        }
      }
      if (junkDropped) {
        // The timer came from the same poisoned scan; drop it too and wait
        // for the next real streaks-menu scan to repopulate.
        taskTimeLeft = "";
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
      hadTask = !REQUIRED.isEmpty();
    } catch (Exception ex) {
      com.openorigins.client.util.DebugLog.warn("[badger] load failed: {}", ex.toString());
    }
  }
}
