package com.openorigins.client.server;

import com.openorigins.client.discord.DiscordPresence;
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
      DiscordPresence.clear();
    });
    ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
      if (this.loginTime < 0) {
        this.loginTime = System.currentTimeMillis();
      }
      this.pushPresence();
    });
  }

  public void update(String location) {
    this.location = location;
    this.pushPresence();
  }

  private void pushPresence() {
    if (!com.openorigins.client.OpenOriginsConfig.getInstance()
        .isEnabled(com.openorigins.client.OriginFeature.LOCATION_TRACKER)) {
      return;
    }
    String name = this.location;
    RemoteRegistry.LocationData data = name == null ? null : RemoteRegistry.get().getLocation(name);
    String icon = data == null ? null : data.icon();
    String details = name != null ? name : "Online";
    // NOTE: Discord 1.0.160+ drops activities with null/empty state, so a
    // neutral filler; brand signature intentionally omitted.
    boolean chatroom = data != null && data.chatroom();
    String state = chatroom ? "Chatroom" : "Exploring";
    DiscordPresence.refresh(
        com.openorigins.client.OpenOriginsConfig.getInstance().getDiscordClientId(),
        details, state, icon, name, this.loginTime);
  }

  public String getLocation() {
    return this.location;
  }

  public long getLoginTime() {
    return this.loginTime;
  }
}
