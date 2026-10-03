package com.openorigins.client;

public enum OriginFeature {
  LOCATION_TRACKER(FeatureCategory.FEATURES, "Discord Rich Presence"),
  ENHANCED_COSMETICS(FeatureCategory.FEATURES, "Enhanced Cosmetics"),
  SMOOTH_ANIMATIONS(FeatureCategory.ENHANCEMENTS, "Smooth Animations"),
  BLOCK_ENHANCEMENTS(FeatureCategory.ENHANCEMENTS, "Block Enhancements"),
  GHOST_BLOCK_FIX(FeatureCategory.ENHANCEMENTS, false, "Ghost Block Fix"),
  BACK_SWAG_SLOT(FeatureCategory.ENHANCEMENTS, "Back Swag Slot"),
  EXPAND_MODEL_BOUNDING_BOX(FeatureCategory.ENHANCEMENTS, "Expand Model Bounding Box"),
  HIDE_NAMES(FeatureCategory.ENHANCEMENTS, "Hide Names"),
  AUCTION_PING(FeatureCategory.UTILITIES, "Auction Ping"),
  SCOREBOARD_DESYNC_FIX(FeatureCategory.UTILITIES, "Scoreboard Desync Fix");

  private final FeatureCategory category;
  private final boolean defaultValue;
  private final String name;

  OriginFeature(FeatureCategory category, String name) {
    this(category, true, name);
  }

  OriginFeature(FeatureCategory category, boolean defaultValue, String name) {
    this.category = category;
    this.defaultValue = defaultValue;
    this.name = name;
  }

  public FeatureCategory getCategory() { return category; }
  public boolean getDefaultValue() { return defaultValue; }
  public String getName() { return name; }

  public String getDescriptionKey() {
    return "feature.openorigins." + this.name().toLowerCase() + ".desc";
  }
}
