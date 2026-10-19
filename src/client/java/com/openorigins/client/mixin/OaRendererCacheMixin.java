package com.openorigins.client.mixin;

import com.openorigins.client.util.OaRendererCache;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Skips the per-frame renderer map lookup for already-seen entity types. */
@Mixin(EntityRenderDispatcher.class)
public abstract class OaRendererCacheMixin {
  @Shadow
  private java.util.Map<net.minecraft.world.entity.EntityType<?>, EntityRenderer<?, ?>> renderers;
  @Inject(method = "getRenderer(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;",
      at = @At("HEAD"), cancellable = true, require = 0)
  private void openorigins$cachedRenderer(Entity entity,
      CallbackInfoReturnable<EntityRenderer<?, ?>> cir) {
    try {
      EntityRenderer<?, ?> hit = OaRendererCache.get(entity);
      if (hit != null) {
        cir.setReturnValue(hit);
      }
    } catch (Throwable ignored) {
    }
  }

  @Inject(method = "getRenderer(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;",
      at = @At("RETURN"), require = 0)
  private void openorigins$storeRenderer(Entity entity,
      CallbackInfoReturnable<EntityRenderer<?, ?>> cir) {
    try {
      if (entity instanceof net.minecraft.world.entity.Avatar) {
        return;
      }
      EntityRenderer<?, ?> r = cir.getReturnValue();
      // Intermittently cross-wired renderer table (teleport window): an
      // entity gets a foreign-family renderer and the typed cast inside
      // extract/culling throws CCE. Swap to the MARKER NoopRenderer — the
      // entity skips drawing for a frame instead of crashing the client.
      if (r != null && !OaRendererCache.sane(entity, r)) {
        try {
          EntityRenderer<?, ?> byType = this.renderers == null ? null
              : this.renderers.get(entity.getType());
          if (byType != null && !OaRendererCache.sane(entity, byType)) {
            OaRendererCache.noteVanillaMapMispair(entity, byType);
          }
        } catch (Throwable ignored) {
        }
        EntityRenderer<?, ?> noop = this.renderers == null ? null
            : this.renderers.get(net.minecraft.world.entity.EntityTypes.MARKER);
        // NoopRenderer is outside every typed family, sane for any entity.
        if (noop != null && OaRendererCache.sane((Entity) null, noop)) {
          cir.setReturnValue(noop);
          r = noop;
          OaRendererCache.noteMispair(entity, noop);
        } else {
          // No safe fallback known: don't cache the bad pair, let the
          // vanilla path run (the crash remains possible but unlogged).
          OaRendererCache.noteMispair(entity, r);
          return;
        }
      }
      OaRendererCache.put(entity, r);
    } catch (Throwable ignored) {
    }
  }

  /**
   * The submit path resolves the renderer via the state overload, which our
   * entity lookup never sees. When the vanilla table heals between extract
   * and submit (resource-pack reload), a state extracted by the Noop
   * fallback reaches the now-correct typed renderer whose submit cast is
   * typed — CCE. Fail the family check here the same way: NoopRenderer.
   */
  @Inject(method = "getRenderer(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)Lnet/minecraft/client/renderer/entity/EntityRenderer;",
      at = @At("RETURN"), cancellable = true, require = 0)
  private void openorigins$stateRenderer(
      net.minecraft.client.renderer.entity.state.EntityRenderState state,
      CallbackInfoReturnable<EntityRenderer<?, ?>> cir) {
    try {
      if (state == null) {
        return;
      }
      EntityRenderer<?, ?> r = cir.getReturnValue();
      if (r == null) {
        return;
      }
      if (!OaRendererCache.stateMatches(state, r)) {
        EntityRenderer<?, ?> noop = this.renderers == null ? null
            : this.renderers.get(net.minecraft.world.entity.EntityTypes.MARKER);
        if (noop != null && OaRendererCache.stateMatches(state, noop)) {
          cir.setReturnValue(noop);
          r = noop;
          OaRendererCache.noteMispair(state, noop);
        } else {
          OaRendererCache.noteMispair(state, r);
        }
      }
    } catch (Throwable ignored) {
    }
  }

  /** A resource reload rebuilds the dispatcher's renderer table; the cached
   *  instances are stale from that point on. */
  @Inject(method = "onResourceManagerReload(Lnet/minecraft/server/packs/resources/ResourceManager;)V",
      at = @At("RETURN"), require = 0)
  private void openorigins$reloadRenderers(
      net.minecraft.server.packs.resources.ResourceManager resourceManager, CallbackInfo ci) {
    try {
      OaRendererCache.clear();
    } catch (Throwable ignored) {
    }
  }
}
