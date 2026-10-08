package com.openorigins.client;

public enum FeatureCategory {
  GENERAL, FIXES, UI, MISC, SERVERS;

  public String getTranslationKey() {
    return "category.openorigins." + this.name().toLowerCase();
  }
}
