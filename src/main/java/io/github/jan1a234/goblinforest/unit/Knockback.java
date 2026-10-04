package io.github.jan1a234.goblinforest.unit;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/** Rückstoß für Fähigkeiten und Spezialangriffe, abgeschwächt durch die Rückstoßresistenz des Ziels. */
public final class Knockback {
	private Knockback() {
	}

	/** Stößt ein Lebewesen in Richtung (dx, dz) weg. */
	public static void shove(LivingEntity entity, double dx, double dz, double strength) {
		double length = Math.sqrt(dx * dx + dz * dz);
		if (length < 1.0E-4) {
			return;
		}
		double factor = strength * (1.0 - entity.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
		if (factor <= 0) {
			return;
		}
		Vec3 motion = entity.getDeltaMovement();
		entity.setDeltaMovement(motion.x / 2 + dx / length * factor, entity.onGround() ? Math.min(0.4, motion.y / 2 + factor * 0.5) : motion.y,
				motion.z / 2 + dz / length * factor);
		entity.hurtMarked = true;
	}

	/** Stößt {@code target} von {@code source} weg. */
	public static void away(LivingEntity target, double fromX, double fromZ, double strength) {
		shove(target, target.getX() - fromX, target.getZ() - fromZ, strength);
	}
}
