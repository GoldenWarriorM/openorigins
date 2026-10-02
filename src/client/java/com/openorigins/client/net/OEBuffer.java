package com.openorigins.client.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;

public final class OEBuffer {
  private final RegistryFriendlyByteBuf buf;

  public OEBuffer(RegistryFriendlyByteBuf buf) {
    this.buf = buf;
  }

  public int readShort() { return this.buf.readShort(); }
  public int readVarInt() { return this.buf.readVarInt(); }
  public int readByte() { return this.buf.readByte(); }
  public boolean readBoolean() { return this.buf.readBoolean(); }
  public float readFloat() { return this.buf.readFloat(); }
  public double readDouble() { return this.buf.readDouble(); }
  public String readString() { return this.buf.readUtf(); }

  public RegistryFriendlyByteBuf registryBuf() { return this.buf; }

  public static void writeShortVarInt(FriendlyByteBuf out, int id, int version) {
    out.writeShort(id);
    out.writeVarInt(version);
  }
}
