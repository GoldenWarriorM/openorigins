package com.openorigins.client.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * Caches home details (created/world/position lore) seen in the server's
 * own Homes container GUI, so our trimmed menu can show the same tooltips
 * and world-colored icons. Filled passively when the player opens that GUI.
 */
public final class HomeInfoCache {
  private HomeInfoCache() {}

  private record Info(List<Component> lore, String worldColor, long resetAt) {}

  private static final Map<String, Info> CACHE = new ConcurrentHashMap<>();
  private static final Map<String, String> CONTAINER_NAMES = new ConcurrentHashMap<>();
  private static volatile long lastHomesGuiMs;
  private static volatile long lastEmptyCompleteMs;
  private static volatile long lastZeroGuiMs;

  /** Authoritative display names from /homes (empty when none). */
  public static Map<String, String> containerNames() {
    return Map.copyOf(CONTAINER_NAMES);
  }

  public static boolean containerFresh() {
    return System.currentTimeMillis() - lastHomesGuiMs < 5 * 60000L;
  }
  private static final List<String> LAYOUT = new java.util.concurrent.CopyOnWriteArrayList<>();
  private static final List<String> NAMES = new java.util.concurrent.CopyOnWriteArrayList<>();
  private static final java.util.Map<String, Long> TOMBSTONES =
      new java.util.concurrent.ConcurrentHashMap<>();
  private static volatile int CAPACITY;
  public static final String DEFAULT_ICON = "textures/custom_ui/transport_menu/button_homes.png";
  public static final String PLUS_ICON = "textures/custom/ui/slot_add.png";

  public static int capacity() {
    return CAPACITY;
  }

  /** Parses lines like "5 home slots" / "Home slots: 5" from rank perk lore. */
  static int parseCapacity(String line) {
    String s = line.toLowerCase(java.util.Locale.ROOT);
    if (!(s.contains("home") || s.contains("slot"))) {
      return 0;
    }
    java.util.regex.Matcher m = java.util.regex.Pattern.compile(
        "(\\d+)\\s*(?:x\\s*)?(?:home|house)?\\s*slots?|(?:home|house)s?\\s*slots?\\s*[:x]?\\s*(\\d+)")
        .matcher(s);
    if (m.find()) {
      String num = m.group(1) != null ? m.group(1) : m.group(2);
      try {
        int v = Integer.parseInt(num);
        return (v > 0 && v <= 100) ? v : 0;
      } catch (NumberFormatException ignored) {
      }
    }
    return 0;
  }

