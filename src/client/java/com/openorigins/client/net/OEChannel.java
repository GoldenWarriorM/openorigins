package com.openorigins.client.net;

import net.minecraft.resources.Identifier;

/**
 * Wire format: short packet id + body.
 * Inbound: 0 handshake, 1 location, 2 props (unused), 3 spawn (unused), 4 nametag, 5 backitem.
 * Outbound: 0 handshake-response.
 */
public final class OEChannel {
  public static final Identifier ID = Identifier.fromNamespaceAndPath("originrealms", "enhanced");
  public static final int PROTOCOL_VERSION = 5;

  public static final int IN_HANDSHAKE = 0;
  public static final int IN_LOCATION = 1;
  public static final int IN_NAMETAG = 4;
  public static final int IN_BACKITEM = 5;
  public static final int OUT_HANDSHAKE_RESPONSE = 0;

  private OEChannel() {}
}
