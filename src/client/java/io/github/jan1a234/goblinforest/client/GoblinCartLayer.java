package io.github.jan1a234.goblinforest.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.monster.piglin.PiglinModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.PiglinRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Zeichnet das Belagerungsgerät des Katapult-Goblins: einen Block-Karren (Werfer), den er vor sich herschiebt.
 * Gleiches Vorgehen wie der Block, den ein Enderman trägt.
 */
public class GoblinCartLayer extends RenderLayer<PiglinRenderState, PiglinModel> {
	public GoblinCartLayer(RenderLayerParent<PiglinRenderState, PiglinModel> renderer) {
		super(renderer);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, PiglinRenderState state, float yRot, float xRot) {
		if (!(state instanceof GoblinRenderState goblin) || goblin.cart.isEmpty()) {
			return;
		}
		poseStack.pushPose();
		// Modellraum: Y zeigt nach unten, die Füße liegen bei 1.501, vorne ist -Z.
		poseStack.translate(0.0F, 1.501F, -1.05F);
		poseStack.scale(-0.85F, -0.85F, 0.85F);
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
		poseStack.translate(-0.5F, 0.0F, -0.5F);
		goblin.cart.submit(poseStack, collector, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
	}
}
