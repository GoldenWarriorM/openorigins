package com.openorigins.client.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pacing rules that keep silent fetches out of the server's chat limiter and
 * out of the player's face. Both are pure functions on purpose.
 */
class BackgroundMenuPacingTest {
  @Test
  void plainCommandsKeepTheFetchInFlight() {
    // Teleporting home right after a quest is exactly the reported case: the
    // pending /streaks answer must stay ours instead of popping as a chest.
    assertTrue(BackgroundMenu.isNonMenuCommand("home base"));
    assertTrue(BackgroundMenu.isNonMenuCommand("home"));
    assertTrue(BackgroundMenu.isNonMenuCommand("home my base".replace("my base", "my_base")));
    assertTrue(BackgroundMenu.isNonMenuCommand("delhome base"));
    assertTrue(BackgroundMenu.isNonMenuCommand("sethome base"));
    assertTrue(BackgroundMenu.isNonMenuCommand("tpa Steve"));
    assertTrue(BackgroundMenu.isNonMenuCommand("tpaccept"));
    assertTrue(BackgroundMenu.isNonMenuCommand("spawn"));
    assertTrue(BackgroundMenu.isNonMenuCommand("realm tp"));
    assertTrue(BackgroundMenu.isNonMenuCommand("balloon blue-balloon"));
  }

  @Test
  void menuCommandsEndTheFetch() {
    assertFalse(BackgroundMenu.isNonMenuCommand("homes"));
    assertFalse(BackgroundMenu.isNonMenuCommand("streaks"));
    assertFalse(BackgroundMenu.isNonMenuCommand("navigator"));
    assertFalse(BackgroundMenu.isNonMenuCommand("ah"));
    assertFalse(BackgroundMenu.isNonMenuCommand("profile"));
    assertFalse(BackgroundMenu.isNonMenuCommand(""));
    assertFalse(BackgroundMenu.isNonMenuCommand(null));
  }

  @Test
  void resendGapIsAtLeastFiveSeconds() {
    assertEquals(5000L, BackgroundMenu.resendGapFor(0));
    assertEquals(5000L, BackgroundMenu.resendGapFor(-1));
    assertEquals(5000L, BackgroundMenu.resendGapFor(100));
    assertEquals(5000L, BackgroundMenu.resendGapFor(1000));
  }

  @Test
  void resendGapFollowsThePing() {
    // A 3s retry was shorter than the round-trip on a bad connection, so both
    // answers came back and the duplicate was shown as a server menu.
    assertEquals(6500L, BackgroundMenu.resendGapFor(2000));
    assertEquals(12500L, BackgroundMenu.resendGapFor(5000));
    assertEquals(20000L, BackgroundMenu.resendGapFor(60000));
  }
}
