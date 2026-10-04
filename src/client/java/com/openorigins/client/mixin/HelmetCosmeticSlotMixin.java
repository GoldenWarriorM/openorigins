package com.openorigins.client.mixin;

import com.openorigins.client.ConnectionUtil;
import com.openorigins.client.OpenOriginsConfig;
import com.openorigins.client.OriginFeature;
import com.openorigins.client.util.HelmetCosmeticTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Helmet cosmetic support in the survival inventory:
 * extra slot right of the helmet slot showing the cosmetic,
 * real helmet snapshot drawn over the helmet slot itself.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class HelmetCosmeticSlotMixin {
  private static final Identifier SLOT_SPRITE =
      Identifier.withDefaultNamespace("container/slot");
  private static final Identifier HIGHLIGHT_BACK =
      Identifier.withDefaultNamespace("container/slot_highlight_back");
  private static final Identifier HIGHLIGHT_FRONT =
      Identifier.withDefaultNamespace("container/slot_highlight_front");
  private static final int HELMET_X = 8;
  private static final int HELMET_Y = 8;
  private static final int COSMETIC_X = 30;
  private static final int COSMETIC_Y = 8;

  @Shadow
  protected int leftPos;
  @Shadow
  protected int topPos;

  private boolean openorigins$active() {
    return OpenOriginsConfig.getInstance().isEnabled(OriginFeature.HELMET_COSMETIC_SLOT)
        && ((Object) this) instanceof InventoryScreen
        && ConnectionUtil.isConnected();
  }

  private ItemStack openorigins$wornHelmet() {
    HelmetCosmeticTracker.resolvePending();
    if (!(((Object) this) instanceof InventoryScreen)) {
      return ItemStack.EMPTY;
    }
    try {
      net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> self =
          (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) (Object) this;
      if (self.getMenu() instanceof net.minecraft.world.inventory.InventoryMenu menu) {
        ItemStack stack = menu.getSlot(5).getItem();
        return stack == null ? ItemStack.EMPTY : stack;
      }
    } catch (Throwable ignored) {
    }
    Player player = Minecraft.getInstance().player;
    if (player == null) {
      return ItemStack.EMPTY;
    }
    ItemStack stack = player.getItemBySlot(EquipmentSlot.HEAD);
    return stack == null ? ItemStack.EMPTY : stack;
  }

  /** Cosmetic presence: glyph-named item, or its plain placeholder while combo active. */
  private ItemStack openorigins$cosmetic(ItemStack worn) {
    if (worn.isEmpty()) {
      return ItemStack.EMPTY;
    }
    HelmetCosmeticTracker.offerReal(worn);
    if (HelmetCosmeticTracker.isCosmetic(worn)) {
      return worn;
    }
    if (!HelmetCosmeticTracker.realHelmet().isEmpty()
        && worn.getHoverName().getString().contains("Marauder Helmet")) {
      return worn;
    }
    return ItemStack.EMPTY;
  }

  private boolean openorigins$heldOnCursor() {
    Player player = Minecraft.getInstance().player;
    if (player == null) {
      return false;
    }
    try {
      return HelmetCosmeticTracker.isStashedReal(player.containerMenu.getCarried());
    } catch (Throwable ignored) {
      return false;
    }
  }

  private boolean openorigins$isHoveringCosmetic(int mouseX, int mouseY) {
    return mouseX >= this.leftPos + COSMETIC_X - 1 && mouseX < this.leftPos + COSMETIC_X + 17
        && mouseY >= this.topPos + COSMETIC_Y - 1 && mouseY < this.topPos + COSMETIC_Y + 17;
  }

  @Inject(method = "extractSlots", at = @At("TAIL"))
  private void openorigins$drawHelmetSlots(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
      CallbackInfo ci) {
    if (!this.openorigins$active()) {
      return;
    }
    ItemStack worn = this.openorigins$wornHelmet();
    ItemStack cosmetic = this.openorigins$cosmetic(worn);
    if (cosmetic.isEmpty()) {
      return;
    }
    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, COSMETIC_X - 1, COSMETIC_Y - 1, 18, 18);
    if (this.openorigins$isHoveringCosmetic(mouseX, mouseY)) {
      graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HIGHLIGHT_BACK, COSMETIC_X - 4, COSMETIC_Y - 4, 24, 24);
    }
    graphics.item(cosmetic, COSMETIC_X, COSMETIC_Y);
    graphics.itemDecorations(Minecraft.getInstance().font, cosmetic, COSMETIC_X, COSMETIC_Y);
    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, HELMET_X - 1, HELMET_Y - 1, 18, 18);
    ItemStack real = HelmetCosmeticTracker.realHelmet();
    if (!real.isEmpty() && !this.openorigins$heldOnCursor()) {
      graphics.item(real, HELMET_X, HELMET_Y);
      graphics.itemDecorations(Minecraft.getInstance().font, real, HELMET_X, HELMET_Y);
    }
  }

  @Inject(method = "extractContents", at = @At("TAIL"))
  private void openorigins$drawHelmetHighlight(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
      float partialTick, CallbackInfo ci) {
    if (!this.openorigins$active()) {
      return;
    }
    ItemStack cosmetic = this.openorigins$cosmetic(this.openorigins$wornHelmet());
    if (cosmetic.isEmpty() || !this.openorigins$isHoveringCosmetic(mouseX, mouseY)) {
      return;
    }
    int x = this.leftPos + COSMETIC_X - 4;
    int y = this.topPos + COSMETIC_Y - 4;
    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HIGHLIGHT_FRONT, x, y, 24, 24);
    Minecraft mc = Minecraft.getInstance();
    java.util.List<net.minecraft.network.chat.Component> lines =
        new java.util.ArrayList<>(
            net.minecraft.client.gui.screens.Screen.getTooltipFromItem(mc, cosmetic));
    java.util.List<net.minecraft.util.FormattedCharSequence> seqs = new java.util.ArrayList<>();
    for (net.minecraft.network.chat.Component line : lines) {
      seqs.add(line.getVisualOrderText());
    }
    graphics.setTooltipForNextFrame(mc.font, seqs,
        net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE,
        mouseX, mouseY, true);
  }
}
