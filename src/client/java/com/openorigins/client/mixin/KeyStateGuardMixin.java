package com.openorigins.client.mixin;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.3 indexes the SDL key-state buffer without a bounds check: any stale or
 * foreign binding (or a not-yet-ready buffer) crashes the game on join
 * (KeyMapping.setAll). Out-of-range reads report false instead.
 * 26.2 has no such method, so this injection is optional (require = 0) and
 * everything SDL is resolved reflectively to keep the 26.2 runtime clean.
 */
@Mixin(com.mojang.blaze3d.platform.InputConstants.class)
public abstract class KeyStateGuardMixin {
  private static final Set<Integer> LOGGED =
      Collections.newSetFromMap(new ConcurrentHashMap<>());

  @Inject(method = "isKeyDown(I)Z", at = @At("HEAD"), cancellable = true, require = 0)
  private static void openorigins$guardKeyState(int code, CallbackInfoReturnable<Boolean> cir) {
    ByteBuffer buf;
    try {
      Class<?> sdl = Class.forName("org.lwjgl.sdl.SDLKeyboard");
      buf = (ByteBuffer) sdl.getMethod("SDL_GetKeyboardState").invoke(null);
    } catch (Throwable t) {
      return;
    }
    if (buf == null) {
      return;
    }
    int limit;
    try {
      limit = buf.limit();
    } catch (Throwable t) {
      return;
    }
    if (code < 0 || code >= limit) {
      if (LOGGED.add(code)) {
        com.openorigins.client.util.DebugLog.info("[keyguard] OOB key read: {} (limit {})",
            code, limit);
      }
      cir.setReturnValue(false);
    }
  }
}
