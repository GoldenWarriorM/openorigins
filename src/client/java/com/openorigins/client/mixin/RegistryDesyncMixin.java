package com.openorigins.client.mixin;

import com.openorigins.client.OpenOriginsConfig;
import com.openorigins.client.OriginFeature;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tolerates duplicate network registry entries (server sends some twice,
 * e.g. custom biomes on realm transfer). Returns the existing holder
 * instead of disconnecting with a protocol error.
 */
@Mixin(MappedRegistry.class)
public abstract class RegistryDesyncMixin {
  @Shadow
  private Map<Identifier, Holder.Reference<?>> byLocation;
  @Shadow
  private Map<?, Holder.Reference<?>> byValue;

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Inject(method = "register", at = @At("HEAD"), cancellable = true)
  private void openorigins$tolerateDupes(ResourceKey key, Object value,
      RegistrationInfo registrationInfo, CallbackInfoReturnable<Holder.Reference<?>> cir) {
    if (!OpenOriginsConfig.getInstance().isEnabled(OriginFeature.REGISTRY_DESYNC_FIX)) {
      return;
    }
    Holder.Reference<?> existing = this.byLocation.get(key.identifier());
    if (existing != null) {
      cir.setReturnValue(existing);
      return;
    }
    existing = (Holder.Reference<?>) ((Map) this.byValue).get(value);
    if (existing != null) {
      cir.setReturnValue(existing);
    }
  }
}
