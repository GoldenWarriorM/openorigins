package com.openorigins.client.screen;

import com.openorigins.client.util.ServerPackIcons;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Radial teleport menu with icons loaded at runtime from the server resource pack.
 * No pack pixels are bundled with the mod; transparent margins are trimmed
 * down to the outline edge on load.
 */
public class TeleportMenuScreen extends Screen {
  private record Entry(String labelKey, String command, double angleDeg, String packPath) {}

  private static final Entry[] ENTRIES = {
      new Entry("menu.openorigins.teleport.blue", "balloon blue-balloon", -90.0,
          "textures/custom_ui/transport_menu/button_balloon_blue.png"),
      new Entry("menu.openorigins.teleport.yellow", "balloon yellow-balloon", 0.0,
          "textures/custom_ui/transport_menu/button_balloon_yellow.png"),
      new Entry("menu.openorigins.teleport.green", "balloon green-balloon", 180.0,
          "textures/custom_ui/transport_menu/button_balloon_green.png"),
      new Entry("menu.openorigins.teleport.realm", "realm tp", 120.0,
          "textures/custom_ui/transport_menu/button_homes.png"),
      new Entry("menu.openorigins.teleport.spawn", "spawn", 60.0,
          "textures/custom_ui/transport_menu/button_origin_isles.png"),
  };
  private static final int ICON_SIZE = 56;
  private static final int RADIUS = 95;

  public TeleportMenuScreen() {
    super(Component.translatable("menu.openorigins.teleport.title"));
  }

  @Override
  protected void init() {
    double cx = this.width / 2.0;
    double cy = this.height / 2.0;
    for (Entry entry : ENTRIES) {
      double angle = Math.toRadians(entry.angleDeg);
      int x = (int) Math.round(cx + Math.cos(angle) * RADIUS) - ICON_SIZE / 2;
      int y = (int) Math.round(cy + Math.sin(angle) * RADIUS) - ICON_SIZE / 2;
      Optional<ServerPackIcons.LoadedIcon> icon = ServerPackIcons.get(entry.packPath);
      if (icon.isPresent()) {
        ServerPackIcons.LoadedIcon li = icon.get();
        this.addRenderableWidget(new PackIconButton(x, y, ICON_SIZE,
            Component.translatable(entry.labelKey), li.textureId(), li.width(), li.height(), () -> {
              if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.connection.sendCommand(entry.command);
              }
              this.onClose();
            }));
      } else {
        this.addRenderableWidget(Button.builder(Component.translatable(entry.labelKey), button -> {
          if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.connection.sendCommand(entry.command);
          }
          this.onClose();
        }).bounds(x, y, 96, 20).build());
      }
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

  private static final class PackIconButton extends AbstractButton {
    private final Identifier textureId;
    private final int texW;
    private final int texH;
    private final Runnable action;

    PackIconButton(int x, int y, int size, Component message, Identifier textureId,
        int texW, int texH, Runnable action) {
      super(x, y, size, size, message);
      this.textureId = textureId;
      this.texW = texW;
      this.texH = texH;
      this.action = action;
      this.setTooltip(Tooltip.create(message));
    }

    @Override
    public void onPress(InputWithModifiers input) {
      this.action.run();
    }

    @Override
    protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
      this.defaultButtonNarrationText(output);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      float aspect = this.texW / (float) this.texH;
      int dw = this.getWidth();
      int dh = this.getHeight();
      if (aspect >= 1.0F) {
        dh = Math.max(1, Math.round(dw / aspect));
      } else {
        dw = Math.max(1, Math.round(dh * aspect));
      }
      int dx = this.getX() + (this.getWidth() - dw) / 2;
      int dy = this.getY() + (this.getHeight() - dh) / 2;
      graphics.blit(RenderPipelines.GUI_TEXTURED, this.textureId,
          dx, dy, 0.0F, 0.0F, dw, dh, this.texW, this.texH, this.texW, this.texH);
      if (this.isHoveredOrFocused()) {
        int x0 = this.getX() - 1;
        int y0 = this.getY() - 1;
        int x1 = this.getX() + this.getWidth() + 1;
        int y1 = this.getY() + this.getHeight() + 1;
        graphics.fill(x0, y0, x1, y0 + 1, 0xFFFFFFFF);
        graphics.fill(x0, y1 - 1, x1, y1, 0xFFFFFFFF);
        graphics.fill(x0, y0, x0 + 1, y1, 0xFFFFFFFF);
        graphics.fill(x1 - 1, y0, x1, y1, 0xFFFFFFFF);
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc != null) {
        String s = this.getMessage().getString();
        int tw = mc.font.width(s);
        graphics.text(mc.font, s, this.getX() + this.getWidth() / 2 - tw / 2,
            this.getY() + this.getHeight() + 3, 0xFFFFFFFF);
      }
    }
  }
}
