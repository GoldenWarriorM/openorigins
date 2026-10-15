package com.openorigins.client.util;

import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Method;

/**
 * Single-jar support for 26.2 (GLFW) and 26.3 (SDL): every API that changed
 * between the two (key types, KeyEvent auxiliaries, PoseStack rotation) is
 * resolved reflectively once and cached. Main code must never touch the
 * divergent members directly, so the same binary runs on both versions.
 */
public final class VersionCompat {
  private VersionCompat() {}

  /** True on 26.3+ (SDL input, KEYBOARD/MOUSE types only). */
  public static final boolean IS_SDL = detectSdl();

  private static boolean detectSdl() {
    try {
      InputConstants.Type.valueOf("KEYBOARD");
      return true;
    } catch (Throwable t) {
      return false;
    }
  }

  private static volatile Object keyboardType;
  private static volatile Method getOrCreate;
  private static volatile Method auxMethod;
  private static volatile Method rotateMethod;

  private static Object keyboardType() {
    Object t = keyboardType;
    if (t == null) {
      synchronized (VersionCompat.class) {
        t = keyboardType;
        if (t == null) {
          t = InputConstants.Type.valueOf(IS_SDL ? "KEYBOARD" : "KEYSYM");
          keyboardType = t;
        }
      }
    }
    return t;
  }

  private static Method getOrCreateMethod() throws Exception {
    Method m = getOrCreate;
    if (m == null) {
      synchronized (VersionCompat.class) {
        m = getOrCreate;
        if (m == null) {
          m = InputConstants.Type.class.getMethod("getOrCreate", int.class);
          getOrCreate = m;
        }
      }
    }
    return m;
  }

  /** Primary key object for a key code (keysym on 26.2, SDL key on 26.3). */
  public static InputConstants.Key makeKey(int code) {
    try {
      return (InputConstants.Key) getOrCreateMethod().invoke(keyboardType(), code);
    } catch (Throwable t) {
      return InputConstants.UNKNOWN;
    }
  }

  /** Fallback key object for auxiliar codes (scancode on 26.2, keycode on 26.3). */
  public static InputConstants.Key makeFallbackKey(int code) {
    if (IS_SDL) {
      return makeKey(code);
    }
    try {
      Object scancode = InputConstants.Type.valueOf("SCANCODE");
      return (InputConstants.Key) getOrCreateMethod().invoke(scancode, code);
    } catch (Throwable t) {
      return InputConstants.UNKNOWN;
    }
  }

  /** Keyboard type object for persistence (MOUSE is referenced directly). */
  public static Object keyboardTypeObject() {
    return keyboardType();
  }

  public static boolean isKeyboardType(Object type) {
    if (type == null) {
      return false;
    }
    String n;
    try {
      n = ((Enum<?>) type).name();
    } catch (Throwable t) {
      return false;
    }
    return "KEYBOARD".equals(n) || "KEYSYM".equals(n) || "SCANCODE".equals(n);
  }

  public static boolean isEscape(int keyCode) {
    return keyCode == inputCode("KEY_ESCAPE");
  }

  /** Version-correct InputConstants int (key/mouse codes differ: GLFW vs SDL). */
  public static int inputCode(String field) {
    Integer cached;
    synchronized (VersionCompat.class) {
      cached = codeCache.get(field);
      if (cached == null) {
        int v = -1;
        try {
          v = InputConstants.class.getField(field).getInt(null);
        } catch (Throwable ignored) {
        }
        cached = v;
        codeCache.put(field, v);
      }
    }
    return cached;
  }

  private static final java.util.Map<String, Integer> codeCache =
      new java.util.HashMap<>();

  public static int mouseLeft() {
    return inputCode("MOUSE_BUTTON_LEFT");
  }

  public static int mouseRight() {
    return inputCode("MOUSE_BUTTON_RIGHT");
  }

  /** Auxiliary event code: scancode() on 26.2, keycode() on 26.3. */
  public static int auxCode(net.minecraft.client.input.KeyEvent event) {
    try {
      Method m = auxMethod;
      if (m == null) {
        synchronized (VersionCompat.class) {
          m = auxMethod;
          if (m == null) {
            String name = IS_SDL ? "keycode" : "scancode";
            m = net.minecraft.client.input.KeyEvent.class.getMethod(name);
            auxMethod = m;
          }
        }
      }
      Object v = m.invoke(event);
      return v instanceof Integer i ? i : InputConstants.UNKNOWN.getValue();
    } catch (Throwable t) {
      return InputConstants.UNKNOWN.getValue();
    }
  }

  /** Y-180 rotation that works on both versions (rotate vs mulPose). */
  public static void rotate(com.mojang.blaze3d.vertex.PoseStack poseStack,
      org.joml.Quaternionf rotation) {
    try {
      Method m = rotateMethod;
      if (m == null) {
        synchronized (VersionCompat.class) {
          m = rotateMethod;
          if (m == null) {
            if (IS_SDL) {
              m = com.mojang.blaze3d.vertex.PoseStack.class
                  .getMethod("rotate", org.joml.Quaternionfc.class);
            } else {
              m = com.mojang.blaze3d.vertex.PoseStack.class
                  .getMethod("mulPose", org.joml.Quaternionf.class);
            }
            rotateMethod = m;
          }
        }
      }
      m.invoke(poseStack, rotation);
    } catch (Throwable ignored) {
    }
  }
}
