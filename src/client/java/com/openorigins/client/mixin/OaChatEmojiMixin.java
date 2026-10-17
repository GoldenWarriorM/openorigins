package com.openorigins.client.mixin;

import com.openorigins.client.OpenOriginsConfig;
import com.openorigins.client.OriginFeature;
import com.openorigins.client.screen.OaEmojiPanel;
import com.openorigins.client.screen.OaEmojiToggle;
import com.openorigins.client.util.OaEmojiStore;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Chat emoji box 1:1 with the original (texture, positions, sounds, tooltip). */
@Mixin(ChatScreen.class)
public abstract class OaChatEmojiMixin extends net.minecraft.client.gui.screens.Screen {
  protected OaChatEmojiMixin(Component title) {
    super(title);
  }

  @Shadow
  protected EditBox input;

  @Unique
  private OaEmojiPanel openorigins$emojiPanel;

  @Unique
  private boolean openorigins$converting;

  @Unique
  private boolean openorigins$emojiOn() {
    try {
      return OpenOriginsConfig.getInstance().isEnabled(OriginFeature.OA_EMOJI_PICKER);
    } catch (Throwable t) {
      return false;
    }
  }

  @Inject(method = "init", at = @At("TAIL"), require = 0)
  private void openorigins$addEmojiButton(CallbackInfo ci) {
    openorigins$emojiPanel = null;
    if (!openorigins$emojiOn()) return;
    this.addRenderableWidget(new OaEmojiToggle(this.height, () -> {
      if (openorigins$emojiPanel != null) {
        this.removeWidget(openorigins$emojiPanel);
        openorigins$emojiPanel = null;
      } else if (input != null) {
        openorigins$emojiPanel = new OaEmojiPanel(input, this.height);
        this.addRenderableWidget(openorigins$emojiPanel);
      }
    }));
  }

  @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
  private void openorigins$blockHudClicks(MouseButtonEvent event, boolean doubleClick,
      CallbackInfoReturnable<Boolean> cir) {
    if (openorigins$emojiPanel != null && openorigins$emojiPanel.isMouseOver(event.x(), event.y())) {
      cir.setReturnValue(openorigins$emojiPanel.mouseClicked(event, doubleClick));
      cir.cancel();
    }
  }

  @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true, require = 0)
  private void openorigins$scrollBox(double mouseX, double mouseY, double scrollX, double scrollY,
      CallbackInfoReturnable<Boolean> cir) {
    if (openorigins$emojiPanel != null && openorigins$emojiPanel.isMouseOver(mouseX, mouseY)) {
      openorigins$emojiPanel.scrollBy(scrollY);
      cir.setReturnValue(true);
      cir.cancel();
    }
  }

  @Unique
  private int openorigins$mouseX;
  @Unique
  private int openorigins$mouseY;

  @Unique
  private boolean openorigins$panelHovered() {
    if (openorigins$emojiPanel == null) return false;
    try {
      return openorigins$emojiPanel.isMouseOver(openorigins$mouseX, openorigins$mouseY);
    } catch (Throwable t) {
      return false;
    }
  }

  @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
      at = @At("HEAD"), require = 0)
  private void openorigins$trackMouse(net.minecraft.client.gui.GuiGraphicsExtractor graphics,
      int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
    openorigins$mouseX = mouseX;
    openorigins$mouseY = mouseY;
  }

  /** Blinds chat-history hover while the emoji box is hovered (no bleed-through). */
  @ModifyArgs(method = "extractRenderState", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/client/gui/components/ChatComponent;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V"), require = 0)
  private void openorigins$blindHistoryHover(Args args) {
    if (openorigins$panelHovered()) {
      args.set(3, -999);
      args.set(4, -999);
    }
  }

  @Inject(method = "onEdited", at = @At("TAIL"), require = 0)
  private void openorigins$autoconvert(String value, CallbackInfo ci) {
    if (openorigins$converting) return;
    if (!openorigins$emojiOn()) return;
    if (input == null || !input.isFocused()) return;
    String converted = OaEmojiStore.replaceKnown(value);
    if (converted.equals(value)) return;
    int cursor = input.getCursorPosition();
    int delta = 0;
    for (String code : OaEmojiStore.findCodes(value)) {
      String glyph = OaEmojiStore.displayFor(code);
      if (glyph == null) continue;
      String token = ":" + code + ":";
      int at = value.indexOf(token);
      if (at >= 0 && at + token.length() <= cursor) delta += token.length() - glyph.length();
    }
    openorigins$converting = true;
    try {
      input.setValue(converted);
      input.setCursorPosition(Math.max(0, cursor - delta));
    } finally {
      openorigins$converting = false;
    }
  }

  @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 0)
  private void openorigins$closeEmojiFirst(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
    if (openorigins$emojiPanel != null
        && com.openorigins.client.util.VersionCompat.isEscape(event.key())) {
      this.removeWidget(openorigins$emojiPanel);
      openorigins$emojiPanel = null;
      cir.setReturnValue(true);
      cir.cancel();
    }
  }
}
