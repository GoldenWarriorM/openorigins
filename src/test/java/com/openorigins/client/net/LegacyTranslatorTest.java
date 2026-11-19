package com.openorigins.client.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The server sends item bytes with 1.21 item ids and 26.2 component ids, and it
 * hides the real item inside custom data. These tests pin the decoder down on a
 * packet captured from the server plus a synthesized packet without that stack.
 */
public class LegacyTranslatorTest {
  private static RegistryAccess access;

  @BeforeAll
  static void bootstrap() {
    net.minecraft.SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
    access = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    // A running game binds item components from its data pack; tests have none.
    BuiltInRegistries.ITEM.listElements()
        .forEach(h -> h.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY));
  }

  @Test
  void decodesCapturedPacketWithPolymerStack() throws Exception {
    RegistryFriendlyByteBuf buf = buffer(resource("backitem-polymer.hex"));

    ItemStack stack = LegacyTranslator.decodeItem(buf);

    // The stand-in item is an enchanted book; the real one comes from the polymer stack.
    assertEquals("minecraft:firework_star", stack.getItem().toString());
    assertEquals(2, stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().size());
    assertEquals(List.of(16283.0F), stack.get(DataComponents.CUSTOM_MODEL_DATA).floats());
    assertFalse(stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT).hideTooltip());
    assertNotNull(stack.get(DataComponents.FIREWORK_EXPLOSION));
    assertNotNull(stack.get(DataComponents.CUSTOM_DATA));
    assertEquals(0, buf.readableBytes());
  }

  @Test
  void decodesThePacketNumberedLike263() throws Exception {
    // Same cape from the same server after it moved to 26.3 numbering:
    // firework_explosion is 70 there, which the 26.2 table reads as profile.
    RegistryFriendlyByteBuf buf = buffer(resource("backitem-263.hex"));

    ItemStack stack = LegacyTranslator.decodeItem(buf);

    assertEquals("minecraft:firework_star", stack.getItem().toString());
    assertEquals(2, stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().size());
    assertNotNull(stack.get(DataComponents.CUSTOM_MODEL_DATA));
    assertNotNull(stack.get(DataComponents.FIREWORK_EXPLOSION));
    TooltipDisplay display = stack.get(DataComponents.TOOLTIP_DISPLAY);
    assertNotNull(display);
    assertTrue(display.hiddenComponents().contains(DataComponents.FIREWORK_EXPLOSION));
    assertTrue(display.hiddenComponents().contains(DataComponents.ENCHANTMENTS));
  }

  @Test
  void keepsTheSentItemWhenThereIsNoPolymerStack() {
    RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
    buf.writeVarInt(1);
    buf.writeVarInt(1244);   // 1.21 enchanted_book
    buf.writeVarInt(2);      // added components
    buf.writeVarInt(0);      // removed components
    buf.writeVarInt(6);      // minecraft:custom_name
    ComponentSerialization.STREAM_CODEC.encode(buf, Component.literal("Armored Cape"));
    buf.writeVarInt(17);     // minecraft:custom_model_data
    DataComponents.CUSTOM_MODEL_DATA.streamCodec().encode(buf,
        new CustomModelData(List.of(3522.0F), List.of(), List.of(), List.of()));

    ItemStack stack = LegacyTranslator.decodeItem(buf);

    assertEquals("minecraft:enchanted_book", stack.getItem().toString());
    assertEquals("Armored Cape", stack.getHoverName().getString());
    assertEquals(List.of(3522.0F), stack.get(DataComponents.CUSTOM_MODEL_DATA).floats());
    assertEquals(0, buf.readableBytes());
  }

  @Test
  void mapsComponentIdsThroughThe262Table() {
    // 26.3 moved these two entries, so the runtime registry cannot be used.
    assertSame(DataComponents.FIREWORK_EXPLOSION, LegacyTranslator.componentType(68));
    assertSame(DataComponents.PROFILE, LegacyTranslator.componentType(70));
    assertSame(DataComponents.CUSTOM_DATA, LegacyTranslator.componentType(0));
  }

  private static RegistryFriendlyByteBuf buffer(String hex) {
    return new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(unhex(hex)), access);
  }

  private static String resource(String name) throws Exception {
    try (InputStream in = LegacyTranslatorTest.class
        .getResourceAsStream("/openorigins/" + name)) {
      if (in == null) {
        throw new IllegalStateException("missing test resource " + name);
      }
      return new String(in.readAllBytes(), StandardCharsets.US_ASCII).trim();
    }
  }

  private static byte[] unhex(String s) {
    byte[] out = new byte[s.length() / 2];
    for (int i = 0; i < out.length; i++) {
      out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
    }
    return out;
  }
}
