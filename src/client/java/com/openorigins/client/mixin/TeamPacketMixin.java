package com.openorigins.client.mixin;

import com.openorigins.client.OpenOriginsConfig;
import com.openorigins.client.OriginFeature;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tolerates stale team-removal packets (server thinks the player is on a team
 * the client never saw). Pre-aligns the client mapping so vanilla removal
 * succeeds with the same end state instead of disconnecting.
 */
@Mixin(ClientPacketListener.class)
public abstract class TeamPacketMixin {
  @Inject(method = "handleSetPlayerTeamPacket", at = @At("HEAD"))
  private void openorigins$alignTeam(ClientboundSetPlayerTeamPacket packet, CallbackInfo ci) {
    if (!OpenOriginsConfig.getInstance().isEnabled(OriginFeature.SCOREBOARD_DESYNC_FIX)) {
      return;
    }
    if (packet.getPlayerAction() != ClientboundSetPlayerTeamPacket.Action.REMOVE) {
      return;
    }
    Scoreboard scoreboard = ((ClientPacketListener) (Object) this).scoreboard();
    PlayerTeam team = scoreboard.getPlayerTeam(packet.getName());
    if (team == null) {
      return;
    }
    for (String player : packet.getPlayers()) {
      if (scoreboard.getPlayersTeam(player) != team) {
        scoreboard.addPlayerToTeam(player, team);
      }
    }
  }
}
