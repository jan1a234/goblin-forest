package io.github.jan1a234.goblinforest.config;

import io.github.jan1a234.goblinforest.unit.UnitType;
import java.util.List;
import java.util.Map;

/**
 * Alle Balancing-Werte der Mod, eins zu eins aus data/goblinforest/balance.json gelesen.
 * Neue Werte hier als Feld ergänzen und in der JSON-Datei eintragen.
 */
public record Balance(
		Match match,
		Economy economy,
		Map<String, UnitStats> units,
		Traits traits,
		UnitLeveling unitLeveling,
		Map<String, UpgradeTrack> upgrades,
		Stronghold stronghold,
		Hero hero,
		Map<String, Ability> abilities,
		Rage rage,
		Map<String, Spell> spells
) {
	public UnitStats unit(UnitType type) {
		UnitStats stats = units.get(type.id());
		if (stats == null) {
			throw new IllegalStateException("Keine Balancing-Werte für Einheit " + type.id());
		}
		return stats;
	}

	public UpgradeTrack upgrade(String id) {
		UpgradeTrack track = upgrades.get(id);
		if (track == null) {
			throw new IllegalStateException("Keine Balancing-Werte für Upgrade " + id);
		}
		return track;
	}

	public Ability ability(String id) {
		Ability ability = abilities.get(id);
		if (ability == null) {
			throw new IllegalStateException("Keine Balancing-Werte für Fähigkeit " + id);
		}
		return ability;
	}

	public Spell spell(String id) {
		Spell spell = spells.get(id);
		if (spell == null) {
			throw new IllegalStateException("Keine Balancing-Werte für Zauber " + id);
		}
		return spell;
	}

	/**
	 * Ablauf eines Matches. Sudden Death: ab {@code suddenDeathMinutes} Kampfzeit verlieren beide Festungskerne
	 * jede Sekunde {@code suddenDeathPercentPerSecond} ihrer maximalen Lebenspunkte.
	 */
	public record Match(
			int countdownSeconds,
			int endDelaySeconds,
			int maxUnitsPerTeam,
			int suddenDeathMinutes,
			double suddenDeathPercentPerSecond,
			int roundBreakSeconds
	) {
		public long suddenDeathTicks() {
			return suddenDeathMinutes * 60L * 20L;
		}
	}

	public record Economy(
			int startGold,
			double passiveGoldPerSecond,
			double bountyShareOfCost,
			double frontFactorMin,
			double frontFactorMax,
			double ownLossBountyShare,
			double veteranBountyBonusPerLevel,
			int heroKillBounty,
			int startPopulationLimit,
			int clanXpPerReputation,
			double clanXpShareOfCost,
			int heroKillClanXp,
			int maxReputation,
			int reputationPerTowerKill
	) {
	}

	/**
	 * Werte einer Einheit auf Level 1, ohne Upgrades. {@code range} 0 bedeutet Nahkampf.
	 * {@code cost} gilt für die ganze Gruppe aus {@code groupSize} Einheiten, {@code population} pro Einheit.
	 * {@code scale} ist die Körpergröße (1 = normal) und bestimmt auch die Trefferbox.
	 */
	public record UnitStats(
			int cost,
			int population,
			int groupSize,
			double health,
			double damage,
			double range,
			double speed,
			int unlockReputation,
			int attackCooldownTicks,
			double aggroRange,
			double scale
	) {
		public boolean ranged() {
			return range > 0;
		}

		/** Anteil der Gruppenkosten, der auf eine einzelne Einheit entfällt (Grundlage für Kopfgeld). */
		public double costPerUnit() {
			return (double) cost / Math.max(1, groupSize);
		}
	}

	/** Besonderheiten einzelner Einheitentypen (DESIGN.md Abschnitt 5.1). */
	public record Traits(
			double warriorFrontalBlock,
			double warriorBlockAngleDegrees,
			double assassinOpeningStrikeMultiplier,
			int assassinStealthSeconds,
			double meleeReach,
			double retreatHealPercentPerSecond,
			double shamanHealAmount,
			double shamanHealRadius,
			int shamanHealIntervalTicks,
			int shamanHealTargets,
			double trollStructureMultiplier,
			double trollKnockback,
			double trollCleaveRadius,
			double trollCleaveShare,
			double wolfChargeMultiplier,
			double wolfChargeKnockback,
			int wolfChargeRechargeSeconds,
			double catapultSplashRadius,
			double catapultUnitDamageShare
	) {
	}

	/** Spezialfähigkeiten ab dem höchsten Veteranen-Level (Champion, DESIGN.md Abschnitt 5.2). */
	public record Champion(
			double slaveExplosionDamage,
			double slaveExplosionRadius,
			double warriorAuraArmor,
			double warriorAuraRadius,
			int archerMultishotTargets,
			int assassinRestealthSeconds,
			double shamanHealMultiplier,
			double shamanRadiusBonus,
			double trollRegenPercentPerSecond,
			int catapultShots
	) {
	}

	public record UnitLeveling(
			int xpPerKill,
			double xpPerDamage,
			int xpPerBuildingHit,
			List<LevelStep> levels,
			Champion champion
	) {
	}

	/** Ein Veteranen-Level; die Boni sind kumulativ gegenüber Level 1 (0.10 = +10 %). */
	public record LevelStep(
			String title,
			int xpRequired,
			double healthBonus,
			double damageBonus,
			double speedBonus
	) {
	}

	/**
	 * Eine kaufbare Ausbaustufe: {@code costs[i]} und {@code unlockReputation[i]} gelten für Stufe i+1.
	 * {@code valuePerLevel} ist die Wirkung pro Stufe (Bedeutung je Upgrade, siehe UpgradeType).
	 */
	public record UpgradeTrack(
			List<Integer> costs,
			double valuePerLevel,
			List<Integer> unlockReputation
	) {
		public int maxLevel() {
			return costs.size();
		}
	}

	/**
	 * Festung. Die Kanone wird über das Upgrade {@code cannon} gekauft (Stufe 1 = gebaut); jede weitere Stufe
	 * bringt {@code upgrades.cannon.valuePerLevel} Schaden und {@code cannonReloadPerLevel} Sekunden schnelleres Nachladen.
	 */
	public record Stronghold(
			double coreHealth,
			double towerHealth,
			double towerDamage,
			double towerRange,
			double towerShotsPerSecond,
			double towerRangePerLevel,
			double structureAttackReach,
			double cannonDamage,
			double cannonRadius,
			double cannonRange,
			double cannonReloadSeconds,
			double cannonReloadPerLevel
	) {
	}

	public record Hero(
			double baseHealth,
			double healthPerLevel,
			double baseDamage,
			double damagePerLevel,
			int maxLevel,
			double armyXpShare,
			int respawnBaseSeconds,
			int respawnSecondsPerLevel,
			List<Integer> xpForLevel
	) {
	}

	/**
	 * Heldenfähigkeit. {@code amount}: Blutrausch = Angriffstempo-Bonus, Kampfstampfer = Schaden,
	 * Raserei = zusätzlicher Schadensfaktor (1.0 = doppelter Schaden).
	 * Mit Fähigkeitspunkten steigt der Rang (0 bis {@code maxRank}); jeder Rang addiert die {@code ...PerRank}-Werte.
	 */
	public record Ability(
			int cooldownSeconds,
			double radius,
			int durationSeconds,
			double amount,
			int maxRank,
			double amountPerRank,
			double radiusPerRank,
			double durationPerRank,
			double cooldownPerRank
	) {
		public double amountAt(int rank) {
			return amount + amountPerRank * rank;
		}

		public double radiusAt(int rank) {
			return radius + radiusPerRank * rank;
		}

		public int durationTicksAt(int rank) {
			return (int) Math.round((durationSeconds + durationPerRank * rank) * 20);
		}

		public int cooldownTicksAt(int rank) {
			return (int) Math.max(20, Math.round((cooldownSeconds - cooldownPerRank * rank) * 20));
		}
	}

	/**
	 * Raserei lädt sich durch Schaden auf, den der Häuptling austeilt oder einsteckt. Ist sie voll, kann sie
	 * gezündet werden: mehr Schaden ({@code abilities.rage.amount}), mehr Tempo und Lebensraub.
	 */
	public record Rage(
			double maxCharge,
			double chargePerDamageDealt,
			double chargePerDamageTaken,
			double speedBonus,
			double lifesteal,
			double lifestealPerRank
	) {
	}

	/**
	 * Zauber. {@code amount}: Schaden, Heilung bzw. Dauer in Sekunden (Wurzelfessel) auf Stufe 1, jede weitere Stufe
	 * gibt {@code bonusPerLevel} dazu. {@code count}: Anzahl der Blitze beim Blitzsturm.
	 * {@code upgradeCosts[i]} und {@code upgradeReputation[i]} gelten für Zauberstufe i+2.
	 */
	public record Spell(
			int cost,
			int cooldownSeconds,
			int unlockReputation,
			double radius,
			double amount,
			double range,
			List<Integer> upgradeCosts,
			List<Integer> upgradeReputation,
			double bonusPerLevel,
			int count
	) {
		public int maxLevel() {
			return upgradeCosts.size() + 1;
		}

		public double amountAtLevel(int level) {
			return amount * (1.0 + bonusPerLevel * (Math.max(1, level) - 1));
		}
	}
}
