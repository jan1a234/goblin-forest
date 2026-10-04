package io.github.jan1a234.goblinforest.unit;

import io.github.jan1a234.goblinforest.config.Balance;
import java.util.List;

/**
 * Veteranen-System: jede Einheit sammelt eigene EP und steigt einzeln im Level (DESIGN.md Abschnitt 5.2).
 * Reine Rechenlogik ohne Minecraft-Abhängigkeit, damit sie per Unit-Test geprüft werden kann.
 */
public final class UnitLeveling {
	private final Balance.UnitLeveling config;

	public UnitLeveling(Balance.UnitLeveling config) {
		this.config = config;
	}

	public int maxLevel() {
		return config.levels().size();
	}

	/** Level (ab 1) für die angegebene Erfahrung. */
	public int levelForXp(double xp) {
		List<Balance.LevelStep> levels = config.levels();
		int level = 1;
		for (int i = 1; i < levels.size(); i++) {
			if (xp >= levels.get(i).xpRequired()) {
				level = i + 1;
			}
		}
		return level;
	}

	public Balance.LevelStep step(int level) {
		int index = Math.clamp(level, 1, maxLevel()) - 1;
		return config.levels().get(index);
	}

	public double xpForKill() {
		return config.xpPerKill();
	}

	public double xpForDamage(double damage) {
		return damage * config.xpPerDamage();
	}

	public double xpForBuildingHit() {
		return config.xpPerBuildingHit();
	}

	public double scaledHealth(double baseHealth, int level) {
		return baseHealth * (1.0 + step(level).healthBonus());
	}

	public double scaledDamage(double baseDamage, int level) {
		return baseDamage * (1.0 + step(level).damageBonus());
	}

	public double scaledSpeed(double baseSpeed, int level) {
		return baseSpeed * (1.0 + step(level).speedBonus());
	}
}
