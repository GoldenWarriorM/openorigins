package com.openorigins.client;

public enum OriginFeature {
  LOCATION_TRACKER(FeatureCategory.GENERAL, "Discord Rich Presence"),
  ENHANCED_COSMETICS(FeatureCategory.GENERAL, "Enhanced Cosmetics"),
  SMOOTH_ANIMATIONS(FeatureCategory.GENERAL, "Smooth Animations"),
  BLOCK_ENHANCEMENTS(FeatureCategory.FIXES, "Block Enhancements"),
  GHOST_BLOCK_FIX(FeatureCategory.FIXES, false, "Ghost Block Fix"),
  BACK_SWAG_SLOT(FeatureCategory.GENERAL, "Back Swag Slot"),
  HELMET_COSMETIC_SLOT(FeatureCategory.GENERAL, "Helmet Cosmetic Slot"),
  EXPAND_MODEL_BOUNDING_BOX(FeatureCategory.GENERAL, "Expand Model Bounding Box"),
  HIDE_NAMES(FeatureCategory.GENERAL, "Hide Names"),
  AUCTION_PING(FeatureCategory.NOTIFICATIONS, "Auction Ping"),
  DEBUG_LOGGING(FeatureCategory.MISC, false, "Debug Logging"),
  SCOREBOARD_DESYNC_FIX(FeatureCategory.FIXES, "Scoreboard Desync Fix"),
  REGISTRY_DESYNC_FIX(FeatureCategory.FIXES, "Registry Desync Fix"),
  BADGER_TASK_HUD(FeatureCategory.GENERAL, "Badger Task HUD");

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
