package com.openorigins.client.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class HomeParseTest {
  @Test
  void parsesCapacityFromRankLoreVariants() {
    assertEquals(5, HomeParse.parseCapacity("5 home slots"));
    assertEquals(5, HomeParse.parseCapacity("Home slots: 5"));
    assertEquals(3, HomeParse.parseCapacity("Unlock 3 home slots"));
    assertEquals(2, HomeParse.parseCapacity("2x Home Slots"));
    assertEquals(12, HomeParse.parseCapacity("Home slot: 12"));
    assertEquals(15, HomeParse.parseCapacity("15 HOUSE SLOTS"));
  }

  @Test
  void rejectsCapacityOutsideTheSaneRange() {
    assertEquals(0, HomeParse.parseCapacity("0 home slots"));
    assertEquals(0, HomeParse.parseCapacity("101 home slots"));
    assertEquals(0, HomeParse.parseCapacity("Home slots: many"));
  }

  @Test
  void ignoresLinesThatAreNotAboutHomes() {
    assertEquals(0, HomeParse.parseCapacity("Speed boost: 2"));
    assertEquals(0, HomeParse.parseCapacity(""));
    assertEquals(0, HomeParse.parseCapacity(null));
  }

  @Test
  void keyNormalisesSpacesUnderscoresAndCase() {
    assertEquals("myhome", HomeParse.key(" My Home "));
    assertEquals("myhome", HomeParse.key("my_home"));
    assertEquals("myhome", HomeParse.key("MY\nHOME"));
    assertEquals("дом", HomeParse.key("ДОМ"));
    assertEquals("", HomeParse.key(null));
    assertEquals("", HomeParse.key("   _ "));
  }

  @Test
  void worldColorIsReadFromTheWorldLine() {
    assertEquals("blue", HomeParse.worldColor(List.of("Created: May 1", "World: Blue - PvP")));
    assertEquals("red", HomeParse.worldColor(List.of("World: Red")));
    assertEquals("purple", HomeParse.worldColor(List.of("world: PURPLE")));
  }

  @Test
  void worldColorIsUnknownWithoutAKnownColour() {
    assertNull(HomeParse.worldColor(List.of("Created: May 1")));
    assertNull(HomeParse.worldColor(List.of("World: Unknown")));
    assertNull(HomeParse.worldColor(List.of()));
  }
}
