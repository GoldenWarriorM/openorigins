package com.openorigins.client.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AuctionParseTest {
  private static final long NOW = 1_700_000_000_000L;
  private static final long MIN = 60_000L;
  private static final long HOUR = 60 * MIN;
  private static final long DAY = 24 * HOUR;

  @Test
  void readsListingEndFromTimeLeftLore() {
    assertEquals(NOW + 17 * HOUR + 50 * MIN,
        AuctionParse.parseEndsAt(List.of("Price: 1,000", "Time left: 17 hours and 50 minutes"), NOW));
    assertEquals(NOW + 2 * DAY + 3 * MIN,
        AuctionParse.parseEndsAt(List.of("time left: 2 days and 3 minutes"), NOW));
  }

  @Test
  void missingOrBrokenTimeLeftMeansUnknownEnd() {
    assertEquals(-1, AuctionParse.parseEndsAt(List.of("Price: 1,000"), NOW));
    assertEquals(-1, AuctionParse.parseEndsAt(List.of("Time left: soon"), NOW));
    assertEquals(-1, AuctionParse.parseEndsAt(List.of(), NOW));
  }

  @Test
  void recognisesSoldRowsAndStoredSoldDescriptions() {
    assertTrue(AuctionParse.isSoldRow(List.of("Price: 1,000", "Sold: 5 minutes ago")));
    assertFalse(AuctionParse.isSoldRow(List.of("Price: 1,000", "Seller: Zoltraq")));
    assertTrue(AuctionParse.isSoldDesc("Stone\nSold: 2 hours ago"));
    assertFalse(AuctionParse.isSoldDesc("Stone\nSeller: Zoltraq"));
    assertFalse(AuctionParse.isSoldDesc(""));
    assertFalse(AuctionParse.isSoldDesc(null));
  }

  @Test
  void parsesPriceLinesWithGroupingAndDecimals() {
    assertEquals(1234.5, AuctionParse.parsePrice("Price: 1,234.5"));
    assertEquals(2000.0, AuctionParse.parsePrice("price: 2,000"));
    assertEquals(75.0, AuctionParse.parsePrice("  Price: 75  "));
    assertNull(AuctionParse.parsePrice("Seller: Zoltraq"));
    assertNull(AuctionParse.parsePrice("Price: lots"));
    assertNull(AuctionParse.parsePrice(null));
  }

  @Test
  void formatsWholeUnitsAndFractions() {
    assertEquals("1,234", AuctionParse.formatUnit(1234.0));
    assertEquals("1,234.57", AuctionParse.formatUnit(1234.567));
    assertEquals("0.50", AuctionParse.formatUnit(0.5));
  }

  @Test
  void sellerNickDropsRankBadges() {
    assertEquals("zoltraq", AuctionParse.sellerNick("♛ Zoltraq"));
    assertEquals("zoltraq", AuctionParse.sellerNick("  Zoltraq "));
    assertEquals("", AuctionParse.sellerNick(null));
    assertEquals("", AuctionParse.sellerNick("   "));
  }

  @Test
  void rarityComesFromTheBadgeGlyph() {
    assertEquals("LEGENDARY", AuctionParse.rarityOf("ꑵ Sword of Doom"));
    assertEquals("COMMON", AuctionParse.rarityOf("ꑲ Dirt"));
    assertEquals("NONE", AuctionParse.rarityOf("Plain Stone"));
    assertEquals("NONE", AuctionParse.rarityOf(""));
    assertEquals("NONE", AuctionParse.rarityOf(null));
  }

  @Test
  void normNameStripsBadgesAndLowercases() {
    assertEquals("sword of doom", AuctionParse.normName("ꑵ Sword of Doom"));
    assertEquals("dirt", AuctionParse.normName("  ꑲ   Dirt  "));
    assertEquals("plain stone", AuctionParse.normName("Plain Stone"));
    assertEquals("", AuctionParse.normName(null));
  }

  @Test
  void identicalItemsShareAVariantSuffix() {
    List<String> a = List.of("A sturdy blade", "Enchant Slots: (0/2)");
    List<String> b = List.of("A sturdy blade", "Enchant Slots: (2/2)");

    assertEquals(AuctionParse.variantOf("Sword", a)[0], AuctionParse.variantOf("Sword", b)[0]);
  }

  @Test
  void differentDescriptionsChangeTheVariantSuffix() {
    String first = AuctionParse.variantOf("Sword", List.of("A sturdy blade"))[0];
    String second = AuctionParse.variantOf("Sword", List.of("A rusty blade"))[0];

    assertNotEquals(first, second);
    assertEquals(AuctionParse.normName("Sword").isEmpty(), false);
    assertTrue(first.contains("|n=sword"));
    assertTrue(first.contains("|v="));
  }

  @Test
  void listingNoiseNeverJoinsTheDescription() {
    String[] variant = AuctionParse.variantOf("Sword", List.of(
        "Price: 1,000", "Seller: Zoltraq", "Time left: 2 hours", "Created: May 1",
        "World: Blue", "Position: 1, 2, 3", "Click to buy", "A sturdy blade"));

    assertEquals("A sturdy blade", variant[1]);
    assertEquals("A sturdy blade", variant[2]);
  }

  @Test
  void enchantStateIsPartOfTheIdentityButSocketCountIsNot() {
    String clean = AuctionParse.variantOf("Sword", List.of(
        "A sturdy blade", "Enchant Slots: (0/2)"))[0];
    String enchanted = AuctionParse.variantOf("Sword", List.of(
        "A sturdy blade", "Enchant Slots: (1/2)", "+ Sharpness I"))[0];

    assertTrue(clean.endsWith("|e=0"));
    assertTrue(enchanted.endsWith("|e=1"));
    assertNotEquals(clean, enchanted);
  }

  @Test
  void labelsAreCappedAtThirtyCharacters() {
    String longLine = "abcdefghijklmnopqrstuvwxyz0123456789";
    String label = AuctionParse.variantOf("Sword", List.of(longLine))[1];

    assertEquals("abcdefghijklmnopqrstuvwxyz0123…", label);
    assertEquals(31, label.length());
  }

  @Test
  void aggregateIsVolumeWeighted() {
    AuctionParse.Aggregate agg = AuctionParse.aggregate(List.of(
        new double[]{10, 1}, new double[]{20, 3}));

    assertEquals(17.5, agg.avg(), 1e-9);
    assertEquals(20.0, agg.med(), 1e-9);
  }

  @Test
  void aggregateMedianUsesWeights() {
    AuctionParse.Aggregate agg = AuctionParse.aggregate(List.of(
        new double[]{20, 2}, new double[]{10, 2}));

    assertEquals(15.0, agg.avg(), 1e-9);
    assertEquals(10.0, agg.med(), 1e-9);
  }

  @Test
  void aggregateRejectsEmptyInput() {
    assertNull(AuctionParse.aggregate(List.of()));
    assertNull(AuctionParse.aggregate(null));
  }
}
