package com.openorigins.client.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Server-provided entity data. Thread-safe: written on the net thread, read on render.
 */
public final class EntityData {
  public record NameTag(List<Component> lines, float offset) {
    public NameTag {
      lines = Collections.synchronizedList(new ArrayList<>(lines));
    }
  }

  private static final ConcurrentMap<Integer, NameTag> TAGS = new ConcurrentHashMap<>();
  private static final ConcurrentMap<Integer, ItemStack> BACK_ITEMS = new ConcurrentHashMap<>();

  private EntityData() {}

  public static NameTag getTag(int entityId) {
    return TAGS.get(entityId);
  }

  private static NameTag getOrCreate(int entityId) {
    return TAGS.computeIfAbsent(entityId, id -> new NameTag(new ArrayList<>(), 0.0F));
  }

  public static void addTagLine(int entityId, Component line) {
    getOrCreate(entityId).lines().add(line);
  }

  public static void setTagLine(int entityId, int index, Component line) {
    NameTag tag = TAGS.get(entityId);
    if (tag != null && index >= 0 && index < tag.lines().size()) {
      tag.lines().set(index, line);
    }
  }

  public static void removeTagLine(int entityId, int index) {
    NameTag tag = TAGS.get(entityId);
    if (tag != null && index >= 0 && index < tag.lines().size()) {
      tag.lines().remove(index);
      if (tag.lines().isEmpty()) TAGS.remove(entityId);
    }
  }

  public static void setTagOffset(int entityId, float offset) {
    NameTag old = TAGS.get(entityId);
    List<Component> lines = old == null ? new ArrayList<>() : new ArrayList<>(old.lines());
    TAGS.put(entityId, new NameTag(lines, Math.max(0.0F, offset)));
  }

  public static void clearTag(int entityId) {
    TAGS.remove(entityId);
  }

  public static void clearAll() {
    TAGS.clear();
    BACK_ITEMS.clear();
  }

  public static ItemStack getBackItem(int entityId) {
    return BACK_ITEMS.get(entityId);
  }

  public static void setBackItem(int entityId, ItemStack stack) {
    if (stack == null) {
      BACK_ITEMS.remove(entityId);
    } else {
      BACK_ITEMS.put(entityId, stack);
    }
  }
}
