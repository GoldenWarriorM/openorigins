package com.openorigins.client.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Server directory; only locations are kept for the tracker. */
public final class RemoteRegistry {
  private static final Logger LOGGER = LoggerFactory.getLogger("openorigins");
  private static final String REMOTE_URL =
      "https://raw.githubusercontent.com/OriginsEnhanced/assets/master/originsenhanced/assets/data.json";

  public record LocationData(boolean chatroom, String icon) {}

  private volatile Map<String, LocationData> locations = Map.of();
  private volatile boolean loaded;

  private static final RemoteRegistry INSTANCE = new RemoteRegistry();

  private RemoteRegistry() {}

  public static RemoteRegistry get() {
    return INSTANCE;
  }

  public CompletableFuture<Void> fetchAsync() {
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    HttpRequest request = HttpRequest.newBuilder(URI.create(REMOTE_URL))
        .timeout(Duration.ofSeconds(15)).GET().build();
    return client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
      if (response.statusCode() == 200) {
        parse(response.body());
      }
    }).exceptionally(t -> {
      com.openorigins.client.util.DebugLog.warn("[OpenOrigins] remote registry fetch failed, using empty registry");
      return null;
    });
  }

  private void parse(String json) {
    try {
      Map<String, LocationData> parsed = new HashMap<>();
      JsonObject root = JsonParser.parseString(json).getAsJsonObject();
      JsonObject locs = root.getAsJsonObject("locations");
      if (locs != null) {
        for (Map.Entry<String, JsonElement> entry : locs.entrySet()) {
          boolean chatroom = false;
          String icon = null;
          if (entry.getValue().isJsonObject()) {
            JsonObject data = entry.getValue().getAsJsonObject();
            chatroom = data.has("chatroom") && data.get("chatroom").getAsBoolean();
            if (data.has("presenceIcon")) {
              icon = data.get("presenceIcon").getAsString();
            }
          }
          parsed.put(entry.getKey(), new LocationData(chatroom, icon));
        }
      }
      this.locations = Collections.unmodifiableMap(parsed);
      this.loaded = true;
    } catch (Throwable t) {
      com.openorigins.client.util.DebugLog.warn("[OpenOrigins] remote registry parse failed");
    }
  }

  public LocationData getLocation(String name) {
    return this.locations.get(name);
  }

  public boolean isLoaded() {
    return this.loaded;
  }
}
