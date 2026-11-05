package com.openorigins.client.util;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drops a repeated teleport command. A teleport takes a moment to land and
 * gives no feedback, so the player presses the same button again; every press
 * used to fire another teleport (2026-11-05).
 */
public final class CommandDebounce {
  private CommandDebounce() {}

  /** Anything inside this window counts as the same intent. */
  private static final long WINDOW_MS = 5000L;

  /** Bounded: only a handful of teleport commands exist per menu. */
  private static final int MAX_KEYS = 64;

  private static final Map<String, Long> LAST = new ConcurrentHashMap<>();

  private static volatile java.util.function.LongSupplier clock = System::currentTimeMillis;

  static void setClockForTest(java.util.function.LongSupplier source) {
    clock = source == null ? System::currentTimeMillis : source;
  }

  static void clearForTest() {
    LAST.clear();
  }

  /** @return true when the command may go out, false while the same one is still fresh. */
  public static boolean accept(String command) {
    if (command == null || command.isBlank()) {
      return true;
    }
    String key = command.strip().toLowerCase(Locale.ROOT);
    long now = clock.getAsLong();
    if (LAST.size() > MAX_KEYS) {
      LAST.entrySet().removeIf(e -> now - e.getValue() >= WINDOW_MS);
    }
    boolean[] accepted = {false};
    LAST.compute(key, (k, prev) -> {
      if (prev != null && now - prev < WINDOW_MS) {
        return prev;
      }
      accepted[0] = true;
      return now;
    });
    if (!accepted[0]) {
      DebugLog.info("[debounce] ignored repeat /{}", key);
    }
    return accepted[0];
  }
}
