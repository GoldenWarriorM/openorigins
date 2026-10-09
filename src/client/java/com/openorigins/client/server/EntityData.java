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
    PENDING_TAG_OPS.remove(entityId);
  }

  public static void clearAll() {
    TAGS.clear();
    BACK_ITEMS.clear();
    PENDING_TAG_OPS.clear();
  }

  /**
   * Tag ops that arrived before the entity was tracked client-side (spawn
   * order). Queued instead of dropped; flushed once the entity appears.
   */
  private static final ConcurrentMap<Integer, List<Runnable>> PENDING_TAG_OPS =
      new ConcurrentHashMap<>();

  public static boolean hasPendingTagOps(int entityId) {
    return PENDING_TAG_OPS.containsKey(entityId);
  }

  public static void queueTagOp(int entityId, Runnable op) {    List<Runnable> ops = PENDING_TAG_OPS.computeIfAbsent(entityId,
        id -> Collections.synchronizedList(new ArrayList<>()));
    synchronized (ops) {
      if (ops.size() >= 32) {
        ops.remove(0);
      }
      ops.add(op);
    }
    if (PENDING_TAG_OPS.size() > 512) {
      PENDING_TAG_OPS.clear();
    }
  }

  /** Applies queued tag ops for entities that are tracked now. Call on tick. */
  public static void flushPendingTags() {
    if (PENDING_TAG_OPS.isEmpty()) {
      return;
    }
    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
    if (mc == null || mc.level == null) {
      return;
    }
    for (Integer id : new ArrayList<>(PENDING_TAG_OPS.keySet())) {
      if (mc.level.getEntity(id) == null) {
        continue;
      }
      List<Runnable> ops = PENDING_TAG_OPS.remove(id);
      if (ops == null) {
        continue;
      }
      synchronized (ops) {
        for (Runnable op : ops) {
          try {
            op.run();
          } catch (Throwable ignored) {
          }
        }
      }
    }
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
