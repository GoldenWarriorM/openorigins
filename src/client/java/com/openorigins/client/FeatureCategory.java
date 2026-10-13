package com.openorigins.client;

public enum FeatureCategory {
  GENERAL, FIXES, UI, EXPERIMENTAL, MISC, SERVERS, ORIGIN_ADDONS;

  public String getTranslationKey() {
    return "category.openorigins." + this.name().toLowerCase();
  }
}
