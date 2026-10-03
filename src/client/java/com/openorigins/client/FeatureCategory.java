package com.openorigins.client;

public enum FeatureCategory {
  FEATURES, ENHANCEMENTS, UTILITIES;

  public String getTranslationKey() {
    return "category.openorigins." + this.name().toLowerCase();
  }
}
