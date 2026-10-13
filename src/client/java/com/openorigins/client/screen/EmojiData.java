package com.openorigins.client.screen;

/** Curated emoji set for the chat picker (own selection, grouped by mood). */
public final class EmojiData {
  private EmojiData() {}

  public static final String[] SMILEYS = {
      "\uD83D\uDE00", "\uD83D\uDE01", "\uD83D\uDE02", "\uD83D\uDE03", "\uD83D\uDE04",
      "\uD83D\uDE09", "\uD83D\uDE0A", "\uD83D\uDE0B", "\uD83D\uDE0E", "\uD83D\uDE0D",
      "\uD83D\uDE18", "\uD83D\uDE17", "\uD83D\uDE19", "\uD83D\uDE1A", "\uD83D\uDE42",
      "\uD83D\uDE43", "\uD83D\uDE14", "\uD83D\uDE1E", "\uD83D\uDE22", "\uD83D\uDE2D",
      "\uD83D\uDE31", "\uD83D\uDE33", "\uD83D\uDE37", "\uD83D\uDE44", "\uD83D\uDE4A",
      "\uD83D\uDE07", "\uD83E\uDD20", "\uD83E\uDD21", "\uD83D\uDC7B", "\uD83D\uDC80",
      "\uD83D\uDE3A", "\uD83D\uDE38"
  };

  public static final String[] GESTURES = {
      "\uD83D\uDC4D", "\uD83D\uDC4E", "\uD83D\uDC4C", "\uD83D\uDD90", "\u270C\uFE0F",
      "\uD83E\uDD1E", "\uD83D\uDCAA", "\uD83D\uDE4F", "\uD83D\uDC4F", "\uD83D\uDC4B",
      "\uD83D\uDC46", "\uD83D\uDC47", "\uD83D\uDC48", "\uD83D\uDC49", "\u270A",
      "\uD83D\uDD95"
  };

  public static final String[] HEARTS = {
      "\u2764\uFE0F", "\uD83D\uDC94", "\uD83D\uDC95", "\uD83D\uDC96", "\uD83D\uDC97",
      "\uD83D\uDC98", "\uD83D\uDC99", "\uD83D\uDC9C", "\uD83D\uDDA4", "\uD83E\uDDE1",
      "\uD83D\uDC9F", "\u2728", "\u2B50", "\uD83C\uDF1F"
  };

  public static String[] all() {
    String[] out = new String[SMILEYS.length + GESTURES.length + HEARTS.length];
    System.arraycopy(SMILEYS, 0, out, 0, SMILEYS.length);
    System.arraycopy(GESTURES, 0, out, SMILEYS.length, GESTURES.length);
    System.arraycopy(HEARTS, 0, out, SMILEYS.length + GESTURES.length, HEARTS.length);
    return out;
  }
}
