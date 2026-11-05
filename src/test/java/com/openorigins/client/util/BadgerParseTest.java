package com.openorigins.client.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class BadgerParseTest {
  private static final long MIN = 60_000L;
  private static final long HOUR = 60 * MIN;
  private static final long DAY = 24 * HOUR;

  @Test
  void parsesWordTimersFromServerLore() {
    assertEquals(23 * HOUR + 59 * MIN, BadgerParse.parseTimeLeftMs("23 hours and 59 minutes"));
    assertEquals(6 * HOUR + 55 * MIN, BadgerParse.parseTimeLeftMs("6h 55m"));
  }

  @Test
  void parsesDaysSecondsAndCombinedUnits() {
    assertEquals(2 * DAY + 3 * HOUR, BadgerParse.parseTimeLeftMs("2 days 3 hours"));
    assertEquals(45_000L, BadgerParse.parseTimeLeftMs("45s"));
    assertEquals(DAY + 2 * HOUR + 3 * MIN + 4_000L,
        BadgerParse.parseTimeLeftMs("1d 2h 3m 4s"));
  }

  @Test
  void returnsZeroForMissingOrUnparseableTimers() {
    assertEquals(0L, BadgerParse.parseTimeLeftMs(null));
    assertEquals(0L, BadgerParse.parseTimeLeftMs(""));
    assertEquals(0L, BadgerParse.parseTimeLeftMs("expired"));
  }

  @Test
  void picksTheSmallestTimeLeftCandidate() {
    assertEquals("30m", BadgerParse.minAvail(
        List.of(2 * HOUR, 30 * MIN, 5 * HOUR), List.of("2h", "30m", "5h")));
    assertNull(BadgerParse.minAvail(List.of(), List.of()));
    assertNull(BadgerParse.minAvail(List.of(1L), List.of()));
  }

  @Test
  void picksTheSmallestNextAvailableValue() {
    assertEquals("45m", BadgerParse.minRaw(List.of("2h", "45m", "1d")));
    assertNull(BadgerParse.minRaw(List.of()));
  }

  @Test
  void timeValueReadsTheSuffixAfterThePrefix() {
    assertEquals("3h", BadgerParse.timeValue("next available: 3h", "next available:"));
    assertEquals("", BadgerParse.timeValue("no timer", "next available:"));
  }

  @Test
  void menuCaptionsAreJunkButRealItemsAreNot() {
    for (String junk : List.of("Your Listings", "Refresh", "Shrink View", "← Back",
        "Back", "Events", "Your Profile", "Badges", "Realms", "Social", "Close", "Learn More")) {
      assertTrue(BadgerParse.isMenuJunk(junk), junk);
    }
    assertTrue(BadgerParse.isMenuJunk(null));
    assertTrue(BadgerParse.isMenuJunk("  "));
    assertFalse(BadgerParse.isMenuJunk("Oak Log"));
    assertFalse(BadgerParse.isMenuJunk("Badger Token"));
  }

  @Test
  void parsesStreakCountersCaseInsensitively() {
    assertEquals(42, BadgerParse.parseScore("Current Streak Score: 42"));
    assertEquals(1337, BadgerParse.parseHighscore("STREAK HIGHSCORE: 1337"));
    assertEquals(6, BadgerParse.parseDaysUntilReset("Days until streak resets: 6"));
    assertNull(BadgerParse.parseScore("nothing here"));
    assertNull(BadgerParse.parseHighscore(null));
    assertNull(BadgerParse.parseDaysUntilReset("streak score: 1"));
  }

  @Test
  void parsesQuestTierLines() {
    BadgerParse.Tier tier = BadgerParse.parseTier("Basic: (2/5)");
    assertEquals("Basic", tier.name());
    assertEquals(2, tier.done());
    assertEquals(5, tier.total());

    BadgerParse.Tier advanced = BadgerParse.parseTier("Advanced Badger: (0/12)");
    assertEquals("Advanced Badger", advanced.name());
    assertEquals(0, advanced.done());
    assertEquals(12, advanced.total());
  }

  @Test
  void rejectsTierLinesWithoutProgress() {
    assertNull(BadgerParse.parseTier("Basic"));
    assertNull(BadgerParse.parseTier(null));
    assertNull(BadgerParse.parseTier("Basic: (a/b)"));
  }
}
