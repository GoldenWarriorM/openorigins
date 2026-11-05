package com.openorigins.client.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Which user commands may keep a silent fetch in flight, and which one ends
 * it because its own answer must stay visible.
 */
class BackgroundMenuPacingTest {
  @Test
  void plainCommandsKeepTheFetchInFlight() {
    // Teleporting home right after a quest is exactly the reported case: the
    // pending /streaks answer must stay ours instead of popping as a chest.
    assertTrue(BackgroundMenu.isNonMenuCommand("home base"));
    assertTrue(BackgroundMenu.isNonMenuCommand("home"));
    assertTrue(BackgroundMenu.isNonMenuCommand("home my_base"));
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
}
