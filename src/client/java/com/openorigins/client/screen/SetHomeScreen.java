package com.openorigins.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Name input for a new home. Done sends {@code /sethome <name>} and waits
 * for the server verdict; errors show under the buttons, success returns.
 */
public class SetHomeScreen extends Screen {
  /** Where to return after Done/Cancel. */
  public static java.util.function.Supplier<Screen> returnTo = TeleportMenuScreen::new;

  private static volatile SetHomeScreen active;

  private EditBox nameBox;
  private String draft;
  private boolean waiting;
  private long sentAt;
  private Component status;
  private int statusColor = 0xFF555555;

  public SetHomeScreen() {
    super(Component.translatable("menu.openorigins.sethome.title"));
  }

  /** Server verdict for the pending creation. Runs on any thread. */
  public static void onServerResult(boolean ok, String detail) {
    SetHomeScreen screen = active;
    if (screen == null || !screen.waiting) {
      return;
    }
    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
    if (mc != null) {
      mc.execute(() -> screen.applyResult(ok, detail));
    }
  }

  @Override
  protected void init() {
    active = this;
    int cx = this.width / 2;
    this.nameBox = new LatinNameBox(this.font, cx - 100, this.height / 2 - 10, 200, 20,
        Component.translatable("menu.openorigins.sethome.title"));
    this.nameBox.setMaxLength(32);
    if (this.draft != null) {
      // Survives re-init on window resize.
      this.nameBox.setValue(this.draft);
    }
    this.nameBox.setResponder(s -> {
      String filtered = s.replaceAll("[^a-zA-Z0-9_]", "");
      if (!filtered.equals(s)) {
        this.nameBox.setValue(filtered);
      }
      this.draft = this.nameBox.getValue();
    });
    this.addRenderableWidget(this.nameBox);
    this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.doDone())
        .bounds(cx - 105, this.height / 2 + 20, 100, 20).build());
    this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> this.doCancel())
        .bounds(cx + 5, this.height / 2 + 20, 100, 20).build());
    this.focusNameBox();
  }

  /**
   * Vanilla runs this after every init/rebuild, and its tab navigation moves the
   * caret on to the next widget (the Done button). The name box is the whole
   * point of this menu, so it takes the focus back there.
   */
  @Override
  protected void setInitialFocus() {
    if (this.nameBox == null) {
      this.setFocused(null);
      return;
    }
    this.focusNameBox();
  }

  /** Focuses the field the way EditBox understands it: its own flag drives the caret. */
  private void focusNameBox() {
    this.setFocused(this.nameBox);
    this.nameBox.setFocused(true);
    this.nameBox.moveCursorToEnd(false);
  }

  /**
   * Name box that reads a Cyrillic layout as the QWERTY keys in the same places
   * (йцукен -> qwerty), so a name can be typed without switching the layout.
   */
  private static final class LatinNameBox extends EditBox {
    /** ЙЦУКЕН rows; every letter sits on the Latin key at the same index. */
    private static final String RU = "йцукенгшщзхъфывапролджэячсмитьбюё";
    private static final String EN = "qwertyuiop[]asdfghjkl;'zxcvbnm,.`";

    LatinNameBox(net.minecraft.client.gui.Font font, int x, int y, int w, int h,
        Component hint) {
      super(font, x, y, w, h, hint);
    }

    @Override
    public void insertText(String text) {
      super.insertText(swap(text));
    }

    private static String swap(String text) {
      if (text == null || text.isEmpty()) {
        return text;
      }
      StringBuilder out = new StringBuilder(text.length());
      for (int i = 0; i < text.length(); i++) {
        char c = text.charAt(i);
        int at = RU.indexOf(Character.toLowerCase(c));
        if (at < 0) {
          out.append(c);
          continue;
        }
        char latin = EN.charAt(at);
        out.append(Character.isUpperCase(c) ? Character.toUpperCase(latin) : latin);
      }
      return out.toString();
    }
  }

  @Override
  public void tick() {
    super.tick();
    if (this.waiting && System.currentTimeMillis() - this.sentAt > 10000) {
      this.waiting = false;
      this.status = Component.translatable("menu.openorigins.sethome.timeout");
      this.statusColor = 0xFFFF5555;
    }
  }

  @Override
  public void removed() {
    if (active == this) {
      active = null;
    }
  }

  private void doDone() {
    if (this.waiting) {
      return;
    }
    String name = this.nameBox == null ? "" : this.nameBox.getValue().trim().replaceAll("[^a-zA-Z0-9_]", "");
    if (!name.isEmpty() && this.minecraft != null && this.minecraft.player != null) {
      com.openorigins.client.util.HomeCreationConfirm.expect(name);
      com.openorigins.client.util.HomeListFetcher.noteMutation();
      this.minecraft.player.connection.sendCommand("sethome " + name);
      com.openorigins.client.util.OutboxStats.record("menu /sethome " + name);
      this.waiting = true;
      this.sentAt = System.currentTimeMillis();
      this.status = Component.translatable("menu.openorigins.sethome.creating");
      this.statusColor = 0xFFAAAAAA;
    }
  }

  private void applyResult(boolean ok, String detail) {
    if (this.minecraft == null || this.minecraft.gui.screen() != this) {
      return;
    }
    if (ok) {
      this.waiting = false;
      com.openorigins.client.util.HomeFetch.requestIdleRefresh();
      this.minecraft.gui.setScreen(returnTo.get());
    } else {
      this.waiting = false;
      this.status = Component.literal(detail).withStyle(ChatFormatting.RED);
      this.statusColor = 0xFFFF5555;
    }
  }

  private void doCancel() {
    if (active == this) {
      active = null;
    }
    if (this.minecraft != null) {
      com.openorigins.client.util.HomeCreationConfirm.dropPending();
      this.minecraft.gui.setScreen(returnTo.get());
    }
  }

  @Override
  public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
    if (event.key() == com.openorigins.client.util.VersionCompat.inputCode("KEY_RETURN")
        || event.key() == com.openorigins.client.util.VersionCompat.inputCode("KEY_NUMPADENTER")) {
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
    if (this.status != null) {
      String s = this.status.getString();
      int w = this.font.width(s);
      extractor.text(this.font, s, this.width / 2 - w / 2, this.height / 2 + 44, this.statusColor);
    }
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
