package io.github.jan1a234.goblinforest.registry;

import io.github.jan1a234.goblinforest.GoblinForest;
import io.github.jan1a234.goblinforest.unit.GoblinUnit;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Registriert die Entity-Typen der Mod. Alle vier Einheitentypen teilen sich einen Entity-Typ. */
public final class ModEntities {
	public static final ResourceKey<EntityType<?>> GOBLIN_KEY = ResourceKey.create(Registries.ENTITY_TYPE, GoblinForest.id("goblin"));

	public static final EntityType<GoblinUnit> GOBLIN = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			GOBLIN_KEY,
			EntityType.Builder.<GoblinUnit>of(GoblinUnit::new, MobCategory.CREATURE)
					.sized(0.6f, 1.95f)
					.eyeHeight(1.79f)
					.clientTrackingRange(10)
					.noLootTable()
					.build(GOBLIN_KEY));

	private ModEntities() {
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(GOBLIN, GoblinUnit.createAttributes());
	}
}
