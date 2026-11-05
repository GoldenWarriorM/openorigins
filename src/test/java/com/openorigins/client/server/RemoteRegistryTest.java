package com.openorigins.client.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class RemoteRegistryTest {
  @Test
  void parsesKnownLocationsAndFlags() {
    String json = """
        {
          "locations": {
            "Spawn": {"chatroom": true, "presenceIcon": "spawn"},
            "Market": {"chatroom": false},
            "Wilds": {}
          }
        }
        """;

    Map<String, RemoteRegistry.LocationData> locs = RemoteRegistry.parseLocations(json);

    assertEquals(3, locs.size());
    assertEquals(new RemoteRegistry.LocationData(true, "spawn"), locs.get("Spawn"));
    assertEquals(new RemoteRegistry.LocationData(false, null), locs.get("Market"));
    assertEquals(new RemoteRegistry.LocationData(false, null), locs.get("Wilds"));
  }

  @Test
  void ignoresUnknownFieldsAndNonObjectEntries() {
    String json = """
        {"locations": {
          "Spawn": {"chatroom": true, "extra": 1},
          "Broken": "not an object",
          "Nulled": null
        }}
        """;

    Map<String, RemoteRegistry.LocationData> locs = RemoteRegistry.parseLocations(json);

    assertEquals(3, locs.size());
    assertEquals(new RemoteRegistry.LocationData(false, null), locs.get("Broken"));
    assertEquals(new RemoteRegistry.LocationData(false, null), locs.get("Nulled"));
  }

  @Test
  void missingLocationsYieldsAnEmptyMap() {
    Map<String, RemoteRegistry.LocationData> locs =
        RemoteRegistry.parseLocations("{\"version\": 2}");

    assertTrue(locs.isEmpty());
  }

  @Test
  void malformedPayloadYieldsNull() {
    assertNull(RemoteRegistry.parseLocations("not json"));
    assertNull(RemoteRegistry.parseLocations("[]"));
    assertNull(RemoteRegistry.parseLocations(""));
    assertNull(RemoteRegistry.parseLocations(null));
  }

  @Test
  void resultIsImmutable() {
    Map<String, RemoteRegistry.LocationData> locs = RemoteRegistry.parseLocations(
        "{\"locations\": {\"Spawn\": {}}}");

    assertThrows(UnsupportedOperationException.class,
        () -> locs.put("Other", new RemoteRegistry.LocationData(false, null)));
  }

  @Test
  void recordEqualityBacksTheTests() {
    RemoteRegistry.LocationData a = new RemoteRegistry.LocationData(true, "x");
    RemoteRegistry.LocationData b = new RemoteRegistry.LocationData(true, "x");

    assertEquals(a, b);
    assertFalse(a.equals(new RemoteRegistry.LocationData(false, "x")));
  }
}
