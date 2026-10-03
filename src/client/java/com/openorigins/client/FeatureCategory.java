package com.openorigins.client;

public enum FeatureCategory {
  GENERAL, FIXES, NOTIFICATIONS;

  public String getTranslationKey() {
    return "category.openorigins." + this.name().toLowerCase();
  }
}
