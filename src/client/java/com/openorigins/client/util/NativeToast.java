package com.openorigins.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/** Vanilla system toast, used for all OpenOrigins notifications. */
public final class NativeToast {
  private NativeToast() {}

  public static void show(Component title, Component message) {
    Minecraft mc = Minecraft.getInstance();
    if (mc == null) {
      return;
    }
    try {
      SystemToast.add(mc.gui.toastManager(), new SystemToast.SystemToastId(), title, message);
    } catch (Throwable ignored) {
    }
  }
}
