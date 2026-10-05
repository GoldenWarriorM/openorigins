package com.openorigins.client.mixin;

import com.openorigins.client.ConnectionUtil;
import com.openorigins.client.OpenOriginsConfig;
import com.openorigins.client.OriginFeature;
import java.util.Collection;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Chat name suggestions: @word completes @player pings, :word completes
 * emojis only, plain words never show emojis.
 */
@Mixin(ClientSuggestionProvider.class)
public abstract class SuggestionProviderMixin {
  @Inject(method = "getOnlinePlayerNames", at = @At("RETURN"), cancellable = true)
  private void openorigins$filterNames(CallbackInfoReturnable<Collection<String>> cir) {
    if (!OpenOriginsConfig.getInstance().isEnabled(OriginFeature.AUTOCOMPLETE_ENHANCEMENTS)
        || !ConnectionUtil.isConnected()) {
      return;
    }
    Collection<String> original = cir.getReturnValue();
    if (original == null) {
      return;
    }
    if (Minecraft.getInstance().gui.screen() instanceof ChatScreen chat) {
      String text = ((ChatScreenAccessor) chat).openorigins$getInput().getValue();
      String[] split = text.split(" ");
      if (split.length > 0 && !text.endsWith(" ")) {
        String word = split[split.length - 1];
        if (word.startsWith("@")) {
          cir.setReturnValue(original.stream().filter(s -> !s.startsWith(":"))
              .map(s -> "@" + s).collect(Collectors.toList()));
          return;
        }
        if (word.startsWith(":")) {
          cir.setReturnValue(original.stream().filter(s -> s.startsWith(":"))
              .collect(Collectors.toList()));
          return;
        }
      }
    }
    cir.setReturnValue(original.stream().filter(s -> !s.startsWith(":"))
        .collect(Collectors.toList()));
  }
}
