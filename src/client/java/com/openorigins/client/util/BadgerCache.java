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
  /** Parsed ms of taskTimeLeft and when that window ends (commit time + ms).
   *  The real task timer only counts down; a bigger value from another page
   *  (daily window) must not replace a live smaller timer. */
  private static volatile long taskTimeLeftMs;
  private static volatile long taskTimeLeftUntil;
  /** TEMP diagnostics; remove after the timer structure is confirmed. */
  private static long lastTempDump;

  private static final Pattern SCORE = Pattern.compile("current streak score:\\s*(\\d+)");
  private static final Pattern HIGH = Pattern.compile("streak highscore:\\s*(\\d+)");
  private static final Pattern DAYS = Pattern.compile("days until streak resets:\\s*(\\d+)");
  private static final Pattern TIER = Pattern.compile("(.+?):\\s*\\((\\d+)/(\\d+)\\)");

  public static List<Required> required() {
    return List.copyOf(REQUIRED);
  }

  /** Quest stages parsed from the streaks menu, e.g. Basic (2/5). */
  public static List<Tier> tiers() {
    return List.copyOf(TIERS);
  }

  /** Parses "23 hours and 59 minutes" / "6h 55m" style timers to ms (0 on fail). */
  static long parseTimeLeftMs(String raw) {
    String s = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
    long ms = 0;
    Matcher m = TIME_PART.matcher(s);
    while (m.find()) {
      long v = Long.parseLong(m.group(1));
      switch (m.group(2)) {
        case "d" -> ms += v * 86_400_000L;
        case "h" -> ms += v * 3_600_000L;
        case "m" -> ms += v * 60_000L;
        case "s" -> ms += v * 1_000L;
        default -> {}
      }
    }
    return ms;
  }

  /** Number followed by a duration letter or the start of a duration word. */
  private static final Pattern TIME_PART = Pattern.compile("(\\d+)\\s*([dhms])");

  /** Smallest parsed timer among "Time left:" name/lore candidates. */
  private static String minOf(java.util.List<long[]> ms, java.util.List<String> raw) {
    if (raw.isEmpty()) {
      return null;
    }
    int best = 0;
    for (int i = 1; i < ms.size(); i++) {
      if (ms.get(i)[0] < ms.get(best)[0]) {
        best = i;
      }
    }
    return raw.get(best);
  }

  /** Smallest parsed timer among "Next available:" lore values. */
  private static String minAvail(java.util.List<String> raw) {
    if (raw.isEmpty()) {
      return null;
    }
    int best = 0;
    for (int i = 1; i < raw.size(); i++) {
      if (parseTimeLeftMs(raw.get(i)) < parseTimeLeftMs(raw.get(best))) {
        best = i;
      }
    }
    return raw.get(best);
  }

  /**
   * Commits a timer string under the countdown lock: the real task timer
   * only counts down, so a bigger value from a foreign page must not
   * replace a live (unexpired) smaller one. A parse-zero string (unknown
   * format) only applies when no live timer exists.
   */
  private static void adoptAvailableTimer(String timeLeft) {
    if (timeLeft == null || timeLeft.isBlank()) {
      return;
    }
    long ms = parseTimeLeftMs(timeLeft);
    long now = System.currentTimeMillis();
    boolean curLive = taskTimeLeftMs > 0 && now < taskTimeLeftUntil;
    synchronized (BadgerCache.class) {
      if (curLive && ms >= taskTimeLeftMs) {
        return;
      }
      taskTimeLeft = timeLeft;
      taskTimeLeftMs = ms;
      taskTimeLeftUntil = ms > 0 ? now + ms : 0;
    }
  }

  /** Text after a lowercase prefix within a (lowercased, equal-length) line. */
  private static String timeValue(String lower, String prefix) {
    int i = lower.indexOf(prefix);
    return i < 0 ? "" : lower.substring(i + prefix.length()).trim();
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
    final java.util.List<long[]> timeCandidates = new ArrayList<>();
    final List<String> timeRaw = new ArrayList<>();
    final List<Boolean> timeFromLore = new ArrayList<>();
    final List<String> nextAvailRaw = new ArrayList<>();
    final boolean[] seenCompleted = {false};
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
        String raw = name.substring(Math.min(name.length(), "time left:".length())).trim();
        timeCandidates.add(new long[]{parseTimeLeftMs(raw)});
        timeRaw.add(raw);
        timeFromLore.add(Boolean.FALSE);
      }
      if (lower.contains("next available:")) {
        nextAvailRaw.add(timeValue(lower, "next available:"));
      }
      if (lower.contains("tasks completed")) {
        seenCompleted[0] = true;
      }
      if (lower.contains("go to badger to accept") && !lines.isEmpty()) {
        Matcher t = TIER.matcher(lines.get(0).getString());
        if (t.find() && tiers.stream().noneMatch(x ->
            x.name().equals(t.group(1).trim()))) {
          // Several quest items repeat the current stage line; keep one.
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
        if (s.startsWith("time left:")) {
          // Task items may carry their own expiry in lore; the menu header
          // item's name shows the daily window instead.
          String raw = line.getString().substring(
              Math.min(line.getString().length(), "time left:".length())).trim();
          timeCandidates.add(new long[]{parseTimeLeftMs(raw)});
          timeRaw.add(raw);
          timeFromLore.add(Boolean.TRUE);
        } else if (s.contains("next available:")) {
          nextAvailRaw.add(timeValue(s, "next available:"));
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
    if (score >= 0 && System.currentTimeMillis() - lastTempDump > 10_000
        && (!timeCandidates.isEmpty() || !nextAvailRaw.isEmpty())) {
      lastTempDump = System.currentTimeMillis();
      StringBuilder sb = new StringBuilder("[badgerTEMP] candidates:");
      for (int i = 0; i < timeRaw.size(); i++) {
        sb.append(" '").append(timeRaw.get(i)).append("'")
            .append(timeFromLore.get(i) ? "[lore]" : "[name]");
      }
      for (String raw : nextAvailRaw) {
        sb.append(" '").append(raw).append("'[avail]");
      }
      org.slf4j.LoggerFactory.getLogger("openorigins").info(sb.toString());
    }
    String timeLeft = minOf(timeCandidates, timeRaw);
    if (score < 0) {
      // Availability page (no streak-score lore): 'Tasks completed!' with a
      // 'Next available: <timer>' — the real task timer source seen in the
      // transport-menu lore captures. Adopting it keeps the HUD counting
      // down while the accept page itself carries no timer item.
      if (seenCompleted[0] && !nextAvailRaw.isEmpty()) {
        String fl = minAvail(nextAvailRaw);
        if (fl != null) {
          adoptAvailableTimer(fl);
        }
      }
      return;
    }
    boolean changed = score != streakScore || high != highscore || days != daysUntilReset
        || !req.equals(REQUIRED)
        || (!tiers.isEmpty() && !tiers.equals(TIERS))
        || (timeLeft != null && !timeLeft.equals(taskTimeLeft));
    streakScore = score;
    if (high >= 0) {
      highscore = high;
    }
    if (days >= 0) {
      daysUntilReset = days;
    }
    adoptAvailableTimer(timeLeft);
    if (req.isEmpty() && !REQUIRED.isEmpty()) {
      if (delivered > 0) {
        // Everything turned in: the shrinkage is real (Delivered! markers),
        // not a half-loaded menu. Clearing schedules a fresh-task fetch.
        forceAcceptUntil = 0;
        REQUIRED.clear();
        TIERS.clear();
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
    // Tier lore ("Basic (2/5)" lines) only exists on quest pages; mid-accept
    // or delivery scans list none, and wiping the list there made the HUD
    // difficulty row blink away. Only overwrite with a non-empty tier list.
    if (!tiers.isEmpty()) {
      TIERS.clear();
      TIERS.addAll(tiers);
    }
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

  /** Debug purge (DebugCaches): wipe state and disk JSON; next scan refills. */
  public static synchronized void clearAll() {
    REQUIRED.clear();
    TIERS.clear();
    ICONS.clear();
    streakScore = 0;
    highscore = 0;
    daysUntilReset = 0;
    taskTimeLeft = "";
    taskTimeLeftMs = 0;
    taskTimeLeftUntil = 0;
    try {
      Files.deleteIfExists(file());
    } catch (Exception ignored) {
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
      taskTimeLeftMs = parseTimeLeftMs(taskTimeLeft);
      taskTimeLeftUntil = taskTimeLeftMs > 0 ? System.currentTimeMillis() + taskTimeLeftMs : 0;
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
        taskTimeLeftMs = 0;
        taskTimeLeftUntil = 0;
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
