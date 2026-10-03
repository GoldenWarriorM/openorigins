package com.openorigins.client.util;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

/**
 * Loads images from the currently applied (server) resource packs at runtime
 * and trims transparent margins down to the visible outline.
 *
 * <p>Nothing from the server pack is bundled with the mod, which keeps
 * redistribution clean: pixels are only read from the packs the player
 * already has loaded.
 */
public final class ServerPackIcons {
  private ServerPackIcons() {}

  public record LoadedIcon(Identifier textureId, int width, int height) {}

  private static final Map<String, LoadedIcon> CACHE = new ConcurrentHashMap<>();

  /**
   * @param packPath path inside {@code assets/<namespace>/}, e.g.
   *                 {@code "textures/custom_ui/transport_menu/button_balloon_blue.png"}
   *                 under the {@code minecraft} namespace.
   * @return trimmed icon, or empty if the pack does not provide it.
   */
  public static Optional<LoadedIcon> get(String packPath) {
    LoadedIcon cached = CACHE.get(packPath);
    if (cached != null) {
      return Optional.of(cached);
    }
    Minecraft mc = Minecraft.getInstance();
    if (mc == null) {
      return Optional.empty();
    }
    Identifier src = Identifier.fromNamespaceAndPath("minecraft", packPath);
    Optional<Resource> res = mc.getResourceManager().getResource(src);
    if (res.isEmpty()) {
      return Optional.empty();
    }
    try (InputStream in = res.get().open(); NativeImage raw = NativeImage.read(in)) {
      int w = raw.getWidth();
      int h = raw.getHeight();
      int minX = w, minY = h, maxX = -1, maxY = -1;
      for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
          int alpha = (raw.getPixel(x, y) >>> 24) & 0xFF;
          if (alpha > 8) {
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
          }
        }
      }
      if (maxX < minX || maxY < minY) {
        return Optional.empty();
      }
      int tw = maxX - minX + 1;
      int th = maxY - minY + 1;
      NativeImage trimmed = new NativeImage(tw, th, false);
      try {
        for (int y = 0; y < th; y++) {
          for (int x = 0; x < tw; x++) {
            trimmed.setPixel(x, y, raw.getPixel(minX + x, minY + y));
          }
        }
      } catch (Throwable t) {
        trimmed.close();
        throw t;
      }
      String name = packPath.substring(packPath.lastIndexOf('/') + 1).replace(".png", "_trimmed");
      Identifier dest = Identifier.fromNamespaceAndPath("openorigins", "pack/" + name);
      try {
        mc.getTextureManager().register(dest, new DynamicTexture(() -> "openorigins-pack-icon", trimmed));
      } catch (Throwable t) {
        trimmed.close();
        throw t;
      }
      LoadedIcon icon = new LoadedIcon(dest, tw, th);
      CACHE.put(packPath, icon);
      return Optional.of(icon);
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  public static void clearCache() {
    CACHE.clear();
  }
}
