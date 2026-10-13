package com.openorigins.client.mixin;

import com.openorigins.client.OpenOriginsConfig;
import com.openorigins.client.OriginFeature;
import com.openorigins.client.screen.OaEmojiPanel;
import com.openorigins.client.util.OaEmojiStore;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Chat emoji grid (OriginAddons-style, JSON-driven). */
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

  @Inject(method = "init", at = @At("TAIL"))
  private void openorigins$addEmojiButton(CallbackInfo ci) {
    openorigins$emojiPanel = null;
    if (!OpenOriginsConfig.getInstance().isEnabled(OriginFeature.OA_EMOJI_PICKER)) return;
    if (input == null) return;
    int btnX = input.getX();
    int btnY0 = input.getY() - 22;
    if (btnY0 < 2) btnY0 = 2;
    final int btnY = btnY0;
    Button button = Button.builder(Component.literal("\u263A"), btn -> {
      if (openorigins$emojiPanel != null) {
        this.removeWidget(openorigins$emojiPanel);
        openorigins$emojiPanel = null;
      } else {
        int w = OaEmojiPanel.panelWidth();
        int h = OaEmojiPanel.panelHeight();
        int px = btnX;
        if (px + w > this.width - 2) px = this.width - 2 - w;
        if (px < 2) px = 2;
        int py = btnY - h - 2;
        if (py < 2) py = 2;
        openorigins$emojiPanel = new OaEmojiPanel(input, px, py);
        this.addRenderableWidget(openorigins$emojiPanel);
      }
    }).bounds(btnX, btnY, 20, 20).build();
    this.addRenderableWidget(button);
  }

  @Inject(method = "onEdited", at = @At("TAIL"))
  private void openorigins$autoconvert(String value, CallbackInfo ci) {
    if (openorigins$converting) return;
    if (!OpenOriginsConfig.getInstance().isEnabled(OriginFeature.OA_EMOJI_PICKER)) return;
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

  @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
  private void openorigins$closeEmojiFirst(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
    if (openorigins$emojiPanel != null && event.key() == 256) {
      this.removeWidget(openorigins$emojiPanel);
      openorigins$emojiPanel = null;
      cir.setReturnValue(true);
      cir.cancel();
    }
  }
}
