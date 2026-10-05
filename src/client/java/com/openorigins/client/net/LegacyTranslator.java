package com.openorigins.client.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decodes 1.21.11 item bytes into a 26.2 ItemStack.
 * Wire frame: [count][item-id][added][removed][(type-id, value)].
 * Numeric ids differ between versions, so item/component ids are mapped
 * via 1.21 order tables (reg121.json) to identifier keys (stable across versions).
 * Values are decoded with 26.2 codecs. Throws on failure; caller substitutes EMPTY.
 */
public final class LegacyTranslator {
  private static final Logger LOGGER = LoggerFactory.getLogger("openorigins");
  private static final Map<Integer, String> ITEMS_121 = new HashMap<>();
  private static final Map<Integer, String> COMPONENTS_121 = new HashMap<>();
  private static volatile boolean tablesLoaded;

  private LegacyTranslator() {}

  private static synchronized void ensureTables() {
    if (tablesLoaded) return;
    tablesLoaded = true;
    try (InputStream in = LegacyTranslator.class
        .getResourceAsStream("/assets/openorigins/tables/reg121.json")) {
      if (in == null) {
        com.openorigins.client.util.DebugLog.warn("[OpenOrigins] reg121 tables missing");
        return;
      }
      JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
          .getAsJsonObject();
      root.getAsJsonObject("items").entrySet()
          .forEach(e -> ITEMS_121.put(Integer.parseInt(e.getKey()), e.getValue().getAsString()));
      root.getAsJsonObject("components").entrySet()
          .forEach(e -> COMPONENTS_121.put(Integer.parseInt(e.getKey()), e.getValue().getAsString()));
    } catch (Throwable t) {
      com.openorigins.client.util.DebugLog.warn("[OpenOrigins] failed to load reg121 tables", t);
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
      int legacyType = buf.readVarInt();
      String typeKey = COMPONENTS_121.get(legacyType);
      if (typeKey == null) {
        throw new IllegalStateException("unknown 1.21 component id " + legacyType);
      }
      if (typeKey.equals("minecraft:fireworks")) {
        decodeInto(DataComponentPatch.builder(), BuiltInRegistries.DATA_COMPONENT_TYPE
            .getValue(Identifier.parse(typeKey)), buf);
        continue;
      }
      DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.parse(typeKey));
      decodeInto(patch, type, buf);
    }
    for (int i = 0; i < removed; i++) {
      int legacyType = buf.readVarInt();
      String typeKey = COMPONENTS_121.get(legacyType);
      if (typeKey != null) {
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.parse(typeKey));
        patch.remove(type);
      }
    }
    Holder<Item> holder = item.builtInRegistryHolder();
    return new ItemStack(holder, count, patch.build());
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void decodeInto(DataComponentPatch.Builder patch, DataComponentType<?> type,
      RegistryFriendlyByteBuf buf) {
    StreamCodec codec = type.streamCodec();
    Object value = codec.decode(buf);
    ((DataComponentPatch.Builder) patch).set((DataComponentType) type, value);
  }
}
