package com.openorigins.client.net;

import com.openorigins.client.HandshakeState;
import com.openorigins.client.OpenOriginsConfig;
import com.openorigins.client.OriginFeature;
import io.netty.buffer.Unpooled;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Login handshake + play packets for originrealms:enhanced. */
public final class OENetwork {
  private static final Logger LOGGER = LoggerFactory.getLogger("openorigins");
  private static volatile boolean loginRegistered;

  private OENetwork() {}

  /** Toggles the login handshake. Disabled means unregistered: returning null would NPE login on 26.2. Applies to the next login. */
  public static void refreshLoginReceiver() {
    boolean want = OpenOriginsConfig.getInstance().isEnabled(OriginFeature.ENHANCED_COSMETICS);
    if (want && !loginRegistered) {
      ClientLoginNetworking.registerGlobalReceiver(OEChannel.ID, OENetwork::onLoginQuery);
      loginRegistered = true;
    } else if (!want && loginRegistered) {
      ClientLoginNetworking.unregisterGlobalReceiver(OEChannel.ID);
      loginRegistered = false;
    }
  }

  private static CompletableFuture<FriendlyByteBuf> onLoginQuery(net.minecraft.client.Minecraft client,
      net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl handler, FriendlyByteBuf buf,
      java.util.function.Consumer<io.netty.channel.ChannelFutureListener> listenerAdder) {
    try {
      int id = buf.readShort();
      com.openorigins.client.util.DebugLog.info("[OpenOrigins] login query id={}", id);
      if (id == OEChannel.IN_HANDSHAKE && buf.readVarInt() == OEChannel.PROTOCOL_VERSION) {
        HandshakeState.setHandshakeComplete(true);
        com.openorigins.client.util.DebugLog.info("[OpenOrigins] login handshake complete, responding");
        FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer());
        OEBuffer.writeShortVarInt(out, OEChannel.OUT_HANDSHAKE_RESPONSE, OEChannel.PROTOCOL_VERSION);
        com.openorigins.client.util.OutboxStats.record("handshake v5", 15000L);
        com.openorigins.client.util.OutboxStats.ackExact("handshake v5");
        return CompletableFuture.<FriendlyByteBuf>completedFuture(out);
      }
    } catch (Throwable t) {
      com.openorigins.client.util.DebugLog.warn("[OpenOrigins] failed to parse login handshake", t);
    }
    HandshakeState.setOutdated(true);
    Minecraft.getInstance().execute(() -> net.minecraft.client.gui.components.toasts.SystemToast.add(
        Minecraft.getInstance().gui.toastManager(),
        new net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId(),
        Component.literal("OpenOrigins"),
        Component.translatable("toast.openorigins.outdated")));
    return null;
  }

  public static void setup() {
    PayloadTypeRegistry.clientboundPlay().register(OEPayload.TYPE, OEPayload.CODEC);
    PayloadTypeRegistry.serverboundPlay().register(OEPayload.TYPE, OEPayload.CODEC);

    ClientPlayNetworking.registerGlobalReceiver(OEPayload.TYPE, (payload, ctx) -> {
      try {
        FriendlyByteBuf raw = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload.data()));
        RegistryFriendlyByteBuf buf =
            new RegistryFriendlyByteBuf(raw, ctx.player().registryAccess());
        OEPackets.dispatch(new OEBuffer(buf).readShort(), new OEBuffer(buf));
      } catch (Throwable t) {
        com.openorigins.client.util.DebugLog.warn("[OpenOrigins] failed to handle channel packet", t);
      }
    });

    refreshLoginReceiver();

    ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
      HandshakeState.setHandshakeComplete(false);
      HandshakeState.setOutdated(false);
      com.openorigins.client.util.BackgroundMenu.disarmNow();
      refreshLoginReceiver();
    });
  }
}
