package com.openorigins.client.screen;

import com.openorigins.client.FeatureCategory;
import java.util.List;

public enum ConfigTab {
  GENERAL("category.openorigins.general", List.of(FeatureCategory.GENERAL, FeatureCategory.FIXES)),
  UI("category.openorigins.ui", List.of(FeatureCategory.UI)),
  MISC("category.openorigins.misc", List.of(FeatureCategory.MISC));

  private final String titleKey;
  private final List<FeatureCategory> categories;

  ConfigTab(String titleKey, List<FeatureCategory> categories) {
    this.titleKey = titleKey;
    this.categories = categories;
  }

  public String getTitleKey() {
    return titleKey;
  }

  public List<FeatureCategory> getCategories() {
    return categories;
  }
}
