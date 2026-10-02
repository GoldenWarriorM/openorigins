package com.openorigins.client.server;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/** Tracks location and login time. */
public final class OriginStateTracker {
  public static final OriginStateTracker INSTANCE = new OriginStateTracker();

  private volatile String location;
  private volatile long loginTime = -1L;

  private OriginStateTracker() {}

  public void setup() {
    ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
      this.loginTime = -1L;
      this.location = null;
      EntityData.clearAll();
    });
    ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
      this.loginTime = System.currentTimeMillis();
    });
  }

  public void update(String location) {
    this.location = location;
  }

  public String getLocation() {
    return this.location;
  }

  public long getLoginTime() {
    return this.loginTime;
  }
}
