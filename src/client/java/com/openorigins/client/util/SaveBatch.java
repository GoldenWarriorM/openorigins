package com.openorigins.client.util;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Defers persistence writes across a capture pass. One open menu would
 * otherwise rewrite several cached JSON files once per captured slot:
 * captures mark their cache dirty here and the outermost end() flushes each
 * marked saver exactly once.
 */
public final class SaveBatch {
  private SaveBatch() {}

  private static final java.util.Set<Runnable> PENDING = ConcurrentHashMap.newKeySet();
  private static int depth;

  /** Opens a deferral window; nests freely. */
  public static void begin() {
    depth++;
  }

  /** Closes one window; the outermost end() flushes all marked savers once. */
  public static void end() {
    if (depth > 0) {
      depth--;
    }
    if (depth == 0 && !PENDING.isEmpty()) {
      for (Runnable r : PENDING) {
        PENDING.remove(r);
        try {
          r.run();
        } catch (Throwable ignored) {
        }
      }
    }
  }

  /** Runs the saver immediately when no batch is open, else marks it dirty. */
  public static void mark(Runnable saver) {
    if (depth > 0) {
      PENDING.add(saver);
    } else {
      try {
        saver.run();
      } catch (Throwable ignored) {
      }
    }
  }
}
