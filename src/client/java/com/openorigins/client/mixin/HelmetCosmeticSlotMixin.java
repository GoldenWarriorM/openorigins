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
  private static final int COSMETIC_X = 77;
  private static final int COSMETIC_Y = 8;
  private static final Identifier EMPTY_HELMET =
      Identifier.withDefaultNamespace("container/slot/helmet");

  @Shadow
  protected int leftPos;
  @Shadow
  protected int topPos;
  @Shadow
  protected net.minecraft.world.inventory.Slot hoveredSlot;

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

  /** Cosmetic presence: glyph-named item, or the server firework placeholder. */
  private ItemStack openorigins$cosmetic(ItemStack worn) {
    if (worn.isEmpty()) {
      return ItemStack.EMPTY;
    }
    HelmetCosmeticTracker.offerReal(worn);
    try {
      Player player = Minecraft.getInstance().player;
      if (player != null) {
        HelmetCosmeticTracker.offerReal(player.containerMenu.getCarried(), true);
        HelmetCosmeticTracker.offerReal(player.getMainHandItem(), true);
      }
    } catch (Throwable ignored) {
    }
    if (HelmetCosmeticTracker.isCosmetic(worn)) {
      return worn;
    }
    String id = net.minecraft.core.registries.BuiltInRegistries.ITEM
        .getKey(worn.getItem()).toString();
    if (id.equals("minecraft:firework_star")) {
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
    boolean showReal = !real.isEmpty() && !this.openorigins$heldOnCursor()
        && !HelmetCosmeticTracker.isAvailableElsewhere();
    if (showReal) {
      graphics.item(real, HELMET_X, HELMET_Y);
      graphics.itemDecorations(Minecraft.getInstance().font, real, HELMET_X, HELMET_Y);
    } else {
      graphics.blitSprite(RenderPipelines.GUI_TEXTURED, EMPTY_HELMET, HELMET_X, HELMET_Y, 16, 16);
    }
  }

  private static java.util.List<net.minecraft.network.chat.Component> cosmeticTip;
  private static String cosmeticTipKey = "";
  private static final java.util.Map<String, net.minecraft.network.chat.Component> CLEAN_NAMES =
      new java.util.HashMap<>();

  private static boolean openorigins$isHoveringHelmet(HelmetCosmeticSlotMixin self, int mouseX, int mouseY) {
    return mouseX >= self.leftPos + HELMET_X - 1 && mouseX < self.leftPos + HELMET_X + 17
        && mouseY >= self.topPos + HELMET_Y - 1 && mouseY < self.topPos + HELMET_Y + 17;
  }

  /** True while we wiped the helmet slot (its vanilla tooltip must stay hidden). */
  private boolean openorigins$wiped() {
    if (!this.openorigins$active()) {
      return false;
    }
    ItemStack worn = this.openorigins$wornHelmet();
    return !this.openorigins$cosmetic(worn).isEmpty();
  }

  @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
  private void openorigins$hideWipedTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
      CallbackInfo ci) {
    if (this.hoveredSlot == null || this.hoveredSlot.index != 5) {
      return;
    }
    if (this.openorigins$wiped()) {
      ci.cancel();
    }
  }

  @Inject(method = "extractContents", at = @At("TAIL"))
  private void openorigins$drawHelmetHighlight(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
      float partialTick, CallbackInfo ci) {
    if (!this.openorigins$active()) {
      return;
    }
    Minecraft mc = Minecraft.getInstance();
    if (mc == null) {
      return;
    }
    ItemStack cosmetic = this.openorigins$cosmetic(this.openorigins$wornHelmet());
    if (cosmetic.isEmpty()) {
      cosmeticTip = null;
      cosmeticTipKey = "";
    } else if (this.openorigins$isHoveringCosmetic(mouseX, mouseY)) {
      int x = this.leftPos + COSMETIC_X - 4;
      int y = this.topPos + COSMETIC_Y - 4;
      graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HIGHLIGHT_FRONT, x, y, 24, 24);
      String key = HelmetCosmeticTracker.cosmeticKey(cosmetic);
      if (cosmeticTip == null || !key.equals(cosmeticTipKey)) {
        java.util.List<net.minecraft.network.chat.Component> memo =
            com.openorigins.client.util.CosmeticCatalogCache.cleanFor(cosmetic);
        if (memo == null
            && com.openorigins.client.util.CosmeticCatalogCache.shouldFetchWorn()) {
          net.minecraft.world.entity.player.Player p = mc.player;
          com.openorigins.client.util.CosmeticCatalogCache.fetchWorn(
              () -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(p));
        }
        org.slf4j.LoggerFactory.getLogger("openorigins").info("[cosmetic-tip] memo {} for '{}'",
            memo == null ? "MISS" : ("HIT size=" + memo.size()),
            com.openorigins.client.util.CosmeticCatalogCache.normName(
                cosmetic.getHoverName().getString()));
        java.util.List<net.minecraft.network.chat.Component> base;
        if (memo != null) {
          base = memo;
        } else {
          base = net.minecraft.client.gui.screens.Screen.getTooltipFromItem(mc, cosmetic);
        }
        cosmeticTip = new java.util.ArrayList<>();
        for (net.minecraft.network.chat.Component line : base) {
          String s = line.getString().strip();
          int i = 0;
          while (i < s.length() && (s.charAt(i) <= 0x20 || s.charAt(i) > 0x2000)) {
            i++;
          }
          String t = s.substring(i);
          if (!t.isEmpty() && (t.startsWith("Left-click") || t.startsWith("Right-click")
              || t.startsWith("Shift-click") || t.startsWith("Middle-click"))) {
            continue;
          }
          if (t.isEmpty() && !cosmeticTip.isEmpty()) {
            continue;
          }
          cosmeticTip.add(line);
        }
        cosmeticTipKey = key;
      }
      this.openorigins$showTip(graphics, mc, mouseX, mouseY, cosmeticTip);
    }
    if (openorigins$isHoveringHelmet(this, mouseX, mouseY) && !cosmetic.isEmpty()) {
      java.util.List<net.minecraft.network.chat.Component> tip = HelmetCosmeticTracker.realTip();
      if (tip != null && !tip.isEmpty()) {
        this.openorigins$showTip(graphics, mc, mouseX, mouseY, tip);
      }
    }
  }

  private void openorigins$showTip(GuiGraphicsExtractor graphics, Minecraft mc, int mouseX, int mouseY,
      java.util.List<net.minecraft.network.chat.Component> lines) {
    java.util.List<net.minecraft.util.FormattedCharSequence> seqs = new java.util.ArrayList<>();
    for (net.minecraft.network.chat.Component line : lines) {
      seqs.add(line.getVisualOrderText());
    }
    graphics.setTooltipForNextFrame(mc.font, seqs,
        net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE,
        mouseX, mouseY, true);
  }
}
