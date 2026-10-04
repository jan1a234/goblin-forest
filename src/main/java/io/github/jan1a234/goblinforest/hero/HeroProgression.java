package io.github.jan1a234.goblinforest.hero;

import io.github.jan1a234.goblinforest.config.Balance;
import java.util.List;

/** Level, Lebenspunkte, Schaden und Wiederbelebungszeit des Häuptlings (DESIGN.md Abschnitt 6). Reine Rechenlogik. */
public final class HeroProgression {
	private final Balance.Hero config;

	public HeroProgression(Balance.Hero config) {
		this.config = config;
	}

	public int maxLevel() {
		return Math.min(config.maxLevel(), config.xpForLevel().size());
	}

	public int levelForXp(double xp) {
		List<Integer> thresholds = config.xpForLevel();
		int level = 1;
		for (int i = 1; i < maxLevel(); i++) {
			if (xp >= thresholds.get(i)) {
				level = i + 1;
			}
		}
		return level;
	}

	/** EP-Schwelle, ab der das angegebene Level erreicht ist. */
	public int xpForLevel(int level) {
		return config.xpForLevel().get(Math.clamp(level, 1, maxLevel()) - 1);
	}

	/** Fortschritt zum nächsten Level zwischen 0 und 1; auf dem Höchstlevel immer 1. */
	public double progressToNext(double xp) {
		int level = levelForXp(xp);
		if (level >= maxLevel()) {
			return 1.0;
		}
		double from = xpForLevel(level);
		double to = xpForLevel(level + 1);
		return Math.clamp((xp - from) / (to - from), 0.0, 1.0);
	}

	public double maxHealth(int level) {
		return config.baseHealth() + config.healthPerLevel() * (level - 1);
	}

	public double damage(int level) {
		return config.baseDamage() + config.damagePerLevel() * (level - 1);
	}

	public int respawnSeconds(int level) {
		return config.respawnBaseSeconds() + config.respawnSecondsPerLevel() * level;
	}

	/** Anteil der Armee-Kills, der dem Häuptling als Erfahrung gutgeschrieben wird. */
	public double armyXpShare() {
		return config.armyXpShare();
	}
}
