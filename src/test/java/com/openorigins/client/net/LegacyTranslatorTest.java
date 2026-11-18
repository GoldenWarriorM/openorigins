package com.openorigins.client.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Replays a back item packet captured from the server: lore, tooltip display,
 *  item name, custom model data, firework explosion and the polymer stack. */
public class LegacyTranslatorTest {
  private static final String CAPTURED =
      "01dc0906000b020a080005636f6c6f7200057768697465080004746578740003ea919e0100"
          + "066974616c696300000a080005636f6c6f72000723464643373435080004746578740015"
          + "416368696576656d656e74204578636c75736976650100066974616c6963000012000244"
          + "0d090a09000565787472610a00000002080005636f6c6f72000577686974650800047465"
          + "78740004ea91b42000080005636f6c6f7200072338423543463608000474657874001841"
          + "726d6f726564204361706520284772617669746974652900080004746578740000001101"
          + "467e6c0000000044000100cb5148000000000a0a000e24706f6c796d65723a737461636b"
          + "0a000a636f6d706f6e656e74730a001b6d696e6563726166743a637573746f6d5f6d6f64"
          + "656c5f64617461090006666c6f6174730500000001467e6c00000800136d696e65637261"
          + "66743a6974656d5f6e616d65001841726d6f726564204361706520284772617669746974"
          + "652909000e6d696e6563726166743a6c6f72650a00000002080005636f6c6f72000577"
          + "68697465080004746578740003ea919e0100066974616c69630000080005636f6c6f72"
          + "000723464643373435080004746578740015416368696576656d656e74204578636c7573"
          + "6976650100066974616c696300000a001c6d696e6563726166743a66697265776f726b5f"
          + "6578706c6f73696f6e0800057368617065000a736d616c6c5f62616c6c090006636f6c"
          + "6f7273030000000100cb5148000a00196d696e6563726166743a746f6f6c7469705f6469"
          + "73706c617909001168696464656e5f636f6d706f6e656e74730800000002001c6d696e65"
          + "63726166743a66697265776f726b5f6578706c6f73696f6e00166d696e656372616674"
          + "3a656e6368616e746d656e7473000a00156d696e6563726166743a637573746f6d5f6461"
          + "74610a00125075626c696342756b6b697456616c756573010008436f736d657469630103"
          + "000d436f736d65746963436f6c6f7200cb514808000c436f736d657469634e616d650016"
          + "61726d6f7265645f636170655f6772617669746974650800067261726974790004657069"
          + "63000000080002696400176d696e6563726166743a66697265776f726b5f737461720000";

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
  void decodesCapturedBackItem() {
    RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(unhex(CAPTURED)), access);

    ItemStack stack = LegacyTranslator.decodeItem(buf);

    // The stand-in item is an enchanted book; the real one comes from the polymer stack.
    assertEquals("minecraft:firework_star", stack.getItem().toString());
    assertEquals(2, stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().size());
    assertEquals(List.of(16283.0F), stack.get(DataComponents.CUSTOM_MODEL_DATA).floats());
    assertFalse(stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY,
        net.minecraft.world.item.component.TooltipDisplay.DEFAULT).hideTooltip());
    assertNotNull(stack.get(DataComponents.FIREWORK_EXPLOSION));
    assertNotNull(stack.get(DataComponents.CUSTOM_DATA));
    assertEquals(0, buf.readableBytes());
  }

  @Test
  void mapsComponentIdsThroughThe262Table() {
    // 26.3 moved these two entries, so the runtime registry cannot be used.
    assertSame(DataComponents.FIREWORK_EXPLOSION, LegacyTranslator.componentType(68));
    assertSame(DataComponents.PROFILE, LegacyTranslator.componentType(70));
    assertSame(DataComponents.CUSTOM_DATA, LegacyTranslator.componentType(0));
  }

  private static byte[] unhex(String s) {
    byte[] out = new byte[s.length() / 2];
    for (int i = 0; i < out.length; i++) {
      out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
    }
    return out;
  }
}
