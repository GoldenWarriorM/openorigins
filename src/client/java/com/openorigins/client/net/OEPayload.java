package com.openorigins.client.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

/** Raw channel bytes; parsed by short id in OEPackets. */
public record OEPayload(byte[] data) implements CustomPacketPayload {
  public static final Type<OEPayload> TYPE = new Type<>(OEChannel.ID);
  public static final StreamCodec<RegistryFriendlyByteBuf, OEPayload> CODEC = StreamCodec.of(
      (buf, payload) -> buf.writeBytes(payload.data),
      buf -> {
        ByteBuf slice = buf.readBytes(buf.readableBytes());
        try {
          byte[] bytes = new byte[slice.readableBytes()];
          slice.readBytes(bytes);
          return new OEPayload(bytes);
        } finally {
          slice.release();
        }
      });

  @Override
  public @NotNull Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
