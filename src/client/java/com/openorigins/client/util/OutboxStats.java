package com.openorigins.client.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Counts every request our mod sends to the server (commands, menu clicks,
 * window closes, suggestions, handshake) and matches them with server
 * answers. Powers the debug outbox HUD: pending (orange), timed out with
 * no answer (red), answered (green + round-trip).
 */
public final class OutboxStats {
  private OutboxStats() {}

  public record Entry(long ms, String label, long timeoutMs, long ackMs) {
    public boolean acked() {
      return ackMs >= 0;
    }
  }

  private static final ArrayDeque<Entry> all = new ArrayDeque<>();
  private static int session;
  private static long lastMs;

  /** Wall clock, replaceable in tests to keep rate windows deterministic. */
  private static volatile java.util.function.LongSupplier clock = System::currentTimeMillis;

  static void setClockForTest(java.util.function.LongSupplier source) {
    clock = source == null ? System::currentTimeMillis : source;
  }

  private static long now() {
    return clock.getAsLong();
  }

  public static synchronized void record(String label) {
    record(label, 10000L);
  }

  public static synchronized void record(String label, long timeoutMs) {
    long now = now();
    prune(now);
    all.addLast(new Entry(now, label, timeoutMs, -1));
    session++;
    lastMs = now;
  }

  /** Marks the newest pending entry with the exact label as answered. */
  public static synchronized void ackExact(String label) {
    ack(l -> l.equals(label));
  }

  /** Marks the newest pending entry matching the predicate as answered. */
  public static synchronized void ack(Predicate<String> match) {
    long now = now();
    ArrayDeque<Entry> rebuilt = null;
    var it = all.descendingIterator();
    while (it.hasNext()) {
      Entry e = it.next();
      if (!e.acked() && match.test(e.label())) {
        rebuilt = new ArrayDeque<>(all.size());
        for (Entry x : all) {
          rebuilt.addLast(x == e ? new Entry(x.ms(), x.label(), x.timeoutMs(), now) : x);
        }
        all.clear();
        all.addAll(rebuilt);
        lastMs = now;
        return;
      }
    }
  }

  private static void prune(long now) {
    while (!all.isEmpty() && now - all.peekFirst().ms() > 1800000L) {
      all.removeFirst();
    }
  }

  public static synchronized int lastMinute() {
    long now = now();
    prune(now);
    return countSince(now - 60000L);
  }

  public static synchronized int lastHalfHour() {
    long now = now();
    prune(now);
    return all.size();
  }

  public static synchronized int sessionTotal() {
    return session;
  }

  public static synchronized long lastMs() {
    return lastMs;
  }

  public static synchronized List<Entry> recent(int n) {
    List<Entry> out = new ArrayList<>();
    var it = all.descendingIterator();
    while (it.hasNext() && out.size() < n) {
      out.add(0, it.next());
    }
    return out;
  }

  private static int countSince(long cutoff) {
    int n = 0;
    var it = all.descendingIterator();
    while (it.hasNext()) {
      if (it.next().ms() < cutoff) {
        break;
      }
      n++;
    }
    return n;
  }

  public static synchronized void reset() {
    all.clear();
    session = 0;
    lastMs = 0;
  }

  public static void register() {
    net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT
        .register((handler, client) -> reset());
  }
}
