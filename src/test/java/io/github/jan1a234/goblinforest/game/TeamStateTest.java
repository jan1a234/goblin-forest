package io.github.jan1a234.goblinforest.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jan1a234.goblinforest.config.Balance;
import io.github.jan1a234.goblinforest.config.BalanceLoader;
import io.github.jan1a234.goblinforest.hero.AbilityType;
import io.github.jan1a234.goblinforest.spell.SpellType;
import io.github.jan1a234.goblinforest.unit.UnitType;
import io.github.jan1a234.goblinforest.upgrade.UpgradeKey;
import io.github.jan1a234.goblinforest.upgrade.UpgradeType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TeamStateTest {
	private static Balance balance;

	@BeforeAll
	static void load() {
		balance = BalanceLoader.loadDefaults();
	}

	private TeamState fresh() {
		return new TeamState(TeamColor.RED, balance);
	}

	@Test
	void startsWithDesignValues() {
		TeamState team = fresh();
		assertEquals(150, team.gold());
		assertEquals(20, team.populationLimit());
		assertEquals(0, team.reputation());
		assertEquals(3000, team.coreHealth(), 1e-9);
		assertEquals(1, team.heroLevel());
	}

	@Test
	void passiveIncomeIsTwoGoldPerSecond() {
		TeamState team = fresh();
		for (int i = 0; i < 20 * 10; i++) {
			team.tickIncome();
		}
		assertEquals(170, team.gold());
	}

	@Test
	void recruitingCostsGoldAndPopulation() {
		TeamState team = fresh();
		assertEquals(PurchaseResult.OK, team.recruit(UnitType.SLAVE, 0));
		assertEquals(110, team.gold());
		assertEquals(3, team.population());
		assertEquals(PurchaseResult.OK, team.recruit(UnitType.WARRIOR, 3));
		assertEquals(70, team.gold());
		assertEquals(5, team.population());
	}

	@Test
	void assassinNeedsReputation() {
		TeamState team = fresh();
		team.addGold(1000);
		assertEquals(PurchaseResult.REPUTATION_TOO_LOW, team.recruit(UnitType.ASSASSIN, 0));
		team.addClanXp(400);
		assertEquals(1, team.reputation());
		assertEquals(PurchaseResult.OK, team.recruit(UnitType.ASSASSIN, 0));
	}

	@Test
	void populationLimitBlocksRecruiting() {
		TeamState team = fresh();
		team.addGold(10_000);
		team.setPopulation(19);
		assertEquals(PurchaseResult.POPULATION_FULL, team.checkRecruit(UnitType.WARRIOR, 10));
		assertEquals(PurchaseResult.OK, team.buyUpgrade(UpgradeKey.stronghold(UpgradeType.HUTS)));
		assertEquals(30, team.populationLimit());
		assertEquals(PurchaseResult.OK, team.checkRecruit(UnitType.WARRIOR, 10));
	}

	@Test
	void notEnoughGold() {
		TeamState team = fresh();
		assertEquals(PurchaseResult.NOT_ENOUGH_GOLD, team.checkUpgrade(UpgradeKey.stronghold(UpgradeType.WALLS)));
	}

	@Test
	void upgradesFollowCostTableAndReputation() {
		TeamState team = fresh();
		team.addGold(10_000);
		UpgradeKey armor = UpgradeKey.unit(UpgradeType.ARMOR, UnitType.WARRIOR);
		assertEquals(100, team.nextUpgradeCost(armor));
		assertEquals(PurchaseResult.OK, team.buyUpgrade(armor));
		assertEquals(PurchaseResult.OK, team.buyUpgrade(armor));
		// Stufe 3 braucht Ruf 1.
		assertEquals(PurchaseResult.REPUTATION_TOO_LOW, team.buyUpgrade(armor));
		team.addClanXp(400 * 3);
		assertEquals(PurchaseResult.OK, team.buyUpgrade(armor));
		assertEquals(PurchaseResult.OK, team.buyUpgrade(armor));
		assertEquals(PurchaseResult.OK, team.buyUpgrade(armor));
		assertEquals(5, team.unitUpgrade(UpgradeType.ARMOR, UnitType.WARRIOR));
		assertEquals(PurchaseResult.MAX_LEVEL, team.buyUpgrade(armor));
		// Andere Einheitentypen bleiben unberührt.
		assertEquals(0, team.unitUpgrade(UpgradeType.ARMOR, UnitType.ARCHER));
		assertEquals(10_150 - 100 - 200 - 350 - 550 - 800, team.gold());
	}

	@Test
	void rangeUpgradeOnlyForRangedUnits() {
		TeamState team = fresh();
		team.addGold(1000);
		assertEquals(PurchaseResult.NOT_AVAILABLE, team.checkUpgrade(UpgradeKey.unit(UpgradeType.RANGE, UnitType.WARRIOR)));
		assertEquals(PurchaseResult.OK, team.checkUpgrade(UpgradeKey.unit(UpgradeType.RANGE, UnitType.ARCHER)));
	}

	@Test
	void wallsRaiseCoreHealth() {
		TeamState team = fresh();
		team.addGold(1000);
		team.damageCore(1000);
		assertEquals(PurchaseResult.OK, team.buyUpgrade(UpgradeKey.stronghold(UpgradeType.WALLS)));
		assertEquals(3500, team.coreMaxHealth(), 1e-9);
		assertEquals(2500, team.coreHealth(), 1e-9);
	}

	@Test
	void destroyedTowerCannotBeUpgraded() {
		TeamState team = fresh();
		team.addGold(1000);
		assertFalse(team.damageTower(100));
		assertTrue(team.damageTower(10_000));
		assertFalse(team.towerAlive());
		assertFalse(team.damageTower(1), "Ein zerstörter Turm wird nur einmal gemeldet");
		assertEquals(PurchaseResult.NOT_AVAILABLE, team.checkUpgrade(UpgradeKey.stronghold(UpgradeType.TOWER)));
	}

	@Test
	void towerKillGrantsReputationAndCapApplies() {
		TeamState team = fresh();
		team.addBonusReputation(1);
		assertEquals(1, team.reputation());
		team.addClanXp(1_000_000);
		assertEquals(5, team.reputation());
		assertEquals(1.0, team.reputationProgress(), 1e-9);
	}

	@Test
	void spellsCostGoldAndCooldown() {
		TeamState team = fresh();
		assertEquals(PurchaseResult.OK, team.checkCast(SpellType.FIREBALL, 0));
		team.payCast(SpellType.FIREBALL, 0);
		assertEquals(90, team.gold());
		assertEquals(PurchaseResult.ON_COOLDOWN, team.checkCast(SpellType.FIREBALL, 100));
		assertEquals(PurchaseResult.OK, team.checkCast(SpellType.FIREBALL, 12 * 20));
		assertEquals(PurchaseResult.REPUTATION_TOO_LOW, team.checkCast(SpellType.HEALING, 0));
	}

	@Test
	void spellUpgradesRaiseLevel() {
		TeamState team = fresh();
		team.addGold(1000);
		assertEquals(PurchaseResult.REPUTATION_TOO_LOW, team.buySpellUpgrade(SpellType.FIREBALL));
		team.addClanXp(400);
		assertEquals(PurchaseResult.OK, team.buySpellUpgrade(SpellType.FIREBALL));
		assertEquals(2, team.spellLevel(SpellType.FIREBALL));
		assertEquals(75.0, balance.spell("fireball").amountAtLevel(2), 1e-9);
	}

	@Test
	void abilityCooldown() {
		TeamState team = fresh();
		assertEquals(PurchaseResult.OK, team.checkAbility(AbilityType.BATTLE_SLAM, 0));
		team.startAbilityCooldown(AbilityType.BATTLE_SLAM, 0);
		assertEquals(PurchaseResult.ON_COOLDOWN, team.checkAbility(AbilityType.BATTLE_SLAM, 299));
		assertEquals(PurchaseResult.OK, team.checkAbility(AbilityType.BATTLE_SLAM, 300));
	}

	@Test
	void heroLevelsUp() {
		TeamState team = fresh();
		assertFalse(team.addHeroXp(59));
		assertTrue(team.addHeroXp(1));
		assertEquals(2, team.heroLevel());
		team.addHeroXp(1_000_000);
		assertEquals(10, team.heroLevel());
	}
}
