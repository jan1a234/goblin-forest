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

	/** Bewegungstempo mit Veteranenbonus und dem Tempofaktor der Armee (Ausdauer-Upgrade). */
	public double speed(UnitType type, int level, double armyMultiplier) {
		return speed(type, level) * Math.max(0.1, armyMultiplier);
	}

	/** Schadensfaktor gegen Gebäude: Trolle reißen Mauern ein. */
	public double structureMultiplier(UnitType type) {
		return type == UnitType.TROLL ? balance.traits().trollStructureMultiplier() : 1.0;
	}

	/** Ist das Level ein Champion (höchste Veteranenstufe) mit Spezialfähigkeit? */
	public boolean isChampion(int level) {
		return level >= leveling.maxLevel();
	}

	/**
	 * Heilung eines Schamanen pro Puls: Grundwert mit Veteranen- und Angriffsbonus, Champions heilen mehr.
	 */
	public double shamanHeal(int level, int attackUpgrade) {
		double heal = balance.traits().shamanHealAmount() * (1.0 + leveling.step(level).damageBonus())
				* (1.0 + balance.upgrade("attack").valuePerLevel() * attackUpgrade);
		return isChampion(level) ? heal * balance.unitLeveling().champion().shamanHealMultiplier() : heal;
	}

	public double shamanHealRadius(int level) {
		double radius = balance.traits().shamanHealRadius();
		return isChampion(level) ? radius + balance.unitLeveling().champion().shamanRadiusBonus() : radius;
	}

	/** Schaden eines Katapult-Einschlags an Einheiten im Umkreis (Anteil des Gebäudeschadens). */
	public double catapultSplashDamage(double damage) {
		return damage * balance.traits().catapultUnitDamageShare();
	}

	/** Schaden der Festungskanone für eine Kanonenstufe (1 = gerade gebaut). */
	public double cannonDamage(int cannonLevel) {
		return balance.stronghold().cannonDamage() + balance.upgrade("cannon").valuePerLevel() * Math.max(0, cannonLevel - 1);
	}

	/** Ticks zwischen zwei Kanonenschüssen für eine Kanonenstufe. */
	public int cannonReloadTicks(int cannonLevel) {
		Balance.Stronghold stronghold = balance.stronghold();
		double seconds = stronghold.cannonReloadSeconds() - stronghold.cannonReloadPerLevel() * Math.max(0, cannonLevel - 1);
		return Math.max(10, (int) Math.round(seconds * 20));
	}

	/** Angriffsreichweite in Blöcken: Fernkampf mit Reichweiten-Upgrade, sonst die Nahkampfreichweite. */
	public double attackRange(UnitType type, int rangeUpgrade) {
		Balance.UnitStats stats = balance.unit(type);
		if (!stats.ranged()) {
			return balance.traits().meleeReach();
		}
		return stats.range() + balance.upgrade("range").valuePerLevel() * rangeUpgrade;
	}

	/** Ticks zwischen zwei Angriffen ohne Blutrausch. */
	public int attackCooldownTicks(UnitType type) {
		return balance.unit(type).attackCooldownTicks();
	}

	/**
	 * Ticks zwischen zwei Angriffen; Blutrausch verkürzt die Pause um den Bonus des Rangs, mit dem er gewirkt wurde.
	 *
	 * @param bloodlustBonus Angriffstempo-Bonus (0.3 = +30 %) oder 0 ohne Blutrausch
	 */
	public int attackCooldownTicks(UnitType type, double bloodlustBonus) {
		int base = attackCooldownTicks(type);
		if (bloodlustBonus <= 0) {
			return base;
		}
		return Math.max(1, (int) Math.round(base / (1.0 + bloodlustBonus)));
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
