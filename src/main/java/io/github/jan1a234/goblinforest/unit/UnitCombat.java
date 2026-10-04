package io.github.jan1a234.goblinforest.unit;

import io.github.jan1a234.goblinforest.config.Balance;

/**
 * Kampfwerte einer Einheit aus Grundwerten, Veteranen-Level und globalen Upgrades. Reine Rechenlogik,
 * damit Balancing-Änderungen per Unit-Test geprüft werden können.
 */
public final class UnitCombat {
	/** Mindestens so viel Schaden kommt immer durch, egal wie viel Rüstung. */
	public static final double MIN_DAMAGE_TAKEN = 0.2;

	private final Balance balance;
	private final UnitLeveling leveling;

	public UnitCombat(Balance balance) {
		this.balance = balance;
		this.leveling = new UnitLeveling(balance.unitLeveling());
	}

	public UnitLeveling leveling() {
		return leveling;
	}

	public double maxHealth(UnitType type, int level) {
		return leveling.scaledHealth(balance.unit(type).health(), level);
	}

	public double damage(UnitType type, int level, int attackUpgrade) {
		double perLevel = balance.upgrade("attack").valuePerLevel();
		return leveling.scaledDamage(balance.unit(type).damage(), level) * (1.0 + perLevel * attackUpgrade);
	}

	/** Faktor für erlittenen Schaden durch das Rüstungs-Upgrade, z. B. 0,92 auf Stufe 1. */
	public double armorMultiplier(int armorUpgrade) {
		double perLevel = balance.upgrade("armor").valuePerLevel();
		return Math.max(MIN_DAMAGE_TAKEN, 1.0 - perLevel * armorUpgrade);
	}

	/** Bewegungstempo (Attributwert) inklusive Veteranenbonus. */
	public double speed(UnitType type, int level) {
		return leveling.scaledSpeed(balance.unit(type).speed(), level);
	}

	/** Angriffsreichweite in Blöcken: Fernkampf mit Reichweiten-Upgrade, sonst die Nahkampfreichweite. */
	public double attackRange(UnitType type, int rangeUpgrade) {
		Balance.UnitStats stats = balance.unit(type);
		if (!stats.ranged()) {
			return balance.traits().meleeReach();
		}
		return stats.range() + balance.upgrade("range").valuePerLevel() * rangeUpgrade;
	}

	/** Ticks zwischen zwei Angriffen; Blutrausch verkürzt die Pause. */
	public int attackCooldownTicks(UnitType type, boolean bloodlust) {
		int base = balance.unit(type).attackCooldownTicks();
		if (!bloodlust) {
			return base;
		}
		double bonus = balance.ability("bloodlust").amount();
		return Math.max(1, (int) Math.round(base / (1.0 + bonus)));
	}

	/**
	 * Schadensfaktor für den Krieger-Block: Angriffe aus dem vorderen Sichtkegel werden abgeschwächt.
	 *
	 * @param angleDegrees Winkel zwischen Blickrichtung des Kriegers und der Richtung zum Angreifer (0 = genau vorne)
	 */
	public double warriorBlockMultiplier(double angleDegrees) {
		Balance.Traits traits = balance.traits();
		if (Math.abs(angleDegrees) <= traits.warriorBlockAngleDegrees()) {
			return 1.0 - traits.warriorFrontalBlock();
		}
		return 1.0;
	}

	public double assassinOpeningMultiplier() {
		return balance.traits().assassinOpeningStrikeMultiplier();
	}

	/** Turmschaden je Turm-Ausbaustufe. */
	public double towerDamage(int towerUpgrade) {
		return balance.stronghold().towerDamage() + balance.upgrade("tower").valuePerLevel() * towerUpgrade;
	}

	public double towerRange(int towerUpgrade) {
		return balance.stronghold().towerRange() + balance.stronghold().towerRangePerLevel() * towerUpgrade;
	}

	public int towerShotIntervalTicks() {
		return Math.max(1, (int) Math.round(20.0 / balance.stronghold().towerShotsPerSecond()));
	}
}
