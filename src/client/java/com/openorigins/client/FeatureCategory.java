package com.openorigins.client;

public enum FeatureCategory {
  GENERAL, FIXES, UI, MISC;

  public String getTranslationKey() {
    return "category.openorigins." + this.name().toLowerCase();
  }
}
