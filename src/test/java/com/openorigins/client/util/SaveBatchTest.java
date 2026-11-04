package com.openorigins.client.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SaveBatchTest {
  @BeforeEach
  void setUp() {
    SaveBatch.resetForTest();
  }

  @AfterEach
  void tearDown() {
    SaveBatch.resetForTest();
  }

  @Test
  void markOutsideBatchRunsImmediately() {
    AtomicInteger runs = new AtomicInteger();

    SaveBatch.mark(runs::incrementAndGet);

    assertEquals(1, runs.get());
  }

  @Test
  void markInsideBatchWaitsForOutermostEnd() {
    AtomicInteger runs = new AtomicInteger();

    SaveBatch.begin();
    SaveBatch.mark(runs::incrementAndGet);
    SaveBatch.end();
    assertEquals(1, runs.get());
  }

  @Test
  void nestedBatchesFlushOnlyAtOutermostEnd() {
    AtomicInteger runs = new AtomicInteger();

    SaveBatch.begin();
    SaveBatch.mark(runs::incrementAndGet);
    SaveBatch.begin();
    SaveBatch.mark(runs::incrementAndGet);
    SaveBatch.end();
    assertEquals(0, runs.get());
    SaveBatch.end();
    assertEquals(2, runs.get());
  }

  @Test
  void eachSaverRunsOncePerBatch() {
    AtomicInteger runs = new AtomicInteger();
    Runnable saver = runs::incrementAndGet;

    SaveBatch.begin();
    SaveBatch.mark(saver);
    SaveBatch.mark(saver);
    SaveBatch.mark(saver);
    SaveBatch.end();

    assertEquals(1, runs.get());
  }

  @Test
  void nextBatchRunsTheSaverAgain() {
    AtomicInteger runs = new AtomicInteger();
    Runnable saver = runs::incrementAndGet;

    SaveBatch.begin();
    SaveBatch.mark(saver);
    SaveBatch.end();
    SaveBatch.begin();
    SaveBatch.mark(saver);
    SaveBatch.end();

    assertEquals(2, runs.get());
  }

  @Test
  void failingSaverDoesNotBlockTheOthers() {
    AtomicInteger runs = new AtomicInteger();

    SaveBatch.begin();
    SaveBatch.mark(() -> {
      throw new IllegalStateException("boom");
    });
    SaveBatch.mark(runs::incrementAndGet);
    SaveBatch.end();

    assertEquals(1, runs.get());
  }

  @Test
  void failingSaverOutsideBatchIsSwallowed() {
    org.junit.jupiter.api.Assertions.assertDoesNotThrow(
        () -> SaveBatch.mark(() -> {
          throw new IllegalStateException("boom");
        }));
  }

  @Test
  void strayEndDoesNotUnderflowDepth() {
    AtomicInteger runs = new AtomicInteger();

    SaveBatch.end();
    SaveBatch.end();
    SaveBatch.mark(runs::incrementAndGet);

    assertEquals(1, runs.get());
  }
}
