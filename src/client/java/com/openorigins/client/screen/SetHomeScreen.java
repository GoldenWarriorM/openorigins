package com.openorigins.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Name input for a new home; Done sends {@code /sethome <name>}. */
public class SetHomeScreen extends Screen {
  private EditBox nameBox;

  public SetHomeScreen() {
    super(Component.translatable("menu.openorigins.sethome.title"));
  }

  @Override
  protected void init() {
    int cx = this.width / 2;
    this.nameBox = new EditBox(this.font, cx - 100, this.height / 2 - 10, 200, 20,
        Component.translatable("menu.openorigins.sethome.title"));
    this.nameBox.setMaxLength(32);
    this.nameBox.setResponder(s -> {
      String filtered = s.replaceAll("[^a-zA-Z0-9_]", "");
      if (!filtered.equals(s)) {
        this.nameBox.setValue(filtered);
      }
    });
    this.addRenderableWidget(this.nameBox);
    this.setFocused(this.nameBox);
    this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.doDone())
        .bounds(cx - 105, this.height / 2 + 20, 100, 20).build());
    this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> this.doCancel())
        .bounds(cx + 5, this.height / 2 + 20, 100, 20).build());
  }

  private void doDone() {
    String name = this.nameBox == null ? "" : this.nameBox.getValue().trim().replaceAll("[^a-zA-Z0-9_]", "");
    if (!name.isEmpty() && this.minecraft != null && this.minecraft.player != null) {
      this.minecraft.player.connection.sendCommand("sethome " + name);
      HomesMenuScreen.requestRefetch();
      this.minecraft.gui.setScreen(new HomesMenuScreen());
    }
  }

  private void doCancel() {
    if (this.minecraft != null) {
      this.minecraft.gui.setScreen(new HomesMenuScreen());
    }
  }

  @Override
  public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
    if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN
        || event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER) {
      this.doDone();
      return true;
    }
    return super.keyPressed(event);
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
    extractor.fill(0, 0, this.width, this.height, 0xA0000000);
    super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    int tw = this.font.width(this.title);
    extractor.text(this.font, this.title, this.width / 2 - tw / 2, this.height / 2 - 30, 0xFFFFFFFF);
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  public void onClose() {
    this.doCancel();
  }
}
