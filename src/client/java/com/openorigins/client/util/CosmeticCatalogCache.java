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
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Clean cosmetic names/descriptions (incl. rarity) memorized from the server
 * cosmetics catalog (items whose lore offers a preview). Never polluted by
 * equipped-helmet stats.
 */
public final class CosmeticCatalogCache {
  private CosmeticCatalogCache() {}

  private static final Logger LOG = LoggerFactory.getLogger("openorigins");
  private static final Map<String, List<Component>> CLEAN = new ConcurrentHashMap<>();

  public static String normName(String raw) {
    String s = raw == null ? "" : raw.strip();
    int i = 0;
    while (i < s.length() && (s.charAt(i) <= 0x20 || s.charAt(i) > 0x2000)) {
      i++;
    }
    return s.substring(i).strip();
  }

  private static String variantBase(String norm) {
    if (norm.endsWith(")") && norm.contains("(")) {
      return norm.substring(0, norm.lastIndexOf('(')).strip();
    }
    return norm;
  }

  /** Memorized clean tooltip lines, or null. */
  public static List<Component> cleanFor(ItemStack cosmetic) {    if (cosmetic == null || cosmetic.isEmpty()) {
      return null;
    }
    String key = normName(cosmetic.getHoverName().getString());
    List<Component> lines = CLEAN.get(key);
    if ((lines == null || lines.isEmpty())) {
      lines = CLEAN.get(variantBase(key));
    }
    return (lines == null || lines.isEmpty()) ? null : lines;
  }

  /** Stores a clean snapshot, e.g. captured at equip-click time. */
  public static void putClean(ItemStack stack) {
    if (stack == null || stack.isEmpty()) {
      return;
    }
    ItemLore lore = stack.get(DataComponents.LORE);
    if (lore == null || lore.lines().isEmpty()) {
      return;
    }
    String key = normName(stack.getHoverName().getString());
    if (key.isEmpty()) {
      return;
    }
    List<Component> full = new java.util.ArrayList<>(lore.lines());
    full.add(0, stack.getHoverName().copy());
    full = withoutActions(full);
    full = withoutActions(full);
    List<Component> prev = CLEAN.get(key);
    if (prev == null || !sameText(prev, full)) {
      CLEAN.put(key, List.copyOf(full));
      save();
      LOG.info("[cosmetics] captured '{}' ({} lines)", key, full.size());
    }
  }

  private static net.minecraft.client.Minecraft mc() {
    return net.minecraft.client.Minecraft.getInstance();
  }

  private static boolean profileArmed;
  private static long profileDeadline;
  private static java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> profileReturn =
      () -> null;
  private static final java.util.Set<String> PROFILE_TARGETS =
      java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
  private static volatile long lastProfileAttempt;

  /** Silently opens /profile to memorize worn cosmetics, then returns. */
  public static void fetchWorn(java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> ret) {
    Minecraft m = mc();
    if (m == null || m.player == null) {
      return;
    }
    PROFILE_TARGETS.clear();
    try {
      ItemStack head = m.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
      if (head != null && !head.isEmpty()) {
        PROFILE_TARGETS.add(normName(head.getHoverName().getString()));
      }
      ItemStack back = com.openorigins.client.server.EntityData.getBackItem(m.player.getId());
      if (back != null && !back.isEmpty()) {
        PROFILE_TARGETS.add(normName(back.getHoverName().getString()));
      }
    } catch (Throwable ignored) {
    }
    if (PROFILE_TARGETS.isEmpty()) {
      return;
    }
    profileReturn = ret;
    profileArmed = true;
    profileDeadline = System.currentTimeMillis() + 10000;
    lastProfileAttempt = System.currentTimeMillis();
    m.player.connection.sendCommand("profile");
  }

  public static boolean shouldFetchWorn() {
    return System.currentTimeMillis() - lastProfileAttempt > 5 * 60000L;
  }

  /** Called on container open/content; captures targets and returns when done. */
  public static void profilePoll(AbstractContainerScreen<?> screen) {
    if (!profileArmed) {
      return;
    }
    Minecraft m = mc();
    if (m == null) {
      profileArmed = false;
      return;
    }
    boolean done = System.currentTimeMillis() > profileDeadline;
    try {
      Object inv = m.player != null ? m.player.getInventory() : null;
      for (Slot slot : screen.getMenu().slots) {
        if (!slot.hasItem()) {
          continue;
        }
        if (inv != null && slot.container == inv) {
          continue;
        }
        ItemStack stack = slot.getItem();
        if (PROFILE_TARGETS.contains(normName(stack.getHoverName().getString()))) {
          putClean(stack);
          done = true;
        }
      }
    } catch (Throwable ignored) {
    }
    if (done) {
      profileArmed = false;
      m.execute(() -> {
        try {
          if (m.player != null) {
            m.player.closeContainer();
          }
          net.minecraft.client.gui.screens.Screen ret = profileReturn.get();
          if (ret != null) {
            m.gui.setScreen(ret);
          } else {
            m.gui.setScreen(null);
          }
        } catch (Throwable ignored) {
        }
      });
    }
  }

