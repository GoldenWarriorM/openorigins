package com.openorigins.client.mixin;

import com.openorigins.client.ConnectionUtil;
import com.openorigins.client.util.AllayArmsFix;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.ItemDisplayEntityRenderState;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.entity.DisplayRenderer$ItemDisplayRenderer")
public abstract class ItemDisplayArmsFixMixin {
  private static final Set<ItemDisplayEntityRenderState> OPENORIGINS$ARMS =
      Collections.newSetFromMap(new WeakHashMap<>());
  private static final java.util.Map<ItemDisplayEntityRenderState, Float> OPENORIGINS$PARTS =
      Collections.synchronizedMap(new WeakHashMap<>());
  private static final java.util.Map<ItemDisplayEntityRenderState, String> OPENORIGINS$INFO =
      Collections.synchronizedMap(new WeakHashMap<>());
  private static final Set<Float> OPENORIGINS$LOGGED_PARTS =
      Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
  private static final Set<String> OPENORIGINS$LOGGED_SUBMITS =
      Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
  private static final Set<ItemDisplayEntityRenderState> OPENORIGINS$OWN =
      Collections.newSetFromMap(new WeakHashMap<>());
  private static boolean OPENORIGINS$LOGGED;

  @Inject(method = "extractRenderState", at = @At("TAIL"))
  private void openorigins$flagArms(Display.ItemDisplay entity, ItemDisplayEntityRenderState state,
      float partialTick, CallbackInfo ci) {
    if (!ConnectionUtil.isConnected()) {
      return;
    }
    try {
      net.minecraft.world.item.ItemStack shown = null;
      try {
        net.minecraft.world.entity.SlotAccess slot = entity.getSlot(0);
        shown = slot == null ? null : slot.get();
      } catch (Throwable ignored) {
      }
      Float part = AllayArmsFix.partCmd(shown);
      if (AllayArmsFix.isOwnCosmetic(shown)) {
        OPENORIGINS$OWN.add(state);
      } else if (part != null) {
        OPENORIGINS$PARTS.put(state, part);
        OPENORIGINS$INFO.put(state, AllayArmsFix.describeShort(shown));
      } else if (shown != null && !shown.isEmpty()) {
        // Non-part, non-cosmetic items near the player (e.g. torch): still identify.
        OPENORIGINS$INFO.put(state, AllayArmsFix.describeShort(shown));
      }
      // Shift every allay part (arms, wings, ...): server transforms are off
      // for everyone, parts show conditionally (flight/dance/held states).
      if (part != null && !AllayArmsFix.isOwnCosmetic(shown)) {
        OPENORIGINS$ARMS.add(state);
        AllayArmsFix.markFlagged();
        if (!OPENORIGINS$LOGGED) {
          OPENORIGINS$LOGGED = true;
          org.slf4j.LoggerFactory.getLogger("openorigins")
              .info("[allay-arms] fix engaged on {}", AllayArmsFix.describe(shown));
        }
      }
    } catch (Throwable ignored) {
    }
  }

  @Inject(method = "submitInner", at = @At("HEAD"))
  private void openorigins$censusSubmit(ItemDisplayEntityRenderState state, PoseStack poseStack,
      SubmitNodeCollector submitNodeCollector, int lightCoords, float interpolationProgress,
      CallbackInfo ci) {
    // One-time log of every nearby NON-COSMETIC display submit.
    try {
      if (OPENORIGINS$OWN.remove(state)) {
        return;
      }
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      if (mc != null && mc.player != null && OPENORIGINS$LOGGED_SUBMITS.size() < 200) {
        double dx = state.x - mc.player.getX();
        double dz = state.z - mc.player.getZ();
        if (dx * dx + dz * dz < 12 * 12) {
          String info = OPENORIGINS$INFO.remove(state);
          String key = String.format(java.util.Locale.ROOT, "%.1f,%.1f,%.1f:%s", state.x,
              state.y, state.z, state.item.isEmpty() ? "empty" : info);
          if (OPENORIGINS$LOGGED_SUBMITS.add(key)) {
            org.slf4j.LoggerFactory.getLogger("openorigins")
                .info("[allay-submit] {} {} at [{},{},{}]",
                    state.item.isEmpty() ? "empty" : "full", info == null ? "?" : info,
                    String.format(java.util.Locale.ROOT, "%.2f", state.x),
                    String.format(java.util.Locale.ROOT, "%.2f", state.y),
                    String.format(java.util.Locale.ROOT, "%.2f", state.z));
          }
        }
      }
    } catch (Throwable ignored) {
    }
  }

  @Inject(method = "submitInner",
      at = @At(value = "INVOKE",
          target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
  private void openorigins$shiftArms(ItemDisplayEntityRenderState state, PoseStack poseStack,
      SubmitNodeCollector submitNodeCollector, int lightCoords, float interpolationProgress,
      CallbackInfo ci) {
    Float part = OPENORIGINS$PARTS.remove(state);
    if (part != null && OPENORIGINS$LOGGED_PARTS.add(part)) {
      org.slf4j.LoggerFactory.getLogger("openorigins").info(
          "[allay-render] part cmd={} at [{},{},{}]", part,
          String.format(java.util.Locale.ROOT, "%.2f", state.x),
          String.format(java.util.Locale.ROOT, "%.2f", state.y),
          String.format(java.util.Locale.ROOT, "%.2f", state.z));
    }
    if (!OPENORIGINS$ARMS.remove(state)) {
      return;
    }
    if (!com.openorigins.client.OpenOriginsConfig.getInstance()
        .isEnabled(com.openorigins.client.OriginFeature.ALLAY_ARMS_FIX)) {
      return;
    }
    float dx = AllayArmsFix.shiftBlocks();
    if (dx != 0.0f) {
      poseStack.translate(dx, 0.0f, 0.0f);
    }
  }
}
