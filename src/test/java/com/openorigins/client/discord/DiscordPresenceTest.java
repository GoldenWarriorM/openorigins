package com.openorigins.client.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class DiscordPresenceTest {
  @Test
  void buildsTheExpectedPresencePayload() {
    JsonObject json = JsonParser.parseString(DiscordPresence.buildRefreshJson(
        "1234567890", "Playing \"Skyblock\"", "In §aWorld", "icon", "A realm", 1_700_000_000_500L))
        .getAsJsonObject();

    assertEquals("1234567890", json.get("client_id").getAsString());
    assertEquals("Playing \"Skyblock\"", json.get("details").getAsString());
    assertEquals("In World", json.get("state").getAsString());
    assertEquals("icon", json.get("large_image").getAsString());
    assertEquals("A realm", json.get("large_text").getAsString());
    assertEquals(1_700_000_000L, json.get("start").getAsLong());
  }

  @Test
  void nullFieldsStayNull() {
    JsonObject json = JsonParser.parseString(
        DiscordPresence.buildRefreshJson("id", null, null, null, null, 0)).getAsJsonObject();

    assertTrue(json.get("details").isJsonNull());
    assertTrue(json.get("state").isJsonNull());
    assertTrue(json.get("large_image").isJsonNull());
    assertTrue(json.get("large_text").isJsonNull());
    assertEquals(0L, json.get("start").getAsLong());
  }

  @Test
  void negativeStartBecomesZero() {
    JsonObject json = JsonParser.parseString(
        DiscordPresence.buildRefreshJson("id", "d", "s", "i", "t", -5L)).getAsJsonObject();

    assertEquals(0L, json.get("start").getAsLong());
  }

  @Test
  void quotesBackslashesNewlinesAndControlsAreEscaped() {
    String raw = "a\"b\\c\nd\u0001e";
    JsonObject json = JsonParser.parseString(
        DiscordPresence.buildRefreshJson("id", "details", "s", raw, "t", 0)).getAsJsonObject();

    assertEquals("a\"b\\c\nd\u0001e", json.get("large_image").getAsString());
  }

  @Test
  void detailsAndStateLoseControlCharactersBeforeEncoding() {
    JsonObject json = JsonParser.parseString(
        DiscordPresence.buildRefreshJson("id", "two\nlines", "st\u0001ate", "i", "t", 0))
        .getAsJsonObject();

    assertEquals("twolines", json.get("details").getAsString());
    assertEquals("state", json.get("state").getAsString());
  }

  @Test
  void stripCodesRemovesFormattingAndServerGlyphs() {
    assertEquals("Hello World", DiscordPresence.stripCodes("§aHello §lWorld"));
    assertEquals("Item", DiscordPresence.stripCodes("ꑲ Item"));
    assertEquals("text", DiscordPresence.stripCodes("text\uE000"));
    assertEquals("a b", DiscordPresence.stripCodes(" a  b "));
    assertEquals("", DiscordPresence.stripCodes("§a§b"));
  }

  @Test
  void stripCodesKeepsNull() {
    assertNull(DiscordPresence.stripCodes(null));
  }
}
