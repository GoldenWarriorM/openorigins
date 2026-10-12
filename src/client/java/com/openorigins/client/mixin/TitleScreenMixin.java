package com.openorigins.client.mixin;

import com.openorigins.client.screen.ConfigScreen;
import com.openorigins.client.screen.QuickJoin;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Quick-join button on the title screen, same row style as the pause-menu icon:
 * left-click joins Origin Realms, right-click opens the config.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
  private static final Identifier JOIN_ICON =
      Identifier.withDefaultNamespace("server_list/join");
  private static final Identifier JOIN_ICON_HOVERED =
      Identifier.withDefaultNamespace("server_list/join_highlighted");

  @Unique
  private SpriteIconButton openorigins$joinButton;

  protected TitleScreenMixin() {
    super(Component.empty());
  }

  @Inject(method = "init", at = @At("TAIL"))
  private void openorigins$addJoinButton(CallbackInfo ci) {
    int anchorRight = -1;
    int anchorY = -1;
    for (GuiEventListener child : this.children()) {
      if (child instanceof AbstractWidget widget
          && widget.getWidth() == 20 && widget.getHeight() == 20) {
        int right = widget.getX() + widget.getWidth();
        if (right > anchorRight) {
          anchorRight = right;
          anchorY = widget.getY();
        }
      }
    }
    SpriteIconButton button = SpriteIconButton.builder(
        Component.translatable("button.openorigins.quickjoin"),
        btn -> QuickJoin.join((TitleScreen) (Object) this),
        true)
        .width(20)
        .sprite(new WidgetSprites(JOIN_ICON, JOIN_ICON_HOVERED, JOIN_ICON), 20, 20)
        .tooltip(Component.translatable("button.openorigins.quickjoin_hint"))
        .build();
    if (anchorRight >= 0) {
      button.setX(anchorRight + 4);
      button.setY(anchorY);
    } else {
      button.setX(this.width - 24);
      button.setY(4);
    }
    this.openorigins$joinButton = button;
    this.addRenderableWidget(button);
  }

  @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
  private void openorigins$rightClickConfig(MouseButtonEvent event, boolean doubleClick,
      CallbackInfoReturnable<Boolean> cir) {
    try {
      if (event.button() == 1 && this.openorigins$joinButton != null
          && this.openorigins$joinButton.isMouseOver(event.x(), event.y())) {
        this.minecraft.gui.setScreen(new ConfigScreen((TitleScreen) (Object) this));
        cir.setReturnValue(true);
        cir.cancel();
      }
    } catch (Throwable ignored) {
    }
  }
}
