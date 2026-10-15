package com.openorigins.client.screen;

import com.openorigins.client.FeatureCategory;
import java.util.List;

public enum ConfigTab {
  GENERAL("category.openorigins.general", List.of(FeatureCategory.GENERAL)),
  UI("category.openorigins.ui", List.of(FeatureCategory.UI)),
  CLIENT_FIXES("category.openorigins.fixes", List.of(FeatureCategory.FIXES)),
  NETWORK_FIXES("category.openorigins.network_fixes",
      List.of(FeatureCategory.NETWORK_FIXES)),
  EXPERIMENTAL("category.openorigins.experimental", List.of(FeatureCategory.EXPERIMENTAL)),
  MISC("category.openorigins.misc", List.of(FeatureCategory.MISC)),
  SERVERS("category.openorigins.servers", List.of(FeatureCategory.SERVERS));

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
