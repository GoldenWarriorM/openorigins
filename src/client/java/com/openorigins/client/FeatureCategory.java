package com.openorigins.client;

public enum FeatureCategory {
  GENERAL, FIXES, UI, EXPERIMENTAL, MISC, SERVERS;

  public String getTranslationKey() {
    return "category.openorigins." + this.name().toLowerCase();
  }
}
