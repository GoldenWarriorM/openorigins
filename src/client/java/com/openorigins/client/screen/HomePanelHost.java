package com.openorigins.client.screen;

/** Screens hosting the homes panel; rebuilt in place after background fetches. */
public interface HomePanelHost {
  void rebuildHomeWidgets();

  /** Drops a home immediately (server confirmed deletion). */
  void forgetHome(String name);

  /** Adds a home immediately (server confirmed creation). */
  void addHome(String name);
}
