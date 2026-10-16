package com.openorigins.client;

public enum OriginFeature {
  LOCATION_TRACKER(FeatureCategory.GENERAL, "Discord Rich Presence"),
  ENHANCED_COSMETICS(FeatureCategory.GENERAL, "Enhanced Cosmetics"),
  SMOOTH_ANIMATIONS(FeatureCategory.GENERAL, "Smooth Animations"),
  BLOCK_ENHANCEMENTS(FeatureCategory.FIXES, "Block Enhancements"),
  BACK_SWAG_SLOT(FeatureCategory.UI, "Back Swag Slot"),
  HELMET_COSMETIC_SLOT(FeatureCategory.EXPERIMENTAL, false, "Helmet Cosmetic Slot"),
  EXPAND_MODEL_BOUNDING_BOX(FeatureCategory.FIXES, "Expand Model Bounding Box"),
  HIDE_NAMES(FeatureCategory.GENERAL, "Hide Names"),
  AUCTION_PING(FeatureCategory.UI, "Auction Ping"),
  PER_ITEM_PRICE(FeatureCategory.UI, "Show Per-Item Price"),
  AH_PRICE_DB(FeatureCategory.UI, "Auction Price Database"),
  CUTSCENE_ENHANCEMENTS(FeatureCategory.GENERAL, "Cutscene Enhancements"),
  CUSTOM_ITEM_IDS(FeatureCategory.NETWORK_FIXES, "Custom Item IDs"),
  DEBUG_LOGGING(FeatureCategory.MISC, false, "Debug Logging"),
  OUTBOX_HUD(FeatureCategory.MISC, false, "Outbox Monitor"),
  SCOREBOARD_DESYNC_FIX(FeatureCategory.NETWORK_FIXES, "Scoreboard Desync Fix"),
  REGISTRY_DESYNC_FIX(FeatureCategory.NETWORK_FIXES, "Registry Desync Fix"),
  BADGER_TASK_HUD(FeatureCategory.UI, "Badger Task HUD"),
  ALLAY_ARMS_FIX(FeatureCategory.FIXES, "Allay Arms Fix"),
  DENY_SPAM_FILTER(FeatureCategory.FIXES, "Deny Spam Filter"),
  ENTITY_EXTRAPOLATION(FeatureCategory.EXPERIMENTAL, false, "Entity Extrapolation"),
  MOA_FACING_FIX(FeatureCategory.FIXES, "Moa Facing Fix"),
  BREAK_SYNC(FeatureCategory.EXPERIMENTAL, false, "Break Sync", true),
  STRICT_RECIPE_MATCH(FeatureCategory.NETWORK_FIXES, "Strict Recipe Match"),
  OA_EMOJI_PICKER(FeatureCategory.UI, false, "Server Emoji", true);

  private final FeatureCategory category;
  private final boolean defaultValue;
  private final String name;
  private final boolean hidden;

  OriginFeature(FeatureCategory category, String name) {
    this(category, true, name, false);
  }

  OriginFeature(FeatureCategory category, boolean defaultValue, String name) {
    this(category, defaultValue, name, false);
  }

  OriginFeature(FeatureCategory category, boolean defaultValue, String name, boolean hidden) {
    this.category = category;
    this.defaultValue = defaultValue;
    this.name = name;
    this.hidden = hidden;
  }

  public FeatureCategory getCategory() { return category; }
  public boolean getDefaultValue() { return defaultValue; }
  public String getName() { return name; }
  /** Hidden from settings UI but kept in code (parked experiments). */
  public boolean isHidden() { return hidden; }

  public String getDescriptionKey() {
    return "feature.openorigins." + this.name().toLowerCase() + ".desc";
  }
}