  public static String key(String name) {
    if (name == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    String lower = name.toLowerCase(Locale.ROOT);
    for (int i = 0; i < lower.length(); i++) {
      char c = lower.charAt(i);
      if (c != ' ' && c != '_' && !Character.isISOControl(c)) {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  public static boolean has(String name) {
    return CACHE.containsKey(key(name));
  }

  public static void put(String name, List<Component> lore) {
    if (name == null || name.isBlank() || lore == null || lore.isEmpty()) {
      return;
    }
    String color = null;
    for (Component line : lore) {
      String s = line.getString().toLowerCase(Locale.ROOT);
      if (s.contains("world:")) {
        for (String c : new String[]{"blue", "green", "yellow", "red", "orange", "purple"}) {
          if (s.contains(c)) {
            color = c;
            break;
          }
        }
        break;
      }
    }
    String k = key(name);
    Info prev = CACHE.get(k);
    List<Component> copy = List.copyOf(lore);
    long anchor = color == null ? 0 : OpenWorldCache.resetAt(color);
    if (prev == null && anchor > 0 && anchor <= System.currentTimeMillis()) {
      anchor = 0;
    }
    if (prev != null && java.util.Objects.equals(prev.worldColor(), color) && prev.lore().equals(copy)
        && prev.resetAt() == anchor) {
      return;
    }
    long now = System.currentTimeMillis();
    if (prev != null && prev.resetAt() > 0 && prev.resetAt() <= now && anchor > now
        && !com.openorigins.client.util.HomeCreationConfirm.isFresh(name)) {
      // Cycle turnover: this entry expired with its old world cycle. A fresh
      // anchor belongs to the new cycle and must not silently resurrect it
      // (that paints dead teleports with the active world's timer instead of
      // expired). Only an explicit re-creation revives (fresh-confirm path).
      // Lore still updates; the timer stays expired.
      CACHE.put(k, new Info(copy, color, prev.resetAt()));
      save();
      com.openorigins.client.util.DebugLog.info("[homes] kept expired for '{}' (new anchor ignored)",
          name);
      return;
    }
    CACHE.put(k, new Info(copy, color, anchor));
    save();
  }

  /**
   * Homes that never had a timer adopt fresh anchors silently.
   * Expired ones stay unavailable until their own lore is re-captured.
   */
  public static void adoptReset(String worldColor, long at) {
    boolean changed = false;
    for (Map.Entry<String, Info> e : CACHE.entrySet()) {
      Info info = e.getValue();
      if (info.resetAt() == 0 && worldColor.equals(info.worldColor())) {
        e.setValue(new Info(info.lore(), info.worldColor(), at));
        changed = true;
      }
    }
    if (changed) {
      save();
    }
  }

  /** Backfills missing per-home timers from already-known valid anchors. */
  public static void backfillResets() {
    boolean changed = false;
    long now = System.currentTimeMillis();
    for (Map.Entry<String, Info> e : CACHE.entrySet()) {
      Info info = e.getValue();
      if (info.resetAt() == 0 && info.worldColor() != null) {
        long anchor = OpenWorldCache.resetAt(info.worldColor());
        if (anchor > now) {
          e.setValue(new Info(info.lore(), info.worldColor(), anchor));
          changed = true;
        }
      }
    }
    if (changed) {
      save();
    }
  }

  public static String worldColor(String name) {
    Info info = CACHE.get(key(name));
    return info == null ? null : info.worldColor();
  }

  public static String iconPath(String name) {
    Info info = CACHE.get(key(name));
    if (info != null && info.worldColor() != null) {
      return "textures/custom/ui/homes/open_world_" + info.worldColor() + ".png";
    }
    return DEFAULT_ICON;
  }

  /** Server lore verbatim (keeps pack glyphs/colors), or a basic fallback. */
  public static Component tooltip(String name) {
    Info info = CACHE.get(key(name));
    if (info != null && !info.lore().isEmpty()) {
      MutableComponent tip = Component.literal(name == null ? "" : name);
      for (Component line : info.lore()) {
        if (line.getString().toLowerCase(Locale.ROOT).contains("reset")) {
          continue;
        }
        tip.append(Component.literal("\n")).append(line);
      }
      long now = System.currentTimeMillis();
      if (info.resetAt() > now) {
        String cd = OpenWorldCache.countdownAt(info.resetAt());
        long left = info.resetAt() - now;
        net.minecraft.ChatFormatting color = net.minecraft.ChatFormatting.GRAY;
        if (left < 30 * 60000L) {
          color = net.minecraft.ChatFormatting.RED;
        } else if (left < 60 * 60000L) {
          color = net.minecraft.ChatFormatting.GOLD;
        }
        tip.append(Component.literal("\n"))
            .append(Component.translatable("menu.openorigins.homes.reset", cd).withStyle(color));
      } else if (info.resetAt() > 0) {
        tip.append(Component.literal("\n"))
            .append(Component.translatable("menu.openorigins.homes.unavailable")
                .withStyle(net.minecraft.ChatFormatting.RED));
      }
      return tip;
    }
    return Component.literal(name == null ? "" : name).withStyle(ChatFormatting.GOLD)
        .append(Component.literal("\n"))
        .append(Component.translatable("menu.openorigins.homes.teleport_hint").withStyle(ChatFormatting.GREEN))
        .append(Component.literal("\n"))
        .append(Component.translatable("menu.openorigins.homes.delete_hint").withStyle(ChatFormatting.YELLOW));
  }

  /**
   * Scans any open container for home-like item lore (identified by content,
   * not by title — server titles are pack glyphs). Returns captures count.
   */
  public static int captureFrom(AbstractContainerScreen<?> screen) {
    if (screen == null) {
      return 0;
    }
    return captureMenu(screen.getMenu());
  }

  /** Core capture working on a bare menu (visible screen or background). */
  public static int captureMenu(net.minecraft.world.inventory.AbstractContainerMenu menu) {
    if (menu == null) {
      return 0;
    }
    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
    Object playerInv = (mc != null && mc.player != null) ? mc.player.getInventory() : null;
    int n = 0;
    int loreSlots = 0;
    int topCount = 0;
    String sample = null;
    List<String> layout = new ArrayList<>();
    java.util.Set<String> others = new java.util.TreeSet<>();
    Map<String, String> guiNames = new java.util.HashMap<>();
    boolean sawRank = false;
    boolean sawCmd = false;
    for (Slot slot : menu.slots) {
      if (!slot.hasItem()) {
        continue;
      }
      if (playerInv != null && slot.container == playerInv) {
        continue;
      }
      topCount++;
      ItemStack stack = slot.getItem();
      String name = stack.getHoverName().getString();
      ItemLore lore = stack.get(DataComponents.LORE);
      boolean hasLore = lore != null && !lore.lines().isEmpty();
      if (hasLore) {
        loreSlots++;
        if (sample == null) {
          sample = "'" + name + "' / '" + lore.lines().get(0).getString() + "'";
        }
      }
      boolean looksLikeHome = false;
      boolean looksLikeVisit = false;
      if (hasLore) {
        boolean hasWorld = false;
        boolean hasPosition = false;
        for (Component line : lore.lines()) {
          String s = line.getString();
          if (s.contains("World:")) {
            hasWorld = true;
          }
          if (s.contains("Position:")) {
            hasPosition = true;
          }
          if (s.contains("visit Realm") || s.contains("pin Realm") || s.contains("Players:")) {
            looksLikeVisit = true;
          }
          if (s.contains("World:") || s.contains("Position:") || s.contains("Created:")) {
            looksLikeHome = true;
          }
        }
        looksLikeHome = looksLikeHome && hasWorld && hasPosition && !looksLikeVisit;
      }
      if (hasLore) {
        for (Component capLine : lore.lines()) {
          int cap = parseCapacity(capLine.getString());
          if (cap > 0 && cap != CAPACITY) {
            CAPACITY = cap;
            save();
            org.slf4j.LoggerFactory.getLogger("openorigins")
                .info("[homes-lore] capacity: {} (from '{}')", cap, capLine.getString());
          }
        }
      }
      if (looksLikeHome) {
        put(name, lore.lines());
        layout.add("home:" + key(name));
        guiNames.put(key(name), name);
        n++;
        StringBuilder lb = new StringBuilder();
        for (net.minecraft.network.chat.Component lc : lore.lines()) {
          if (lb.length() > 0) lb.append(" // ");
          lb.append(lc.getString());
          if (lb.length() > 300) break;
        }
        com.openorigins.client.util.DebugLog.info("[homes-lore] +home '{}' lore: '{}'",
            name, lb.toString());
      } else {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("rank up to unlock")) {
          sawRank = true;
        }
        if (lower.startsWith("cmd:")) {
          sawCmd = true;
        }
        layout.add("locked:" + name);
        String fullLore = "<no lore>";
        if (hasLore) {
          StringBuilder lb = new StringBuilder();
          for (Component lc : lore.lines()) {
            if (lb.length() > 0) {
              lb.append(" // ");
            }
            lb.append(lc.getString());
            if (lb.length() > 160) {
              break;
            }
          }
          fullLore = lb.toString();
        }
        others.add(name + " :: " + fullLore);
      }
    }
    if (loreSlots > 0 && n == 0) {
      org.slf4j.LoggerFactory.getLogger("openorigins")
          .info("[homes-lore] {} lore slots, no homes. sample: {}", loreSlots, sample);
    }
    if ((n > 0 || sawRank || sawCmd) && !layout.isEmpty() && !layout.equals(LAYOUT)) {
      LAYOUT.clear();
      LAYOUT.addAll(layout);
      EXTRA_PLUS = 0;
      save();
      if (!others.isEmpty()) {
        org.slf4j.LoggerFactory.getLogger("openorigins")
            .info("[homes-lore] layout: {} homes, {} other. others: {}", n, layout.size() - n, others);
      }
    }
    if (n > 0 || sawRank || sawCmd) {
      CONTAINER_NAMES.clear();
      CONTAINER_NAMES.putAll(guiNames);
      lastHomesGuiMs = System.currentTimeMillis();
      if (n == 0) {
        lastZeroGuiMs = System.currentTimeMillis();
      }
      for (Map.Entry<String, String> e : guiNames.entrySet()) {
        if (TOMBSTONES.containsKey(e.getKey())) {
          continue;
        }
        boolean known = false;
        for (String x : NAMES) {
          if (key(x).equals(e.getKey())) {
            known = true;
            break;
          }
        }
        if (!known) {
          NAMES.add(e.getValue());
          save();
        }
      }
      if (topCount >= 10) {
        boolean changed = NAMES.removeIf(x ->
            !guiNames.containsKey(key(x))
                && !HomeCreationConfirm.isFresh(x));
        if (changed) {
          org.slf4j.LoggerFactory.getLogger("openorigins")
              .info("[homes] reconciled names with menu");
        }
        changed = false;
        java.util.Iterator<Map.Entry<String, String>> cit = CONTAINER_NAMES.entrySet().iterator();
        while (cit.hasNext()) {
          if (!guiNames.containsKey(cit.next().getKey())) {
            cit.remove();
            changed = true;
          }
        }
        java.util.Iterator<String> lit = LAYOUT.iterator();
        while (lit.hasNext()) {
          String s = lit.next();
          if (s.startsWith("home:") && !guiNames.containsKey(s.substring(5))) {
            lit.remove();
            changed = true;
          }
        }
        if (changed) {
          save();
        }
      }
      if (n == 0 && topCount >= 10) {
        if (!NAMES.isEmpty() || LAYOUT.stream().anyMatch(x -> x.startsWith("home:"))) {
          NAMES.clear();
          LAYOUT.removeIf(x -> x.startsWith("home:"));
          save();
          org.slf4j.LoggerFactory.getLogger("openorigins")
              .info("[homes] pruned to empty by authoritative menu");
        }
      }
    }
    return n;
  }

  private static int EXTRA_PLUS;

  /** Instant free slot after a confirmed delete (server layout follows later). */
  public static void noteDeleted() {
    EXTRA_PLUS++;
  }

  public static List<String> names() {
    return List.copyOf(NAMES);
  }

  public static List<String> layout() {
    return List.copyOf(LAYOUT);
  }

  /** Adds a name immediately (e.g. server confirmed creation). @return true if changed. */
  public static synchronized boolean addName(String name) {
    if (name == null || name.isBlank()) {
      return false;
    }
    for (String n : NAMES) {
      if (n.equalsIgnoreCase(name)) {
        return false;
      }
    }
    NAMES.add(name);
    com.openorigins.client.util.DebugLog.info("[homes] +name via addName: '{}'", name);
    save();
    return true;
  }

  /** A free slot was taken: drop one CMD placeholder for instant count. */
  public static synchronized void consumeCmdSlot() {
    for (int i = 0; i < LAYOUT.size(); i++) {
      String e = LAYOUT.get(i);
      if (e.toLowerCase(java.util.Locale.ROOT).startsWith("locked:cmd:")) {
        LAYOUT.remove(i);
        save();
        return;
      }
    }
  }

  /** A slot was freed: append a CMD placeholder for instant count. */
  public static synchronized void addCmdSlot() {
    LAYOUT.add("locked:CMD: /sethome [name]");
    save();
  }

  /** Bundled server-lore template (decoded lazily) for empty caches. */
  private static volatile List<Component> defaultTemplate;

  private static List<Component> defaultLoreTemplate() {
    List<Component> t = defaultTemplate;
    if (t != null) {
      return t;
    }
    try (var in = HomeInfoCache.class.getResourceAsStream("/default_home_lore.txt")) {
      if (in != null) {
        String text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        List<Component> out = new ArrayList<>();
        for (String line : text.split("\n")) {
          line = line.strip();
          if (!line.isEmpty()) {
            out.add(decodeLine(line));
          }
        }
        if (out.size() >= 3) {
          defaultTemplate = List.copyOf(out);
          return defaultTemplate;
        }
      }
    } catch (Exception ex) {
      org.slf4j.LoggerFactory.getLogger("openorigins")
          .warn("[homes-lore] default template load failed: {}", ex.toString());
    }
    return null;
  }

  /** Provisional entry at creation time: position and date are known locally,
   * world comes from the live boss bar. Styles are cloned 1:1 from any cached
   * server lore (same world preferred); the next real capture overwrites this.
   * No parsing of /homes is needed for the tooltip to look final. */
  public static synchronized void provisional(String name, net.minecraft.core.BlockPos pos) {
    if (name == null || name.isBlank() || pos == null) {
      return;
    }
    String k = key(name);
    if (CACHE.containsKey(k)) {
      return;
    }
    String color = com.openorigins.client.util.OpenWorldCache.currentWorldColor();
    List<Component> template = null;
    for (Map.Entry<String, Info> e : CACHE.entrySet()) {
      if (color != null && color.equals(e.getValue().worldColor())) {
        template = e.getValue().lore();
        break;
      }
      if (template == null && e.getValue().lore().size() >= 3) {
        template = e.getValue().lore();
      }
    }
    if (template == null) {
      template = defaultLoreTemplate();
    }
    java.time.LocalDate date = java.time.LocalDate.now();
    String created = date.getMonth().getDisplayName(java.time.format.TextStyle.FULL,
        java.util.Locale.US) + " " + date.getDayOfMonth() + ", " + date.getYear();
    String coords = pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    List<Component> lore;
    if (template != null && template.size() >= 3) {
      List<Component> built = new ArrayList<>(template.size());
      built.add(styled(template.get(0), "Created: ", created));
      String worldName = color == null
          ? template.get(1).getString().replaceFirst("(?i)^world:\\s*", "")
          : com.openorigins.client.util.OpenWorldCache.worldName(color);
      built.add(styled(template.get(1), "World: ", worldName));
      built.add(styled(template.get(2), "Position: ", coords));
      for (int i = 3; i < template.size(); i++) {
        built.add(template.get(i).copy());
      }
      lore = List.copyOf(built);
    } else {
      lore = List.of(
          Component.literal("Created: " + created),
          Component.literal("World: "
              + (color == null ? "" : com.openorigins.client.util.OpenWorldCache.worldName(color))),
          Component.literal("Position: " + coords));
    }
    long anchor = color == null ? 0 : OpenWorldCache.resetAt(color);
    if (anchor <= System.currentTimeMillis()) {
      anchor = 0;
    }
    CACHE.put(k, new Info(List.copyOf(lore), color, anchor));
    save();
  }

  /** Rebuilds a two-part lore line (fixed prefix + variable value), keeping
   * the template's root and value styles 1:1. Extra lines are copied as-is. */
  private static Component styled(Component template, String prefix, String value) {
    MutableComponent out = Component.literal(prefix).withStyle(template.getStyle());
    List<Component> sibs = template.getSiblings();
    if (sibs.isEmpty()) {
      // Single-part line: whole text is the old value.
      return out.append(Component.literal(value).withStyle(template.getStyle()));
    }
    boolean first = true;
    for (Component s : sibs) {
      if (first) {
        // First sibling holds the variable part (date / world / coords).
        MutableComponent val = Component.literal(value).withStyle(s.getStyle());
        for (Component sub : s.getSiblings()) {
          val.append(sub.copy());
        }
        out.append(val);
        first = false;
      } else {
        out.append(s.copy());
      }
    }
    return out;
  }

  /** Drops cached lore/timer for a name (re-created homes start fresh). */
  public static synchronized void dropCache(String name) {
    if (CACHE.remove(key(name)) != null) {
      save();
    }
  }

  /** Drops a name immediately (e.g. server confirmed deletion). @return true if changed. */
  public static synchronized boolean removeName(String name) {
    String k = key(name);
    TOMBSTONES.put(k, System.currentTimeMillis());
    boolean changed = NAMES.removeIf(n -> n.equalsIgnoreCase(name));
    if (CONTAINER_NAMES.remove(k) != null) {
      changed = true;
    }
    if (LAYOUT.removeIf(e -> e.equals("home:" + k))) {
      changed = true;
    }
    if (changed) {
      save();
    }
    return changed;
  }

  public static void untomb(String name) {
    TOMBSTONES.remove(key(name));
  }

  /**
   * Server rejected recent commands: drop fresh tombstones so the next
   * fetch restores the true state, then resync in background.
   */
  public static synchronized void rollback() {
    long now = System.currentTimeMillis();
    boolean dropped = TOMBSTONES.entrySet()
        .removeIf(e -> now - e.getValue() < 15000);
    if (dropped) {
      save();
    }
    com.openorigins.client.util.DebugLog.info("[homes] rollback, fresh tombstones dropped={}", dropped);
    HomeFetch.start();
  }

  /** Records a tab-complete answer for consensus pruning. */
  public static synchronized void noteComplete(List<String> names) {
    if (names == null || names.isEmpty()) {
      lastEmptyCompleteMs = System.currentTimeMillis();
    }
  }

  /**
   * Wipes everything when the server unanimously reports zero homes
   * (empty complete + fresh locks-only menu). Legacy garbage cleanup.
   */
  public static synchronized boolean maybePruneAll() {
    long now = System.currentTimeMillis();
    if (lastEmptyCompleteMs <= 0 || lastZeroGuiMs <= 0) {
      return false;
    }
    if (now - lastEmptyCompleteMs > 120000 || now - lastZeroGuiMs > 120000) {
      return false;
    }
    if (NAMES.isEmpty() && CONTAINER_NAMES.isEmpty()
        && LAYOUT.stream().noneMatch(e -> e.startsWith("home:"))) {
      return false;
    }
    NAMES.clear();
    CONTAINER_NAMES.clear();
    LAYOUT.removeIf(e -> e.startsWith("home:"));
    lastEmptyCompleteMs = 0;
    lastZeroGuiMs = 0;
    save();
    com.openorigins.client.util.DebugLog.info("[homes] pruned stale cache by consensus");
    return true;
  }

  /** Merges a fresh server response: only adds, removals come from verdicts. */
  public static synchronized boolean setNames(List<String> names) {
    boolean changed = false;
    for (String n : names) {
      if (TOMBSTONES.containsKey(key(n))) {
        continue;
      }
      boolean known = false;
      for (String e : NAMES) {
        if (e.equalsIgnoreCase(n)) {
          known = true;
          break;
        }
      }
      if (!known) {
        NAMES.add(n);
        changed = true;
      }
    }
    for (Map.Entry<String, String> e : HomeCreationConfirm.freshNames().entrySet()) {
      boolean known = false;
      for (String n : NAMES) {
        if (key(n).equals(e.getKey())) {
          known = true;
          break;
        }
      }
      if (!known) {
        NAMES.add(e.getValue());
        changed = true;
      }
    }
    if (changed) {
      save();
    }
    return changed;
  }

  /**
   * Ordered panel cells. Label is the display name (/homes), command is the
   * exact tab-complete name when paired, otherwise the label itself.
   */
  public record Cell(String label, String command, boolean plus) {}

  public static List<Cell> cellsFor(List<String> tabNames) {
    Map<String, String> tabByKey = new java.util.HashMap<>();
    for (String n : tabNames) {
      if (HomeCreationConfirm.isPending(n) || HomeCreationConfirm.isBlocked(n)) {
        continue;
      }
      tabByKey.put(key(n), n);
    }
    java.util.List<Cell> homes = new ArrayList<>();
    java.util.List<Cell> pluses = new ArrayList<>();
    java.util.Set<String> covered = new java.util.HashSet<>();
    for (String e : LAYOUT) {
      if (e.startsWith("home:")) {
        String k = e.substring(5);
        String display = CONTAINER_NAMES.get(k);
        String cmd = tabByKey.get(k);
        if (display != null && cmd != null) {
          homes.add(new Cell(display, cmd, false));
          covered.add(k);
        } else if (cmd != null) {
          homes.add(new Cell(cmd, cmd, false));
          covered.add(k);
        }
      } else if (e.toLowerCase(java.util.Locale.ROOT).startsWith("locked:cmd:")) {
        pluses.add(new Cell("", "", true));
      }
    }
    for (Map.Entry<String, String> en : tabByKey.entrySet()) {
      if (covered.contains(en.getKey())) {
        continue;
      }
      homes.add(new Cell(en.getValue(), en.getValue(), false));
    }
    java.util.List<Cell> cells = new ArrayList<>(homes);
    cells.addAll(pluses);
    int max = com.openorigins.client.OpenOriginsConfig.getInstance().getHomeCapacity();
    if (max < 1 || max > 15) {
      max = 5;
    }
    java.util.List<Cell> homesOnly = new ArrayList<>();
    java.util.List<Cell> plusOnly = new ArrayList<>();
    for (Cell c : cells) {
      (c.plus() ? plusOnly : homesOnly).add(c);
    }
    java.util.List<Cell> out = new ArrayList<>(homesOnly);
    while (out.size() < max && out.size() < 15 && !plusOnly.isEmpty()) {
      out.add(plusOnly.remove(0));
    }
    return out.size() > 15 ? out.subList(0, 15) : out;
  }

  private static Path file() {
    return FabricLoader.getInstance().getConfigDir().resolve("openorigins_homes.json");
  }

  private static String encodeLine(Component line) {
    ByteBuf buf = Unpooled.buffer();
    try {
      net.minecraft.network.chat.ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.encode(buf, line);
      byte[] b = new byte[buf.readableBytes()];
      buf.readBytes(b);
      return Base64.getEncoder().encodeToString(b);
    } finally {
      buf.release();
    }
  }

  private static Component decodeLine(String s) {
    byte[] b = Base64.getDecoder().decode(s);
    ByteBuf buf = Unpooled.wrappedBuffer(b);
    try {
      return net.minecraft.network.chat.ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.decode(buf);
    } finally {
      buf.release();
    }
  }

  /** Persists cache to disk (only called when data actually changed). */
  public static synchronized void save() {
    try {
      JsonArray arr = new JsonArray();
      for (Map.Entry<String, Info> e : CACHE.entrySet()) {
        JsonObject o = new JsonObject();
        o.addProperty("key", e.getKey());
        if (e.getValue().worldColor() != null) {
          o.addProperty("world", e.getValue().worldColor());
        }
        if (e.getValue().resetAt() > 0) {
          o.addProperty("resetAt", e.getValue().resetAt());
        }
        JsonArray lore = new JsonArray();
        for (Component line : e.getValue().lore()) {
          lore.add(encodeLine(line));
        }
        o.add("lore", lore);
        arr.add(o);
      }
      JsonObject root = new JsonObject();
      root.add("homes", arr);
      JsonObject res = new JsonObject();
      for (Map.Entry<String, Long> e : OpenWorldCache.snapshot().entrySet()) {
        res.addProperty(e.getKey(), e.getValue());
      }
      root.add("resets", res);
      JsonObject wn = new JsonObject();
      for (OpenWorldCache.WorldEntry w : OpenWorldCache.worlds()) {
        wn.addProperty(w.color(), w.name());
      }
      root.add("worldNames", wn);
      JsonArray nm = new JsonArray();
      for (String n : NAMES) {
        nm.add(n);
      }
      root.add("names", nm);
      JsonObject tb = new JsonObject();
      for (Map.Entry<String, Long> t : TOMBSTONES.entrySet()) {
        tb.addProperty(t.getKey(), t.getValue());
      }
      root.add("tombstones", tb);
      if (CAPACITY > 0) {
        root.addProperty("capacity", CAPACITY);
      }
      JsonArray lay = new JsonArray();
      for (String e : LAYOUT) {
        lay.add(e);
      }
      root.add("layout", lay);
      Files.writeString(file(), root.toString(), StandardCharsets.UTF_8);
    } catch (Exception ex) {
      org.slf4j.LoggerFactory.getLogger("openorigins")
          .warn("[homes-lore] save failed: {}", ex.toString());
    }
  }

  /** Loads disk cache at startup; server data overwrites it when captured. */
  public static synchronized void load() {
    try {
      Path f = file();
      if (!Files.isRegularFile(f)) {
        return;
      }
      JsonObject root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
      JsonArray arr = root.getAsJsonArray("homes");
      if (arr == null) {
        return;
      }
      int n = 0;
      for (JsonElement el : arr) {
        JsonObject o = el.getAsJsonObject();
        String k = o.get("key").getAsString();
        String world = o.has("world") ? o.get("world").getAsString() : null;
        long resetAt = 0;
        if (o.has("resetAt")) {
          try {
            resetAt = o.get("resetAt").getAsLong();
          } catch (Exception ignored) {
          }
        }
        List<Component> lore = new ArrayList<>();
        for (JsonElement le : o.getAsJsonArray("lore")) {
          lore.add(decodeLine(le.getAsString()));
        }
        if (!k.isEmpty() && !lore.isEmpty()) {
          CACHE.put(k, new Info(List.copyOf(lore), world, resetAt));
          n++;
        }
      }
      org.slf4j.LoggerFactory.getLogger("openorigins")
          .info("[homes-lore] loaded {} homes from disk", n);
      JsonArray lay = root.getAsJsonArray("layout");
      if (lay != null) {
        LAYOUT.clear();
        for (JsonElement el : lay) {
          LAYOUT.add(el.getAsString());
        }
      }
      JsonArray nm = root.getAsJsonArray("names");
      if (nm != null) {
        NAMES.clear();
        for (JsonElement el : nm) {
          NAMES.add(el.getAsString());
        }
      }
      TOMBSTONES.clear();
      if (root.has("tombstones") && root.get("tombstones").isJsonObject()) {
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("tombstones").entrySet()) {
          try {
            TOMBSTONES.put(e.getKey(), e.getValue().getAsLong());
          } catch (Exception ignored) {
          }
        }
      } else if (root.has("tombstones") && root.get("tombstones").isJsonArray()) {
        for (JsonElement el : root.getAsJsonArray("tombstones")) {
          TOMBSTONES.put(el.getAsString(), System.currentTimeMillis());
        }
      }
      JsonObject res = root.getAsJsonObject("resets");
      if (res != null) {
        Map<String, Long> map = new java.util.HashMap<>();
        for (Map.Entry<String, JsonElement> e : res.entrySet()) {
          try {
            map.put(e.getKey(), e.getValue().getAsLong());
          } catch (Exception ignored) {
          }
        }
        OpenWorldCache.restore(map);
      }
      JsonObject wn = root.getAsJsonObject("worldNames");
      if (wn != null) {
        for (Map.Entry<String, JsonElement> e : wn.entrySet()) {
          try {
            OpenWorldCache.putWorldName(e.getKey(), e.getValue().getAsString());
          } catch (Exception ignored) {
          }
        }
      }
      if (root.has("capacity")) {
        try {
          int c = root.get("capacity").getAsInt();
          if (c > 0 && c <= 100) {
            CAPACITY = c;
          }
        } catch (Exception ignored) {
        }
      }
      int purged = 0;
      java.util.Iterator<Map.Entry<String, Info>> cit = CACHE.entrySet().iterator();
      while (cit.hasNext()) {
        Map.Entry<String, Info> ce = cit.next();
        boolean hasWorld = false;
        boolean hasPosition = false;
        try {
          for (Component line : ce.getValue().lore()) {
            String s = line.getString();
            if (s.contains("World:")) hasWorld = true;
            if (s.contains("Position:")) hasPosition = true;
          }
        } catch (Throwable ignored) {
        }
        if (!hasWorld || !hasPosition) {
          cit.remove();
          String k = ce.getKey();
          NAMES.removeIf(w -> key(w).equals(k));
          LAYOUT.removeIf(e -> e.equals("home:" + k));
          purged++;
        }
      }
      if (purged > 0) {
        org.slf4j.LoggerFactory.getLogger("openorigins")
            .info("[homes-lore] purged {} visit entries from disk", purged);
        save();
      }
    } catch (Exception ex) {
      org.slf4j.LoggerFactory.getLogger("openorigins")
          .warn("[homes-lore] load failed: {}", ex.toString());
    }
  }
}
