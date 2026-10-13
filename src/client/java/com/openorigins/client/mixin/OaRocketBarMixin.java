package com.openorigins.client.mixin;

import com.openorigins.client.OpenOriginsConfig;
import com.openorigins.client.OriginFeature;
import com.openorigins.client.util.OaRocketFuel;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Rocket-boots durability bar replaced with the fuel bar. */
@Mixin(ItemStack.class)
public abstract class OaRocketBarMixin {
  private boolean openorigins$fuelBar() {
    try {
      return OpenOriginsConfig.getInstance().isEnabled(OriginFeature.OA_ROCKET_FUEL_BAR);
    } catch (Throwable t) {
      return false;
    }
  }

  private float openorigins$fraction() {
    ItemStack self = (ItemStack) (Object) this;
    try {
      String id = OaRocketFuel.shortId(self);
      if (!id.startsWith("rocket_boots_")) return -1.0f;
      if (OaRocketFuel.maxFuel(id) <= 0) return -1.0f;
      return OaRocketFuel.fraction(self, id);
    } catch (Throwable t) {
      return -1.0f;
    }
  }

  @Inject(method = "isBarVisible", at = @At("HEAD"), cancellable = true)
  private void openorigins$fuelVisible(CallbackInfoReturnable<Boolean> cir) {
    if (openorigins$fuelBar() && openorigins$fraction() >= 0.0f) {
      cir.setReturnValue(true);
      cir.cancel();
    }
  }

  @Inject(method = "getBarWidth", at = @At("HEAD"), cancellable = true)
  private void openorigins$fuelStep(CallbackInfoReturnable<Integer> cir) {
    if (!openorigins$fuelBar()) return;
    float f = openorigins$fraction();
    if (f >= 0.0f) {
      cir.setReturnValue(Math.max(0, Math.min(13, Math.round(13.0f * f))));
      cir.cancel();
    }
  }

  @Inject(method = "getBarColor", at = @At("HEAD"), cancellable = true)
  private void openorigins$fuelColor(CallbackInfoReturnable<Integer> cir) {
    if (!openorigins$fuelBar()) return;
    float f = openorigins$fraction();
    if (f >= 0.0f) {
      cir.setReturnValue(OaRocketFuel.barColor(f));
      cir.cancel();
    }
  }
}
