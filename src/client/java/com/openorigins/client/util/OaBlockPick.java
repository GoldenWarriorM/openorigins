package com.openorigins.client.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Middle-click picks custom Origin Realms blocks by matching the targeted
 * blockstate's custom model against inventory item ids. Mechanism mirrors
 * OriginAddons (MIT), reimplemented.
 */
public final class OaBlockPick implements SimpleSynchronousResourceReloadListener {
  private static final Identifier ID =
      Identifier.fromNamespaceAndPath("openorigins", "block_pick");
  private static final Pattern NUMBERED = Pattern.compile("^(.*)_\\d+$");
  private static final Map<String, String> NAME_CACHE = new HashMap<>();

  private static final Map<Block, Map<BlockState, String>> BLOCK_TYPES = new HashMap<>();

  private OaBlockPick() {}

  @Override
  public Identifier getFabricId() {
    return ID;
  }

  public static void register() {
    try {
      ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new OaBlockPick());
    } catch (Throwable ignored) {
    }
  }

  @Override
  public void onResourceManagerReload(ResourceManager manager) {
    BLOCK_TYPES.clear();
    NAME_CACHE.clear();
    Map<Identifier, Resource> resources =
        manager.listResources("blockstates", p -> p.getPath().endsWith(".json"));
    for (Map.Entry<Identifier, Resource> e : resources.entrySet()) {
      try (InputStream in = e.getValue().open();
          BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
        JsonObject json = JsonParser.parseReader(r).getAsJsonObject();
        Identifier rl = e.getKey();
        Identifier blockId = Identifier.fromNamespaceAndPath(rl.getNamespace(),
            rl.getPath().replace("blockstates/", "").replace(".json", ""));
        processBlockStateFile(json, blockId);
      } catch (Throwable t) {
        DebugLog.info("[blockpick] skip {}: {}", e.getKey(), t.toString());
      }
    }
    DebugLog.info("[blockpick] mapped {} blocks", BLOCK_TYPES.size());
  }

  public static String customBlockName(BlockState state) {
    Map<BlockState, String> states = BLOCK_TYPES.get(state.getBlock());
    if (states == null) return "";
    return states.getOrDefault(state, "");
  }

  public static String customItemName(net.minecraft.world.item.ItemStack stack) {
    Optional<String> id = CustomItemIds.resolve(stack);
    String customId;
    if (id.isPresent() && !id.get().isEmpty()) {
      customId = id.get();
    } else if (stack.getItem() != null) {
      customId = "minecraft:" + BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    } else {
      return "";
    }
    String[] parts = customId.split(":");
    return cleanModelName(parts[parts.length - 1]);
  }

  public static String cleanModelName(String modelName) {
    String[] split = modelName.split("/");
    String id = split[split.length - 1].replace(' ', '_').replace('-', '_');
    Matcher m = NUMBERED.matcher(id);
    if (m.find()) id = m.group(1);
    return id;
  }

  public static boolean namesMatch(String a, String b) {
    return clean(a).equalsIgnoreCase(clean(b));
  }

  private static String clean(String name) {
    String hit = NAME_CACHE.get(name);
    if (hit != null) return hit;
    hit = Arrays.stream(name.toLowerCase(Locale.ROOT).split("_"))
        .map(w -> w.endsWith("s") && w.length() > 1 ? w.substring(0, w.length() - 1) : w)
        .sorted().collect(Collectors.joining("_"));
    NAME_CACHE.put(name, hit);
    return hit;
  }

  private static void processBlockStateFile(JsonObject json, Identifier blockId) {
    Block block = BuiltInRegistries.BLOCK.getValue(blockId);
    if (block == null) return;
    if (json.has("variants")) {
      JsonObject variants = json.getAsJsonObject("variants");
      for (String key : variants.keySet()) {
        processVariant(block, variants, key);
      }
    }
    if (json.has("multipart")) {
      JsonArray multipart = json.getAsJsonArray("multipart");
      for (JsonElement part : multipart) {
        JsonObject obj = part.getAsJsonObject();
        if (!obj.has("apply") || !obj.has("when")) continue;
        Identifier model = modelId(obj.get("apply"));
        if (model == null || !isCustomModel(block, model)) continue;
        Map<Property<?>, String> props =
            propsFromPairs(block, pairsFromWhen(obj.getAsJsonObject("when")));
        processStates(block, props, cleanModelName(model.getPath()));
      }
    }
  }

  private static void processVariant(Block block, JsonObject variants, String key) {
    Identifier model = modelId(variants.get(key));
    if (model == null || !isCustomModel(block, model)) return;
    List<String[]> pairs = new ArrayList<>();
    for (String kv : key.split(",")) {
      String[] t = kv.split("=", 2);
      if (t.length == 2) pairs.add(t);
    }
    processStates(block, propsFromPairs(block, pairs), cleanModelName(model.getPath()));
  }

  private static List<String[]> pairsFromWhen(JsonObject when) {
    List<String[]> pairs = new ArrayList<>();
    for (String k : when.keySet()) {
      pairs.add(new String[]{k, when.get(k).getAsString()});
    }
    return pairs;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Map<Property<?>, String> propsFromPairs(Block block, List<String[]> pairs) {
    StateDefinition<Block, BlockState> def = block.getStateDefinition();
    Map<Property<?>, String> out = new HashMap<>();
    for (String[] kv : pairs) {
      Property<?> prop = null;
      for (Property<?> p : def.getProperties()) {
        if (p.getName().equals(kv[0])) {
          prop = p;
          break;
        }
      }
      if (prop == null) throw new RuntimeException("Unknown property: " + kv[0]);
      boolean ok = false;
      for (Object v : prop.getPossibleValues()) {
        if (((Property) prop).getName((Comparable) v).equalsIgnoreCase(kv[1])) {
          ok = true;
          break;
        }
      }
      if (!ok) throw new RuntimeException("Unknown value: " + kv[1] + " for " + kv[0]);
      out.put(prop, kv[1]);
    }
    return out;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void processStates(Block block, Map<Property<?>, String> props, String modelName) {
    for (BlockState state : block.getStateDefinition().getPossibleStates()) {
      boolean match = true;
      for (Map.Entry<Property<?>, String> e : props.entrySet()) {
        Comparable<?> v = state.getValue(e.getKey());
        String name = ((Property) e.getKey()).getName(v);
        if (!name.equalsIgnoreCase(e.getValue())) {
          match = false;
          break;
        }
      }
      if (!match) continue;
      BLOCK_TYPES.computeIfAbsent(block, b -> new HashMap<>()).put(state, modelName);
    }
  }

  private static Identifier modelId(JsonElement def) {
    if (def == null || def.isJsonNull()) return null;
    if (def.isJsonPrimitive()) return Identifier.tryParse(def.getAsString());
    if (def.isJsonArray()) {
      JsonArray arr = def.getAsJsonArray();
      return arr.isEmpty() ? null : modelId(arr.get(0));
    }
    return modelId(def.getAsJsonObject().get("model"));
  }

  private static boolean isCustomModel(Block block, Identifier model) {
    boolean custom = model.getPath().contains("custom/");
    boolean noteBlock = block == Blocks.NOTE_BLOCK;
    boolean vanillaOre =
        model.getPath().startsWith("block/") && model.getPath().endsWith("_ore");
    if (noteBlock) return custom || vanillaOre;
    return custom;
  }
}
