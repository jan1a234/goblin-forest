package io.github.jan1a234.goblinforest.config;

import io.github.jan1a234.goblinforest.unit.UnitType;
import java.util.List;
import java.util.Map;

/**
 * Alle Balancing-Werte der Mod, eins zu eins aus data/goblinforest/balance.json gelesen.
 * Neue Werte hier als Feld ergänzen und in der JSON-Datei eintragen.
 */
public record Balance(
		Economy economy,
		Map<String, UnitStats> units,
		UnitLeveling unitLeveling,
		Stronghold stronghold,
		Hero hero
) {
	public UnitStats unit(UnitType type) {
		UnitStats stats = units.get(type.id());
		if (stats == null) {
			throw new IllegalStateException("Keine Balancing-Werte für Einheit " + type.id());
		}
		return stats;
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
			int clanXpPerReputation
	) {
	}

	/** Werte einer Einheit auf Level 1, ohne Upgrades. {@code range} 0 bedeutet Nahkampf. */
	public record UnitStats(
			int cost,
			int population,
			int groupSize,
			double health,
			double damage,
			double range,
			double speed,
			int unlockReputation
	) {
	}

	public record UnitLeveling(
			int xpPerKill,
			double xpPerDamage,
			int xpPerBuildingHit,
			List<LevelStep> levels
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

	public record Stronghold(
			double coreHealth,
			double towerDamage,
			double towerRange,
			double towerShotsPerSecond
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
			int respawnSecondsPerLevel
	) {
	}
}
