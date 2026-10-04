package io.github.jan1a234.goblinforest.client;

import io.github.jan1a234.goblinforest.GoblinForest;
import io.github.jan1a234.goblinforest.unit.GoblinUnit;
import io.github.jan1a234.goblinforest.unit.UnitType;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.piglin.AdultPiglinModel;
import net.minecraft.client.model.monster.piglin.BabyPiglinModel;
import net.minecraft.client.model.monster.piglin.PiglinModel;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.PiglinRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.PiglinRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.piglin.PiglinArmPose;

/**
 * Zeichnet Goblins mit dem Piglin-Modell (Ohren, Hauer, gedrungener Körper) und eigenen Texturen
 * je Einheitentyp und Clan. Rüstung ab Veteran wird über die normale Rüstungsschicht gezeigt.
 */
public class GoblinRenderer extends HumanoidMobRenderer<GoblinUnit, PiglinRenderState, PiglinModel> {
	private static final Map<String, Identifier> TEXTURES = new HashMap<>();

	public GoblinRenderer(EntityRendererProvider.Context context) {
		super(context, new AdultPiglinModel(context.bakeLayer(ModelLayers.PIGLIN)), new BabyPiglinModel(context.bakeLayer(ModelLayers.PIGLIN_BABY)),
				0.5F, PiglinRenderer.PIGLIN_CUSTOM_HEAD_TRANSFORMS);
		addLayer(new HumanoidArmorLayer<>(this,
				ArmorModelSet.bake(ModelLayers.PIGLIN_ARMOR, context.getModelSet(), AdultPiglinModel::new),
				ArmorModelSet.bake(ModelLayers.PIGLIN_BABY_ARMOR, context.getModelSet(), BabyPiglinModel::new),
				context.getEquipmentRenderer()));
	}

	@Override
	public PiglinRenderState createRenderState() {
		return new GoblinRenderState();
	}

	@Override
	public void extractRenderState(GoblinUnit entity, PiglinRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		UnitType type = entity.unitType();
		if (state instanceof GoblinRenderState goblin) {
			goblin.unit = type.id();
			goblin.team = entity.team().id();
		}
		boolean melee = type != UnitType.ARCHER;
		state.armPose = melee && entity.isAggressive() ? PiglinArmPose.ATTACKING_WITH_MELEE_WEAPON : PiglinArmPose.DEFAULT;
	}

	@Override
	protected HumanoidModel.ArmPose getArmPose(GoblinUnit mob, HumanoidArm arm) {
		if (mob.unitType() == UnitType.ARCHER && mob.isAggressive() && arm == mob.getMainArm()) {
			return HumanoidModel.ArmPose.BOW_AND_ARROW;
		}
		return super.getArmPose(mob, arm);
	}

	@Override
	public Identifier getTextureLocation(PiglinRenderState state) {
		String unit = state instanceof GoblinRenderState goblin ? goblin.unit : "slave";
		String team = state instanceof GoblinRenderState goblin ? goblin.team : "red";
		return TEXTURES.computeIfAbsent(unit + "_" + team, key -> GoblinForest.id("textures/entity/goblin/" + key + ".png"));
	}
}