  /** Scans any open container for catalog entries. */
  public static void scan(AbstractContainerScreen<?> screen) {
    if (screen == null || screen.getMenu() == null) {
      return;
    }
    Minecraft mc = Minecraft.getInstance();
    Object playerInv = (mc != null && mc.player != null) ? mc.player.getInventory() : null;
    boolean changed = false;
    for (Slot slot : screen.getMenu().slots) {
      if (!slot.hasItem()) {
        continue;
      }
      if (playerInv != null && slot.container == playerInv) {
        continue;
      }
      ItemStack stack = slot.getItem();
      ItemLore lore = stack.get(DataComponents.LORE);
      if (lore == null || lore.lines().isEmpty()) {
        continue;
      }
      boolean catalog = false;
      for (Component line : lore.lines()) {
        String text = line.getString();
        if (text.contains("Left-click to")) {
          catalog = true;
          break;
        }
      }
      if (!catalog) {
        continue;
      }
      String key = normName(stack.getHoverName().getString());
      if (key.isEmpty()) {
        continue;
      }
      List<Component> full = new java.util.ArrayList<>(lore.lines());
      full.add(0, stack.getHoverName().copy());
      full = withoutActions(full);
      List<Component> prev = CLEAN.get(key);
      if (prev == null) {
        CLEAN.put(key, List.copyOf(full));
        changed = true;
      } else if (!sameText(prev, full) && full.size() <= prev.size()) {
        CLEAN.put(key, List.copyOf(full));
        changed = true;
      }
    }
    if (changed) {
      save();
    }
  }

  private static boolean isActionLine(String s) {
    return s.startsWith("Left-click") || s.startsWith("Right-click")
        || s.startsWith("Shift-click") || s.startsWith("Middle-click");
  }

  /** Drops menu-action lines, keeps the display content. */
  private static List<Component> withoutActions(List<Component> lines) {
    List<Component> out = new java.util.ArrayList<>(lines.size());
    for (int i = 0; i < lines.size(); i++) {
      if (i > 0 && isActionLine(lines.get(i).getString())) {
        continue;
      }
      out.add(lines.get(i));
    }
    return out;
  }

  private static boolean sameText(List<Component> a, List<Component> b) {
    if (a.size() != b.size()) {
      return false;
    }
    for (int i = 0; i < a.size(); i++) {
      if (!a.get(i).getString().equals(b.get(i).getString())) {
        return false;
      }
    }
    return true;
  }

  private static String encode(Component line) {
    ByteBuf buf = Unpooled.buffer();
    try {
      net.minecraft.network.chat.ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC
          .encode(buf, line);
      byte[] b = new byte[buf.readableBytes()];
      buf.readBytes(b);
      return Base64.getEncoder().encodeToString(b);
    } finally {
      buf.release();
    }
  }

  private static Component decode(String s) {
    byte[] b = Base64.getDecoder().decode(s);
    ByteBuf buf = Unpooled.wrappedBuffer(b);
    try {
      return net.minecraft.network.chat.ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC
          .decode(buf);
    } finally {
      buf.release();
    }
  }

  private static Path file() {
    return FabricLoader.getInstance().getConfigDir().resolve("openorigins_cosmetics.json");
  }

  public static synchronized void save() {
    try {
      JsonArray arr = new JsonArray();
      for (Map.Entry<String, List<Component>> e : CLEAN.entrySet()) {
        JsonObject o = new JsonObject();
        o.addProperty("key", e.getKey());
        JsonArray lore = new JsonArray();
        for (Component line : e.getValue()) {
          lore.add(encode(line));
        }
        o.add("lore", lore);
        arr.add(o);
      }
      JsonObject root = new JsonObject();
      root.add("cosmetics", arr);
      Files.writeString(file(), root.toString(), StandardCharsets.UTF_8);
    } catch (Exception ex) {
      LOG.warn("[cosmetics] save failed: {}", ex.toString());
    }
  }

  public static synchronized void load() {
    try {
      Path f = file();
      if (!Files.isRegularFile(f)) {
        return;
      }
      JsonObject root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
      JsonArray arr = root.getAsJsonArray("cosmetics");
      if (arr == null) {
        return;
      }
      int n = 0;
      for (JsonElement el : arr) {
        JsonObject o = el.getAsJsonObject();
        List<Component> lore = new java.util.ArrayList<>();
        for (JsonElement le : o.getAsJsonArray("lore")) {
          lore.add(decode(le.getAsString()));
        }
        if (!lore.isEmpty()) {
          CLEAN.put(o.get("key").getAsString(), List.copyOf(lore));
          n++;
        }
      }
      LOG.info("[cosmetics] loaded {} entries from disk", n);
    } catch (Exception ex) {
      LOG.warn("[cosmetics] load failed: {}", ex.toString());
    }
  }
}
