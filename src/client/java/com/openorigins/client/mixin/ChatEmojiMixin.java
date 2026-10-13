package com.openorigins.client.mixin;

import com.openorigins.client.OpenOriginsConfig;
import com.openorigins.client.OriginFeature;
import com.openorigins.client.screen.EmojiPanel;
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

/** Smiley button in chat that toggles our own emoji grid. */
@Mixin(ChatScreen.class)
public abstract class ChatEmojiMixin extends net.minecraft.client.gui.screens.Screen {
  protected ChatEmojiMixin(Component title) {
    super(title);
  }

  @Shadow
  protected EditBox input;

  @Unique
  private EmojiPanel openorigins$emojiPanel;

  @Unique
  private Button openorigins$emojiButton;

  @Inject(method = "init", at = @At("TAIL"))
  private void openorigins$addEmojiButton(CallbackInfo ci) {
    openorigins$emojiPanel = null;
    if (!OpenOriginsConfig.getInstance().isEnabled(OriginFeature.EMOJI_PICKER)) return;
    if (input == null) return;
    input.setWidth(input.getWidth() - 22);
    openorigins$emojiButton = Button.builder(Component.literal("\u263A"), btn -> {
      if (openorigins$emojiPanel != null) {
        this.removeWidget(openorigins$emojiPanel);
        openorigins$emojiPanel = null;
      } else {
        int w = EmojiPanel.COLS * EmojiPanel.CELL + EmojiPanel.PAD * 2;
        int h = EmojiPanel.HEADER + EmojiPanel.ROWS * EmojiPanel.CELL + EmojiPanel.PAD * 2;
        int px = input.getX() + input.getWidth() + 2 - w;
        if (px < 2) px = 2;
        int py = input.getY() - h - 2;
        if (py < 2) py = 2;
        openorigins$emojiPanel = new EmojiPanel(input, px, py);
        this.addWidget(openorigins$emojiPanel);
      }
    }).bounds(input.getX() + input.getWidth() + 2, input.getY(), 20, 20).build();
    this.addRenderableWidget(openorigins$emojiButton);
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
