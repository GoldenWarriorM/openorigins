package com.openorigins.client.mixin;

import com.openorigins.client.ConnectionUtil;
import com.openorigins.client.OpenOriginsConfig;
import com.openorigins.client.OriginFeature;
import com.openorigins.client.server.EntityData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Visual back-cosmetic slot in the survival inventory (over the offhand position 77,44):
 * frame + item + highlight + tooltip. Plain blit, no shaders.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class BackSwagSlotMixin {
  private static boolean openorigins$tipLogged;
  private static final Identifier SLOT_SPRITE =
      Identifier.withDefaultNamespace("container/slot");
  private static final Identifier HIGHLIGHT_BACK =
      Identifier.withDefaultNamespace("container/slot_highlight_back");
  private static final Identifier HIGHLIGHT_FRONT =
      Identifier.withDefaultNamespace("container/slot_highlight_front");
  private static final int SLOT_X = 77;
  private static final int SLOT_Y = 44;

  @Shadow
  protected int leftPos;
  @Shadow
  protected int topPos;

  private ItemStack openorigins$backStack() {    if (!OpenOriginsConfig.getInstance().isEnabled(OriginFeature.BACK_SWAG_SLOT)) {
      return ItemStack.EMPTY;
    }
    if (!(((Object) this) instanceof InventoryScreen) || !ConnectionUtil.isConnected()) {
      return ItemStack.EMPTY;
    }
    Player player = Minecraft.getInstance().player;
    if (player == null) {
      return ItemStack.EMPTY;
    }
    ItemStack stack = EntityData.getBackItem(player.getId());
    return stack == null ? ItemStack.EMPTY : stack;
  }

  private static boolean openorigins$iconSkipped;

  private static net.minecraft.network.chat.Component collapseLeadingIconDupes(
      net.minecraft.network.chat.Component line) {
    openorigins$iconSkipped = false;
    String[] firstTwo = firstTwoIconLeaves(line, new java.util.ArrayList<>());
    if (firstTwo == null) {
      return line;
    }
    net.minecraft.network.chat.Component out = dedupeSecondIcon(line, firstTwo[0]);
    return out == null ? line : out;
  }

  private static String[] firstTwoIconLeaves(net.minecraft.network.chat.Component node,
      java.util.List<String> acc) {
    if (node.getSiblings().isEmpty()) {
      String text = node.getString().strip();
      if (text.length() == 1 && text.charAt(0) > 0x2000) {
        acc.add(text);
      }
      return null;
    }
    for (net.minecraft.network.chat.Component child : node.getSiblings()) {
      firstTwoIconLeaves(child, acc);
      if (acc.size() >= 2) {
        break;
      }
    }
    if (acc.size() >= 2 && acc.get(0).equals(acc.get(1))) {
      return new String[]{acc.get(0)};
    }
    return null;
  }

  private static net.minecraft.network.chat.Component dedupeSecondIcon(
      net.minecraft.network.chat.Component node, String icon) {
    if (node.getSiblings().isEmpty()) {
      String text = node.getString().strip();
      if (!openorigins$iconSkipped && text.length() == 1 && text.equals(icon)) {
        openorigins$iconSkipped = true;
        return null;
      }
      return node;
    }
    if (!(node instanceof net.minecraft.network.chat.MutableComponent mutable)) {
      return node;
    }
    net.minecraft.network.chat.MutableComponent copy =
        net.minecraft.network.chat.Component.empty();
    copy.setStyle(node.getStyle());
    for (net.minecraft.network.chat.Component child : new java.util.ArrayList<>(node.getSiblings())) {
      net.minecraft.network.chat.Component mapped = dedupeSecondIcon(child, icon);
      if (mapped != null) {
        copy.append(mapped);
      } else {
        for (net.minecraft.network.chat.Component grand : child.getSiblings()) {
          copy.append(dedupeSecondIcon(grand, icon));
        }
      }
    }
    return copy;
  }

  private static void dumpTree(net.minecraft.network.chat.Component node, int depth) {
    StringBuilder indent = new StringBuilder();
    for (int i = 0; i < depth; i++) {
      indent.append("  ");
    }
    com.openorigins.client.util.DebugLog.info("[OpenOrigins] name tree: {}{} style={}",
        indent, escape(node.getString()), node.getStyle());
    for (net.minecraft.network.chat.Component child : node.getSiblings()) {
      dumpTree(child, depth + 1);
    }
  }

  private static String escape(String s) {
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c < 0x20 || c > 0x2000) {
        out.append(String.format("\\u%04x", (int) c));
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }

  private boolean openorigins$isHovering(int mouseX, int mouseY) {    return mouseX >= this.leftPos + SLOT_X - 1 && mouseX < this.leftPos + SLOT_X + 17
        && mouseY >= this.topPos + SLOT_Y - 1 && mouseY < this.topPos + SLOT_Y + 17;
  }

  @Inject(method = "extractSlots", at = @At("TAIL"), require = 0)
  private void openorigins$drawSlot(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
      CallbackInfo ci) {
    ItemStack stack = this.openorigins$backStack();
    if (stack.isEmpty()) {
      return;
    }
    com.openorigins.client.util.VersionCompat.blitSprite(graphics, SLOT_SPRITE, SLOT_X - 1, SLOT_Y - 1, 18, 18);
    if (this.openorigins$isHovering(mouseX, mouseY)) {
      com.openorigins.client.util.VersionCompat.blitSprite(graphics, HIGHLIGHT_BACK, SLOT_X - 4, SLOT_Y - 4, 24, 24);
    }
    graphics.item(stack, SLOT_X, SLOT_Y);
    graphics.itemDecorations(Minecraft.getInstance().font, stack, SLOT_X, SLOT_Y);
  }

  @Inject(method = "extractContents", at = @At("TAIL"), require = 0)
  private void openorigins$drawHighlight(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
      float partialTick, CallbackInfo ci) {
    ItemStack stack = this.openorigins$backStack();
    if (stack.isEmpty() || !this.openorigins$isHovering(mouseX, mouseY)) {
      return;
    }
    int x = this.leftPos + SLOT_X - 4;
    int y = this.topPos + SLOT_Y - 4;
    com.openorigins.client.util.VersionCompat.blitSprite(graphics, HIGHLIGHT_FRONT, x, y, 24, 24);
    Minecraft mc = Minecraft.getInstance();
    java.util.List<net.minecraft.network.chat.Component> lines =
        new java.util.ArrayList<>(
            net.minecraft.client.gui.screens.Screen.getTooltipFromItem(mc, stack));
    lines.removeIf(line -> {
      String text = line.getString().trim();
      return text.equals("Small Ball") || text.equals("Custom");
    });
    if (!lines.isEmpty()) {
      lines.set(0, collapseLeadingIconDupes(lines.get(0)));
    }
    if (!openorigins$tipLogged) {
      openorigins$tipLogged = true;
      StringBuilder sb = new StringBuilder();
      for (net.minecraft.network.chat.Component line : lines) {
        sb.append('[').append(line.getString()).append(']');
      }
      com.openorigins.client.util.DebugLog.info("[OpenOrigins] slot tooltip lines: {}", sb);
      if (!lines.isEmpty()) {
        dumpTree(lines.get(0), 0);
      }
    }
      java.util.List<net.minecraft.util.FormattedCharSequence> seqs = new java.util.ArrayList<>();
      for (net.minecraft.network.chat.Component line : lines) {
        seqs.add(line.getVisualOrderText());
      }
      graphics.setTooltipForNextFrame(mc.font, seqs,
          net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE,
          mouseX, mouseY, true);
  }
}
