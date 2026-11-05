package com.openorigins.client.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OutboxStatsTest {
  private static final long T0 = 1_000_000L;

  private final AtomicLong clock = new AtomicLong(T0);

  @BeforeEach
  void setUp() {
    OutboxStats.reset();
    OutboxStats.setClockForTest(clock::get);
  }

  @AfterEach
  void tearDown() {
    OutboxStats.reset();
    OutboxStats.setClockForTest(null);
  }

  @Test
  void recordShowsUpAsPending() {
    OutboxStats.record("cmd /homes");

    List<OutboxStats.Entry> recent = OutboxStats.recent(5);

    assertEquals(1, recent.size());
    assertEquals("cmd /homes", recent.get(0).label());
    assertFalse(recent.get(0).acked());
    assertEquals(T0, recent.get(0).ms());
    assertEquals(10_000L, recent.get(0).timeoutMs());
    assertEquals(T0, OutboxStats.lastMs());
    assertEquals(1, OutboxStats.sessionTotal());
  }

  @Test
  void ackExactMarksTheNewestMatchingEntry() {
    OutboxStats.record("same");
    clock.addAndGet(500L);
    OutboxStats.record("same");
    clock.addAndGet(500L);
    OutboxStats.ackExact("same");

    List<OutboxStats.Entry> recent = OutboxStats.recent(2);

    assertFalse(recent.get(0).acked());
    assertTrue(recent.get(1).acked());
    assertEquals(clock.get(), recent.get(1).ackMs());
  }

  @Test
  void ackIgnoresAlreadyAnsweredAndUnknownLabels() {
    OutboxStats.record("a");
    outboxAck("a");
    long ackMs = OutboxStats.recent(1).get(0).ackMs();
    clock.addAndGet(100L);

    outboxAck("a");
    outboxAck("missing");

    assertEquals(ackMs, OutboxStats.recent(1).get(0).ackMs());
  }

  @Test
  void rateWindowsCountOnlyRecentEntries() {
    OutboxStats.record("a");
    clock.addAndGet(30_000L);
    OutboxStats.record("b");
    clock.addAndGet(31_000L);
    OutboxStats.record("c");

    assertEquals(2, OutboxStats.lastMinute());
    assertEquals(3, OutboxStats.lastHalfHour());
  }

  @Test
  void entriesOlderThanHalfAnHourArePruned() {
    OutboxStats.record("old");
    clock.addAndGet(1_800_001L);

    assertEquals(0, OutboxStats.lastHalfHour());
    assertTrue(OutboxStats.recent(5).isEmpty());
    assertEquals(1, OutboxStats.sessionTotal());
  }

  @Test
  void recentReturnsTheLastNInChronologicalOrder() {
    for (int i = 0; i < 5; i++) {
      OutboxStats.record("e" + i);
      clock.addAndGet(10L);
    }

    List<OutboxStats.Entry> recent = OutboxStats.recent(3);

    assertEquals(List.of("e2", "e3", "e4"), recent.stream().map(OutboxStats.Entry::label).toList());
  }

  @Test
  void timeoutMsIsPreservedPerEntry() {
    OutboxStats.record("handshake v5", 15_000L);

    assertEquals(15_000L, OutboxStats.recent(1).get(0).timeoutMs());
  }

  @Test
  void resetClearsEverything() {
    OutboxStats.record("a");
    OutboxStats.record("b");

    OutboxStats.reset();

    assertTrue(OutboxStats.recent(10).isEmpty());
    assertEquals(0, OutboxStats.sessionTotal());
    assertEquals(0L, OutboxStats.lastMs());
  }

  private static void outboxAck(String label) {
    OutboxStats.ackExact(label);
  }
}
