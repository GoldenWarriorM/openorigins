package com.openorigins.client.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decodes the server's item bytes into a local ItemStack.
 * Wire frame: [count][item-id][added][removed][(type-id, value)].
 * The server numbers items in 1.21 order and components in 26.2 order, while
 * this jar also runs on 26.3, where component ids shifted. Both are therefore
 * mapped through their tables to keys, which are stable across versions, and
 * values are decoded with the local codecs. Throws on failure; caller
 * substitutes EMPTY.
 */
public final class LegacyTranslator {
  private static final Logger LOGGER = LoggerFactory.getLogger("openorigins");
  private static final Map<Integer, String> ITEMS_121 = new HashMap<>();
  private static final Map<Integer, String> COMPONENTS_262 = new HashMap<>();
  private static volatile boolean tablesLoaded;

  private LegacyTranslator() {}

  private static synchronized void ensureTables() {
    if (tablesLoaded) return;
    tablesLoaded = true;
    loadIds("reg121.json", ITEMS_121);
    loadIds("components.json", COMPONENTS_262);
  }

  private static void loadIds(String file, Map<Integer, String> into) {
    try (InputStream in = LegacyTranslator.class
        .getResourceAsStream("/assets/openorigins/tables/" + file)) {
      if (in == null) {
        com.openorigins.client.util.DebugLog.warn("[OpenOrigins] table " + file + " missing");
        return;
      }
      JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
          .getAsJsonObject();
      if (file.equals("reg121.json")) {
        root = root.getAsJsonObject("items");
      }
      root.entrySet()
          .forEach(e -> into.put(Integer.parseInt(e.getKey()), e.getValue().getAsString()));
    } catch (Throwable t) {
      com.openorigins.client.util.DebugLog.warn("[OpenOrigins] failed to load " + file, t);
    }
  }

  public static ItemStack decodeItem(RegistryFriendlyByteBuf buf) {
    ensureTables();
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
      DataComponentType<?> type = componentType(buf.readVarInt());
      if (type == DataComponents.FIREWORKS) {
        decodeInto(DataComponentPatch.builder(), type, buf);
        continue;
      }
      decodeInto(patch, type, buf);
    }
    for (int i = 0; i < removed; i++) {
      patch.remove(componentType(buf.readVarInt()));
    }
    Holder<Item> holder = item.builtInRegistryHolder();
    return withPolymerStack(new ItemStack(holder, count, patch.build()), buf.registryAccess());
  }

  /**
   * The server shows vanilla clients a stand-in item (an enchanted book) and
   * puts the real one, with the model the pack draws, into custom data.
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
    String key = COMPONENTS_262.get(id);
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
