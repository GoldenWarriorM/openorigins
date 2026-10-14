package com.openorigins.client.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.openorigins.client.BadgerHud;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;

/**
 * Snapshots the live task HUD once and reuses it as the offline editor
 * template (icons fall back to text when unresolvable).
 */
public final class BadgerTemplate {
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static long lastSaveMs;

  private BadgerTemplate() {}

  private static Path path() {
    return FabricLoader.getInstance().getConfigDir().resolve("openorigins-badger-template.json");
  }

  public static synchronized void maybeSnapshot(List<BadgerHud.Row> rows) {
    if (rows == null || rows.isEmpty()) return;
    long now = System.currentTimeMillis();
    if (now - lastSaveMs < 10000) return;
    lastSaveMs = now;
    try {
      JsonArray arr = new JsonArray();
      for (BadgerHud.Row r : rows) {
        JsonObject o = new JsonObject();
        o.addProperty("text", r.text());
        o.addProperty("color", r.color());
        String item = "";
        try {
          if (r.icon() != null && !r.icon().isEmpty()) {
            item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(r.icon().getItem()).toString();
          }
        } catch (Throwable ignored) {
        }
        o.addProperty("item", item);
        arr.add(o);
      }
      JsonObject root = new JsonObject();
      root.add("rows", arr);
      Files.createDirectories(path().getParent());
      try (Writer w = Files.newBufferedWriter(path(), StandardCharsets.UTF_8)) {
        GSON.toJson(root, w);
      }
    } catch (IOException ignored) {
    }
  }

  public static synchronized List<BadgerHud.Row> loadTemplate() {
    List<BadgerHud.Row> out = new ArrayList<>();
    Path p = path();
    if (!Files.exists(p)) return out;
    try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
      JsonObject root = GSON.fromJson(r, JsonObject.class);
      if (root == null || !root.has("rows")) return out;
      for (JsonElement e : root.getAsJsonArray("rows")) {
        if (!e.isJsonObject()) continue;
        JsonObject o = e.getAsJsonObject();
        String text = o.has("text") ? o.get("text").getAsString() : "";
        int color = o.has("color") ? o.get("color").getAsInt() : 0xFFFFFFFF;
        ItemStack icon = ItemStack.EMPTY;
        if (o.has("item")) {
          String id = o.get("item").getAsString();
          if (id != null && !id.isEmpty()) {
            try {
              var key = net.minecraft.resources.Identifier.parse(id);
              var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(key);
              if (item != null) icon = new ItemStack(item);
            } catch (Throwable ignored) {
            }
          }
        }
        if (!text.isEmpty()) out.add(new BadgerHud.Row(icon, text, color));
      }
    } catch (IOException ignored) {
    }
    return out;
  }
}
