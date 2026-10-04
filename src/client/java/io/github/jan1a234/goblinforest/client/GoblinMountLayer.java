package io.github.jan1a234.goblinforest.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.animal.wolf.WolfModel;
import net.minecraft.client.model.monster.piglin.PiglinModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.PiglinRenderState;
import net.minecraft.client.renderer.entity.state.WolfRenderState;

/**
 * Zeichnet den Warg unter dem Wolfsreiter: das Wolfsmodell, etwas größer und dunkler als ein normaler Wolf,
 * mit der Laufanimation des Reiters. Der Reiter selbst wird dafür in {@link GoblinRenderer} angehoben
 * und sitzt in Reitpose.
 */
public class GoblinMountLayer extends RenderLayer<PiglinRenderState, PiglinModel> {
	/** Größe des Wargs im Verhältnis zum normalen Wolf. */
	static final float WARG_SCALE = 1.4F;
	/** So weit (in Modelleinheiten, 1 = ein Block) wird der Reiter angehoben, damit er auf dem Rücken sitzt. */
	static final float RIDER_RAISE = 0.38F;
	/** Dunkles Grau statt des hellen Wolfsfells. */
	private static final int WARG_TINT = 0xFF6E655C;
	/** Fußpunkt im Modellraum (Y zeigt nach unten). */
	private static final float FEET_Y = 1.501F;

	private final WolfModel wolfModel;
	private final WolfRenderState wolf = new WolfRenderState();

	public GoblinMountLayer(RenderLayerParent<PiglinRenderState, PiglinModel> renderer, WolfModel wolfModel) {
		super(renderer);
		this.wolfModel = wolfModel;
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, PiglinRenderState state, float yRot, float xRot) {
		if (!(state instanceof GoblinRenderState goblin) || !goblin.mounted) {
			return;
		}
		wolf.ageInTicks = state.ageInTicks;
		wolf.walkAnimationPos = state.walkAnimationPos * 0.8F;
		wolf.walkAnimationSpeed = state.walkAnimationSpeed;
		wolf.yRot = yRot * 0.5F;
		wolf.xRot = 0.0F;
		wolf.isAngry = goblin.aggressive;
		wolf.hasRedOverlay = state.hasRedOverlay;
		wolf.deathTime = state.deathTime;
		wolf.lightCoords = lightCoords;
		wolf.outlineColor = state.outlineColor;

		poseStack.pushPose();
		// Anhebung des Reiters rückgängig machen, dann um den Fußpunkt vergrößern.
		poseStack.translate(0.0F, RIDER_RAISE + FEET_Y, 0.0F);
		poseStack.scale(WARG_SCALE, WARG_SCALE, WARG_SCALE);
		poseStack.translate(0.0F, -FEET_Y, 0.0F);
		collector.submitModel(wolfModel, wolf, poseStack, wolfModel.renderType(wolf.texture), lightCoords,
				LivingEntityRenderer.getOverlayCoords(state, 0.0F), WARG_TINT, null, state.outlineColor, null);
		poseStack.popPose();
	}
}
