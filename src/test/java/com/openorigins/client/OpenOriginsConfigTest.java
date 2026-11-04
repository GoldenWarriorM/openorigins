package com.openorigins.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenOriginsConfigTest {
  private static final String DEFAULT_SERVERS =
      "play.originrealms.com,tcpshield.originrealms.com,tcpshield-iad.originrealms.com,"
          + "spectrum-tcpshield.originrealms.com,bhs.originrealms.com";

  @TempDir
  Path tmp;

  private Path file;
  private OpenOriginsConfig config;

  @BeforeEach
  void setUp() {
    file = tmp.resolve("openorigins.properties");
    OpenOriginsConfig.setConfigPathForTest(file);
    config = OpenOriginsConfig.getInstance();
    // The config is a singleton shared by the whole test JVM: pin a known state.
    for (OriginFeature feature : OriginFeature.values()) {
      config.setEnabled(feature, feature.getDefaultValue());
    }
    config.setHomeCapacity(5);
    config.setExtrapMs(150);
    config.setUseHandshake(true);
    config.setAllayArmsShiftTexels(12.0f);
    config.setBadgerHudCorner(OpenOriginsConfig.HudCorner.TOP_LEFT);
    config.setServersText(DEFAULT_SERVERS);
  }

  @AfterEach
  void tearDown() {
    OpenOriginsConfig.setConfigPathForTest(null);
  }

  @Test
  void missingFileIsCreatedWithDefaults() throws IOException {
    Files.deleteIfExists(file);

    config.load();

    assertTrue(Files.exists(file));
    assertTrue(config.isEnabled(OriginFeature.SMOOTH_ANIMATIONS));
    assertFalse(config.isEnabled(OriginFeature.ENTITY_EXTRAPOLATION));
    assertTrue(config.isEnabled(OriginFeature.CUSTOM_ITEM_IDS));
    assertEquals(Set.of(DEFAULT_SERVERS.split(",")), config.getServers());
  }

  @Test
  void settingsSurviveSaveAndReload() {
    config.setEnabled(OriginFeature.SMOOTH_ANIMATIONS, false);
    config.setEnabled(OriginFeature.ENTITY_EXTRAPOLATION, true);
    config.setHomeCapacity(9);
    config.setExtrapMs(500);
    config.setUseHandshake(false);
    config.setBadgerHudCorner(OpenOriginsConfig.HudCorner.BOTTOM_RIGHT);
    config.setServersText("Example.COM, test.example.com ,");

    config.load();

    assertFalse(config.isEnabled(OriginFeature.SMOOTH_ANIMATIONS));
    assertTrue(config.isEnabled(OriginFeature.ENTITY_EXTRAPOLATION));
    assertEquals(9, config.getHomeCapacity());
    assertEquals(500, config.getExtrapMs());
    assertFalse(config.isUseHandshake());
    assertEquals(OpenOriginsConfig.HudCorner.BOTTOM_RIGHT, config.getBadgerHudCorner());
    assertEquals(Set.of("example.com", "test.example.com"), config.getServers());
  }

  @Test
  void savedFileContainsFeatureKeys() throws IOException {
    config.setEnabled(OriginFeature.SMOOTH_ANIMATIONS, false);

    String text = Files.readString(file);

    assertTrue(text.contains("feature.smooth_animations=false"));
  }

  @Test
  void unknownAndMalformedKeysAreIgnored() throws IOException {
    Files.writeString(file, String.join("\n",
        "feature.smooth_animations=false",
        "feature.does_not_exist=true",
        "unknown.key=whatever",
        "homes.capacity=not-a-number",
        "extrap.ms=99999",
        "allay.arms.x=oops",
        "scan.key=broken",
        ""));

    config.load();

    assertFalse(config.isEnabled(OriginFeature.SMOOTH_ANIMATIONS));
    assertEquals(5, config.getHomeCapacity());
    assertEquals(150, config.getExtrapMs());
    assertEquals(12.0f, config.getAllayArmsShiftTexels());
  }

  @Test
  void settersClampOutOfRangeValues() {
    config.setHomeCapacity(99);
    assertEquals(15, config.getHomeCapacity());
    config.setHomeCapacity(-5);
    assertEquals(0, config.getHomeCapacity());

    config.setExtrapMs(5000);
    assertEquals(1000, config.getExtrapMs());
    config.setExtrapMs(-1);
    assertEquals(0, config.getExtrapMs());

    config.setAllayArmsShiftTexels(50.0f);
    assertEquals(32.0f, config.getAllayArmsShiftTexels());
    config.setAllayArmsShiftTexels(-50.0f);
    assertEquals(-32.0f, config.getAllayArmsShiftTexels());
  }

  @Test
  void serverListIsNormalizedAndDeduplicated() {
    config.setServersText(" A.COM ,a.com,, b.example.com ");

    assertEquals(Set.of("a.com", "b.example.com"), config.getServers());
    assertEquals("a.com,b.example.com", config.getServersText());
  }

  @Test
  void emptyServerListTextKeepsSetEmpty() {
    config.setServersText("");
    assertTrue(config.getServers().isEmpty());

    config.setServersText(" , , ");
    assertTrue(config.getServers().isEmpty());
  }

  @Test
  void parkedFeaturesStayOffEvenIfStoredEnabled() {
    config.setEnabled(OriginFeature.BREAK_SYNC, true);
    config.setEnabled(OriginFeature.AUCTION_PING, true);

    assertFalse(config.isEnabled(OriginFeature.BREAK_SYNC));
    assertFalse(config.isEnabled(OriginFeature.AUCTION_PING));
  }

  @Test
  void extrapListsAreSplitLowercasedAndTrimmed() {
    assertEquals(Set.of("minecraft:zombie", "originrealms:grimm"),
        OpenOriginsConfig.parseExtrapTypes(" Minecraft:Zombie , originrealms:grimm ,,"));
    assertEquals(Set.of(), OpenOriginsConfig.parseExtrapTypes(null));
    assertEquals(Set.of(), OpenOriginsConfig.parseExtrapTypes(""));
  }

  @Test
  void nonParkedFeaturesFollowStoredValue() {
    for (OriginFeature feature : OriginFeature.values()) {
      if (feature == OriginFeature.BREAK_SYNC || feature == OriginFeature.AUCTION_PING) {
        continue;
      }
      boolean expected = !feature.getDefaultValue();
      config.setEnabled(feature, expected);
      assertEquals(expected, config.isEnabled(feature), "feature " + feature.name());
    }
  }
}
