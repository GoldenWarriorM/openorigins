package com.openorigins.client.net;

import com.openorigins.client.HandshakeState;
import com.openorigins.client.server.EntityData;
import com.openorigins.client.server.OriginStateTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OEPackets {
  private static final Logger LOGGER = LoggerFactory.getLogger("openorigins");
  private static boolean loggedTag;
  private static boolean loggedBackItem;
  private static final java.util.Set<Integer> loggedIds = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

  private OEPackets() {}

  public static void dispatch(int id, OEBuffer buf) {
    if (loggedIds.add(id)) {
      LOGGER.info("[OpenOrigins] first channel packet id={}", id);
    }
    switch (id) {
      case OEChannel.IN_HANDSHAKE -> onHandshake(buf);
      case OEChannel.IN_LOCATION -> onLocation(buf);
      case OEChannel.IN_NAMETAG -> onNametag(buf);
      case OEChannel.IN_BACKITEM -> onBackItem(buf);
      default -> {
      }
    }
  }

  private static void onHandshake(OEBuffer buf) {
    int version = buf.readVarInt();
    LOGGER.info("[OpenOrigins] handshake received, protocol version {}", version);
    if (version == OEChannel.PROTOCOL_VERSION) {
      HandshakeState.setHandshakeComplete(true);
    } else {
      HandshakeState.setOutdated(true);
      Minecraft mc = Minecraft.getInstance();
      mc.execute(() -> net.minecraft.client.gui.components.toasts.SystemToast.add(
          mc.gui.toastManager(),
          new net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId(),
          Component.literal("OpenOrigins"),
          Component.translatable("toast.openorigins.outdated")));
    }
  }

  private static void onLocation(OEBuffer buf) {
    boolean present = buf.readBoolean();
    String location = present ? buf.readString() : null;
    LOGGER.info("[OpenOrigins] location update: {}", location);
    OriginStateTracker.INSTANCE.update(location);
  }

  private static void onNametag(OEBuffer buf) {
    int entityId = buf.readVarInt();
    int action = buf.readByte();
    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null || mc.level.getEntity(entityId) == null) {
      return;
    }
    RegistryFriendlyByteBuf regBuf = buf.registryBuf();
    try {
      switch (action) {
        case 0 -> {
          Component line = ComponentSerialization.STREAM_CODEC.decode(regBuf);
          EntityData.addTagLine(entityId, line);
        }
        case 1 -> {
          int index = buf.readVarInt();
          Component line = ComponentSerialization.STREAM_CODEC.decode(regBuf);
          EntityData.setTagLine(entityId, index, line);
        }
        case 2 -> EntityData.removeTagLine(entityId, buf.readVarInt());
        case 3 -> EntityData.setTagOffset(entityId, (float) buf.readDouble());
        default -> {
        }
      }
    } catch (Throwable t) {
      EntityData.clearTag(entityId);
    }
    if (!loggedTag) {
      loggedTag = true;
      LOGGER.info("[OpenOrigins] first nametag packet applied");
    }
  }

  private static void onBackItem(OEBuffer buf) {
    int entityId = buf.readVarInt();
    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null || mc.level.getEntity(entityId) == null) {
      return;
    }
    net.minecraft.world.entity.Entity target = mc.level.getEntity(entityId);
    LOGGER.info("[OpenOrigins] backitem for {}#{} (you={})", target.getType().toShortString(), entityId,
        target == mc.player);
    ItemStack stack;
    RegistryFriendlyByteBuf regBuf = buf.registryBuf();
    byte[] raw = new byte[regBuf.readableBytes()];
    regBuf.getBytes(regBuf.readerIndex(), raw);
    try {
      stack = LegacyTranslator.decodeItem(regBuf);
    } catch (Throwable t) {
      StringBuilder hex = new StringBuilder();
      for (int i = 0; i < Math.min(raw.length, 96); i++) {
        hex.append(String.format("%02x", raw[i]));
      }
      LOGGER.warn("[OpenOrigins] backitem decode failed: {}: {} ({} bytes, head={})",
          t.getClass().getSimpleName(), t.getMessage(), raw.length, hex);
      stack = ItemStack.EMPTY;
    }
    EntityData.setBackItem(entityId, stack.isEmpty() ? null : stack);
    if (!loggedBackItem) {
      loggedBackItem = true;
      StringBuilder keys = new StringBuilder();
      for (net.minecraft.core.component.DataComponentType<?> t : stack.getComponents().keySet()) {
        keys.append(t.toString()).append(',');
      }
      net.minecraft.network.chat.Component customName =
          stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
      String nameJson = "";
      if (customName != null) {
        try {
          nameJson = net.minecraft.network.chat.ComponentSerialization.CODEC
              .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, customName)
              .getOrThrow().toString();
        } catch (Throwable t) {
          nameJson = customName.getString();
        }
      }
      LOGGER.info("[OpenOrigins] first backitem packet applied (empty={}) rarity={} patch=[{}] custom_name={}",
          stack.isEmpty(), stack.getRarity(), keys, nameJson);
    }
  }
}
