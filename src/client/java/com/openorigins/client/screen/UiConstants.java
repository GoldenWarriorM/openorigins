package com.openorigins.client.screen;

/**
 * Shared screen metrics. Row heights differ on purpose (row content differs
 * per list) — centralizing them keeps the clamps and spacing honest when one
 * list changes.
 */
final class UiConstants {
  private UiConstants() {}

  /** FeatureList row height. */
  static final int FEATURE_ROW_H = 24;

  /** Config right-panel row / tab height. */
  static final int CONFIG_ROW_H = 22;

  /** FeatureList row width clamp: min..max while following list width. */
  static final int ROW_W_MIN = 220;
  static final int ROW_W_MAX = 380;

  /** Config tab strip width clamp. */
  static final int TAB_W_MIN = 240;
  static final int TAB_W_MAX = 420;
}
