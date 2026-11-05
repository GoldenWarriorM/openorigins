package com.openorigins.client.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A second click on the same teleport must not fire a second teleport. */
class CommandDebounceTest {
  private long now;

  @BeforeEach
  void setUp() {
    now = 1_000L;
    CommandDebounce.clearForTest();
    CommandDebounce.setClockForTest(() -> now);
  }

  @AfterEach
  void tearDown() {
    CommandDebounce.setClockForTest(null);
    CommandDebounce.clearForTest();
  }

  @Test
  void firstPressGoesThrough() {
    assertTrue(CommandDebounce.accept("home base"));
  }

  @Test
  void repeatedPressesAreDropped() {
    assertTrue(CommandDebounce.accept("home base"));
    now += 200;
    assertFalse(CommandDebounce.accept("home base"));
    now += 1_000;
    assertFalse(CommandDebounce.accept("home base"));
  }

  @Test
  void differentDestinationsAreIndependent() {
    assertTrue(CommandDebounce.accept("home base"));
    assertTrue(CommandDebounce.accept("home mine"));
    assertTrue(CommandDebounce.accept("spawn"));
  }

  @Test
  void caseAndSpacingDoNotSneakThrough() {
    assertTrue(CommandDebounce.accept("Home Base"));
    assertFalse(CommandDebounce.accept("  home base  "));
  }

  @Test
  void theWindowExpires() {
    assertTrue(CommandDebounce.accept("home base"));
    now += 5_001;
    assertTrue(CommandDebounce.accept("home base"));
  }
}
