package com.openorigins.client;

import com.openorigins.client.util.OaRocketFuel;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Rocket-boots fuel bar above the armor HUD (original texture + position). */
public final class RocketFuelHud implements net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement {
  public static final Identifier ID = Identifier.fromNamespaceAndPath("openorigins", "rocket_fuel");
  private static final Identifier TEX =
      Identifier.fromNamespaceAndPath("originaddons", "textures/gui/rocket_boots_fuel_bar.png");
  private static final int BAR_H = 30;

  @Override
  public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
    if (!OpenOriginsConfig.getInstance().isEnabled(OriginFeature.OA_ROCKET_FUEL_HUD)) return;
    Minecraft mc = Minecraft.getInstance();
    if (mc == null || mc.player == null || mc.level == null) return;
    ItemStack boots = OaRocketFuel.wornBoots(mc.player);
    if (boots.isEmpty()) return;
    String id = OaRocketFuel.shortId(boots);
    int max = OaRocketFuel.maxFuel(id);
    if (max <= 0) return;
    int cur = OaRocketFuel.currentFuel(boots);
    try {
      if (!mc.player.getAbilities().mayfly && cur == 1) cur = 0;
    } catch (Throwable ignored) {
    }
    int w = graphics.guiWidth();
    int h = mc.getWindow().getGuiScaledHeight();
    int barX = (w / 2 - 5 / 2) + 96;
    int barY = h - BAR_H - 4;
    if (cur <= 2 && cur > 0) {
      java.util.Random r = new java.util.Random();
      barX += r.nextInt(2) - 1;
      barY += r.nextInt(2);
    }
    int fill = Math.max(0, Math.min(BAR_H, cur));
    graphics.blit(RenderPipelines.GUI_TEXTURED, TEX, barX, barY,
        0.0f, 0.0f, 5, BAR_H + 1 - fill, 10, BAR_H + 2);
    graphics.blit(RenderPipelines.GUI_TEXTURED, TEX, barX, barY + BAR_H + 1 - fill,
        5.0f, (float) (BAR_H + 1 - fill), 5, 1 + fill, 10, BAR_H + 2);
  }
}
