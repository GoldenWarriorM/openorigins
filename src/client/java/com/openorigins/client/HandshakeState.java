package com.openorigins.client;

/** Handshake/outdated flags, reset on disconnect. */
public final class HandshakeState {
  private static volatile boolean handshakeComplete;
  private static volatile boolean outdated;

  private HandshakeState() {}

  public static boolean isHandshakeComplete() { return handshakeComplete; }
  public static void setHandshakeComplete(boolean v) { handshakeComplete = v; }
  public static boolean isOutdated() { return outdated; }
  public static void setOutdated(boolean v) { outdated = v; }
}
