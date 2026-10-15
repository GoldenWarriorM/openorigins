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

  /** Unbound key value on this version (26.2: -1, 26.3: 0). */
  public static int unboundValue() {
    try {
      return com.mojang.blaze3d.platform.InputConstants.UNKNOWN.getValue();
    } catch (Throwable t) {
      return -1;
    }
  }

  /** Key object behind a KeyMapping via its protected field (no getter on 26.2). */
  public static InputConstants.Key mappingKey(net.minecraft.client.KeyMapping mapping) {
    if (mapping == null) {
      return null;
    }
    try {
      java.lang.reflect.Field f = net.minecraft.client.KeyMapping.class.getDeclaredField("key");
      f.setAccessible(true);
      Object k = f.get(mapping);
      return k instanceof InputConstants.Key key ? key : null;
    } catch (Throwable t) {
      return null;
    }
  }

  /** True for null or UNKNOWN-valued keys (shows vanilla "Not Bound"). */
  public static boolean isUnbound(InputConstants.Key key) {
    if (key == null) {
      return true;
    }
    try {
      return key.getValue() == InputConstants.UNKNOWN.getValue();
    } catch (Throwable t) {
      return false;
    }
  }

  public static boolean isUnbound(net.minecraft.client.KeyMapping mapping) {
    return mapping == null || isUnbound(mappingKey(mapping));
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

  private static volatile Object guiTexturedPipeline;
  private static final java.util.Map<String, Method> blitMethods =
      new java.util.concurrent.ConcurrentHashMap<>();
  private static final java.util.Set<String> blitMissLogged =
      java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

  /** GUI_TEXTURED pipeline object (type moved packages in 26.3). */
  public static Object guiTexturedPipeline() {
    Object p = guiTexturedPipeline;
    if (p == null) {
      synchronized (VersionCompat.class) {
        p = guiTexturedPipeline;
        if (p == null) {
          try {
            p = Class.forName("net.minecraft.client.renderer.RenderPipelines")
                .getField("GUI_TEXTURED").get(null);
          } catch (Throwable t) {
            return null;
          }
          guiTexturedPipeline = p;
        }
      }
    }
    return p;
  }

  private static Method blitMethod(Object gfx, String name, int totalArgs) {
    String key = name + "#" + totalArgs;
    Method m = blitMethods.get(key);
    if (m != null) {
      return m;
    }
    Object pipe = guiTexturedPipeline();
    if (pipe == null) {
      return null;
    }
    for (Method cand : gfx.getClass().getMethods()) {
      if (!cand.getName().equals(name)
          || cand.getParameterCount() != totalArgs + 1) {
        continue;
      }
      if (!cand.getParameterTypes()[0].isInstance(pipe)) {
        continue;
      }
      blitMethods.put(key, cand);
      return cand;
    }
    // TEMP diagnostic for invisible slot overlays.
    if (blitMissLogged.add(key)) {
      com.openorigins.client.util.DebugLog.info(
          "[blitprobe] no match for {} with {} args on {} (pipe {})", name, totalArgs,
          gfx.getClass().getName(),
          pipe == null ? "<null>" : pipe.getClass().getName());
    }
    return null;
  }

  private static void invokeBlit(String name, Object gfx, Object[] args) {
    try {
      Object pipe = guiTexturedPipeline();
      Method m = blitMethod(gfx, name, args.length);
      if (pipe == null || m == null) {
        return;
      }
      Object[] full = new Object[args.length + 1];
      full[0] = pipe;
      System.arraycopy(args, 0, full, 1, args.length);
      m.invoke(gfx, full);
    } catch (Throwable ignored) {
    }
  }

  public static void blitSprite(net.minecraft.client.gui.GuiGraphicsExtractor gfx,
      net.minecraft.resources.Identifier sprite, int x, int y, int w, int h) {
    invokeBlit("blitSprite", gfx, new Object[]{sprite, x, y, w, h});
  }

  public static void blit(net.minecraft.client.gui.GuiGraphicsExtractor gfx,
      net.minecraft.resources.Identifier texture, int x, int y, float u, float v,
      int w, int h, int texW, int texH) {
    invokeBlit("blit", gfx, new Object[]{texture, x, y, u, v, w, h, texW, texH});
  }

  public static void blitFull(net.minecraft.client.gui.GuiGraphicsExtractor gfx,
      net.minecraft.resources.Identifier texture, int x, int y, float u, float v,
      int w, int h, int a, int b, int c, int d) {
    invokeBlit("blit", gfx, new Object[]{texture, x, y, u, v, w, h, a, b, c, d});
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
