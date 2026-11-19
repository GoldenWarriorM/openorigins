package com.openorigins.client.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import io.netty.buffer.Unpooled;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Decodes the server's item bytes into a local ItemStack.
 * Wire frame: [count][item-id][added][removed][(type-id, value)].
 * The server numbers items in 1.21 order and components in the order of its own
 * build, which shifts with every release (26.2 puts firework_explosion at 68,
 * 26.3 at 70). The frame is therefore decoded with each numbering we know and
 * accepted only when the whole frame is consumed. Throws on failure; the caller
 * substitutes EMPTY.
 */
public final class LegacyTranslator {
  private static final Map<Integer, String> ITEMS_121 = new HashMap<>();
  private static final Map<Integer, String> COMPONENTS_262 = new HashMap<>();
  private static final Map<Integer, String> COMPONENTS_263 = new HashMap<>();
  private static final List<String> NUMBERING_NAMES = List.of("26.2", "26.3");
  private static final java.util.concurrent.atomic.AtomicBoolean loggedNumbering =
      new java.util.concurrent.atomic.AtomicBoolean();
  private static volatile boolean tablesLoaded;

  private LegacyTranslator() {}

  private static synchronized void ensureTables() {
    if (tablesLoaded) return;
    tablesLoaded = true;
    loadIds("reg121.json", "items", ITEMS_121);
    loadIds("components.json", null, COMPONENTS_262);
    loadIds("components263.json", null, COMPONENTS_263);
  }

  private static void loadIds(String file, String section, Map<Integer, String> into) {
    try (InputStream in = LegacyTranslator.class
        .getResourceAsStream("/assets/openorigins/tables/" + file)) {
      if (in == null) {
        com.openorigins.client.util.DebugLog.warn("[OpenOrigins] table " + file + " missing");
        return;
      }
      JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
          .getAsJsonObject();
      if (section != null) {
        root = root.getAsJsonObject(section);
      }
      root.entrySet()
          .forEach(e -> into.put(Integer.parseInt(e.getKey()), e.getValue().getAsString()));
    } catch (Throwable t) {
      com.openorigins.client.util.DebugLog.warn("[OpenOrigins] failed to load " + file, t);
    }
  }

  public static ItemStack decodeItem(RegistryFriendlyByteBuf buf) {
    ensureTables();
    List<Map<Integer, String>> numberings = List.of(COMPONENTS_262, COMPONENTS_263);
    byte[] raw = new byte[buf.readableBytes()];
    buf.readBytes(raw);
    RuntimeException last = null;
    for (int i = 0; i < numberings.size(); i++) {
      try {
        ItemStack stack = decodeRaw(raw, numberings.get(i), buf.registryAccess());        if (loggedNumbering.compareAndSet(false, true)) {
          com.openorigins.client.util.DebugLog.info(
              "[OpenOrigins] backitem decoded with {} component numbering",
              NUMBERING_NAMES.get(i));
        }
        return stack;
      } catch (RuntimeException e) {
        last = e;
      }
    }
    throw new IllegalStateException("no component numbering matches this packet", last);
  }

  private static ItemStack decodeRaw(byte[] raw, Map<Integer, String> numbering,
      RegistryAccess registries) {
    RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(raw), registries);
    int count = buf.readVarInt();
    if (count <= 0) {
      return ItemStack.EMPTY;
    }
    int legacyId = buf.readVarInt();
    String itemKey = ITEMS_121.get(legacyId);
    if (itemKey == null) {
      throw new IllegalStateException("unknown 1.21 item id " + legacyId);
    }
    Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemKey));
    int added = buf.readVarInt();
    int removed = buf.readVarInt();
    DataComponentPatch.Builder patch = DataComponentPatch.builder();
    for (int i = 0; i < added; i++) {
      DataComponentType<?> type = componentType(buf.readVarInt(), numbering);
      if (type == DataComponents.FIREWORKS) {
        decodeInto(DataComponentPatch.builder(), type, buf);
        continue;
      }
      decodeInto(patch, type, buf);
    }
    for (int i = 0; i < removed; i++) {
      patch.remove(componentType(buf.readVarInt(), numbering));
    }
    if (buf.readableBytes() != 0) {
      throw new IllegalStateException(buf.readableBytes() + " bytes left after decoding");
    }
    Holder<Item> holder = item.builtInRegistryHolder();
    return withPolymerStack(new ItemStack(holder, count, patch.build()), registries);
  }

  /**
   * The server shows vanilla clients a stand-in item and puts the real one, with
   * the model the pack draws, into custom data.
   */
  private static ItemStack withPolymerStack(ItemStack outer, RegistryAccess registries) {
    CustomData data = outer.get(DataComponents.CUSTOM_DATA);
    if (data == null) {
      return outer;
    }
    CompoundTag polymer = data.copyTag().getCompoundOrEmpty("$polymer:stack");
    String id = polymer.getStringOr("id", "");
    if (id.isEmpty()) {
      return outer;
    }
    CompoundTag stackTag = new CompoundTag();
    stackTag.putString("id", id);
    stackTag.putInt("count", 1);
    stackTag.put("components", polymer.getCompoundOrEmpty("components"));
    return ItemStack.CODEC
        .parse(RegistryOps.create(NbtOps.INSTANCE, registries), stackTag)
        .result()
        .orElse(outer);
  }

  static DataComponentType<?> componentType(int id) {
    ensureTables();
    return componentType(id, COMPONENTS_262);
  }

  private static DataComponentType<?> componentType(int id, Map<Integer, String> numbering) {
    String key = numbering.get(id);
    if (key == null) {
      throw new IllegalStateException("unknown component id " + id);
    }
    Identifier name = Identifier.parse(key);
    if (!BuiltInRegistries.DATA_COMPONENT_TYPE.containsKey(name)) {
      throw new IllegalStateException("component " + key + " is missing in this client");
    }
    return BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(name);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void decodeInto(DataComponentPatch.Builder patch, DataComponentType<?> type,
      RegistryFriendlyByteBuf buf) {
    StreamCodec codec = type.streamCodec();
    Object value = codec.decode(buf);
    ((DataComponentPatch.Builder) patch).set((DataComponentType) type, value);
  }
}
