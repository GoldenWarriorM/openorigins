package com.openorigins.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.openorigins.client.ConnectionUtil;
import com.openorigins.client.OpenOriginsConfig;
import com.openorigins.client.OriginFeature;
import com.openorigins.client.util.BackItemHolder;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Renders the back-slot item on the torso, facing backward. */
public class BackSwagLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
  public BackSwagLayer(net.minecraft.client.renderer.entity.RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
    super(parent);
  }

  @Override
  public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
      AvatarRenderState state, float limbSwing, float limbSwingAmount) {
    if (!OpenOriginsConfig.getInstance().isEnabled(OriginFeature.BACK_SWAG_SLOT)) return;
    if (!ConnectionUtil.isConnected()) return;
    ItemStackRenderState item = ((BackItemHolder) state).openorigins$getBackItem();
    if (item == null || item.isEmpty()) return;
    poseStack.pushPose();
    HumanoidModel<?> model = this.getParentModel();
    model.body.translateAndRotate(poseStack);
    poseStack.translate(0.0F, -2.075F, 0.0F);
    com.openorigins.client.util.VersionCompat.rotate(poseStack, Axis.YP.rotationDegrees(180.0F));
    poseStack.scale(0.625F, -0.625F, -0.625F);
    item.submit(poseStack, collector, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
    poseStack.popPose();
  }
}
