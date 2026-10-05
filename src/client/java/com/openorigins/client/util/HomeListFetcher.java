package com.openorigins.client.util;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;

/**
 * Lists homes by asking the server for tab-completions of {@code /home }.
 * Uses plain vanilla suggestion packets; no pack data is bundled.
 */
public final class HomeListFetcher {
  private HomeListFetcher() {}

  private static final AtomicInteger NEXT_ID = new AtomicInteger(0x4F000000);
  private static final Map<Integer, Consumer<List<String>>> PENDING = new ConcurrentHashMap<>();
  private static volatile long lastMutationMs;

  /** Call after our own delete/create commands: trust the next answers. */
  public static void noteMutation() {
    lastMutationMs = System.currentTimeMillis();
  }

  public static boolean trustEmpty() {
    return System.currentTimeMillis() - lastMutationMs < 15000;
  }

  public static void fetchHomes(Consumer<List<String>> callback) {
    fetchRaw("/home ", true, callback);
  }

  /** Generic tab-complete request. */
  public static void fetchRaw(String command, boolean dropSpaced, Consumer<List<String>> callback) {
    Minecraft mc = Minecraft.getInstance();
    if (mc == null || mc.player == null || mc.player.connection == null) {
      callback.accept(List.of());
      return;
    }
    int id = NEXT_ID.getAndIncrement();
    PENDING.put(id, callback);
    mc.player.connection.send(new ServerboundCommandSuggestionPacket(id, command));
  }

  /**
   * @return true if the packet was one of ours (caller should cancel it).
   */
  public static boolean handle(ClientboundCommandSuggestionsPacket packet) {
    Consumer<List<String>> callback = PENDING.remove(packet.id());
    if (callback == null) {
      return false;
    }
    List<String> names = packet.suggestions().stream()
        .map(ClientboundCommandSuggestionsPacket.Entry::text)
        .filter(s -> !s.isBlank() && !s.contains(" "))
        .sorted(String.CASE_INSENSITIVE_ORDER)
        .toList();
    com.openorigins.client.util.DebugLog.info("[homes] tab-complete: {}", names);
    Minecraft mc = Minecraft.getInstance();
    if (mc != null) {
      mc.execute(() -> callback.accept(names));
    }
    return true;
  }
}
