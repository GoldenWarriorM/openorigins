package com.openorigins.client.mixin;

import com.openorigins.client.screen.ConfigScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Config icon in the report bugs / feedback / friends / player reporting row. */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
  private static final Identifier ICON_NORMAL =
      Identifier.fromNamespaceAndPath("openorigins", "config_button");
  private static final Identifier ICON_FOCUSED =
      Identifier.fromNamespaceAndPath("openorigins", "config_button_focused");

  protected PauseScreenMixin() {
    super(Component.empty());
  }

  @Inject(method = "init", at = @At("TAIL"), require = 0)
  private void openorigins$addConfigIcon(CallbackInfo ci) {
    try {
      if (!com.openorigins.client.OpenOriginsConfig.getInstance().isPauseConfigButton()) {
        return;
      }
    } catch (Throwable ignored) {
    }
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
    if (anchorRight < 0) return;
    SpriteIconButton button = SpriteIconButton.builder(
        Component.translatable("button.openorigins.config"),
        btn -> this.minecraft.gui.setScreen(new ConfigScreen((PauseScreen) (Object) this)),
        true)
        .width(20)
        .sprite(new WidgetSprites(ICON_NORMAL, ICON_NORMAL, ICON_FOCUSED), 20, 20)
        .withTootip()
        .build();
    button.setX(anchorRight + 4);
    button.setY(anchorY);
    this.addRenderableWidget(button);
    com.openorigins.client.screen.IconRow.addCentered(this, button);
  }
}
