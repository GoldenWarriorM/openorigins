package com.openorigins.client.screen;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Radial teleport menu: entries arranged in a circle around the cursor point.
 * Click an entry to run its command, Esc to cancel.
 */
public class TeleportMenuScreen extends Screen {
  private record Entry(String labelKey, String command, double angleDeg) {}

  private static final Entry[] ENTRIES = {
      new Entry("menu.openorigins.teleport.blue", "balloon blue-balloon", -90.0),
      new Entry("menu.openorigins.teleport.yellow", "balloon yellow-balloon", 0.0),
      new Entry("menu.openorigins.teleport.red", "balloon red-balloon", 90.0),
      new Entry("menu.openorigins.teleport.green", "balloon green-balloon", 180.0),
      new Entry("menu.openorigins.teleport.realm", "realm tp", 135.0),
      new Entry("menu.openorigins.teleport.spawn", "spawn", 45.0),
  };
  private static final int BUTTON_W = 96;
  private static final int BUTTON_H = 20;
  private static final int RADIUS = 95;

  public TeleportMenuScreen() {
    super(Component.translatable("menu.openorigins.teleport.title"));
  }

  @Override
  protected void init() {
    double cx = this.width / 2.0;
    double cy = this.height / 2.0;
    for (int i = 0; i < ENTRIES.length; i++) {
      Entry entry = ENTRIES[i];
      double angle = Math.toRadians(entry.angleDeg);
      int x = (int) Math.round(cx + Math.cos(angle) * RADIUS) - BUTTON_W / 2;
      int y = (int) Math.round(cy + Math.sin(angle) * RADIUS) - BUTTON_H / 2;
      this.addRenderableWidget(Button.builder(Component.translatable(entry.labelKey), button -> {
        if (this.minecraft != null && this.minecraft.player != null) {
          this.minecraft.player.connection.sendCommand(entry.command);
        }
        this.onClose();
      }).bounds(x, y, BUTTON_W, BUTTON_H).build());
    }
  }

  @Override
  public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor extractor,
      int mouseX, int mouseY, float partialTick) {
    extractor.fill(0, 0, this.width, this.height, 0xA0000000);
    super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    int titleWidth = this.font.width(this.title);
    extractor.text(this.font, this.title, this.width / 2 - titleWidth / 2, 20, 0xFFFFFFFF);
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  public void onClose() {
    if (this.minecraft != null) {
      this.minecraft.gui.setScreen(null);
    }
  }
}
