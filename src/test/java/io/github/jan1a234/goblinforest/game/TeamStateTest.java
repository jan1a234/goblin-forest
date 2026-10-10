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

	@Test
	void heroLevelsGrantAbilityPoints() {
		TeamState team = fresh();
		assertEquals(0, team.abilityPoints());
		assertEquals(PurchaseResult.NO_ABILITY_POINTS, team.buyAbilityRank(AbilityType.BATTLE_SLAM));
		team.addHeroXp(150);
		assertEquals(3, team.heroLevel());
		assertEquals(2, team.abilityPoints());
		assertEquals(PurchaseResult.OK, team.buyAbilityRank(AbilityType.BATTLE_SLAM));
		assertEquals(PurchaseResult.OK, team.buyAbilityRank(AbilityType.BATTLE_SLAM));
		assertEquals(2, team.abilityRank(AbilityType.BATTLE_SLAM));
		assertEquals(0, team.abilityPoints());
		assertEquals(PurchaseResult.NO_ABILITY_POINTS, team.buyAbilityRank(AbilityType.BLOODLUST));
	}

	@Test
	void abilityRanksAreCapped() {
		TeamState team = fresh();
		team.addHeroXp(100_000);
		for (int i = 0; i < 3; i++) {
			assertEquals(PurchaseResult.OK, team.buyAbilityRank(AbilityType.BLOODLUST));
		}
		assertEquals(PurchaseResult.MAX_LEVEL, team.buyAbilityRank(AbilityType.BLOODLUST));
	}

	@Test
	void abilityRankShortensCooldown() {
		TeamState team = fresh();
		team.addHeroXp(60);
		team.buyAbilityRank(AbilityType.BATTLE_SLAM);
		team.startAbilityCooldown(AbilityType.BATTLE_SLAM, 0);
		// 15 s minus 1 s für Rang 1
		assertEquals(PurchaseResult.ON_COOLDOWN, team.checkAbility(AbilityType.BATTLE_SLAM, 279));
		assertEquals(PurchaseResult.OK, team.checkAbility(AbilityType.BATTLE_SLAM, 280));
	}

	@Test
	void rageChargesInCombatAndThenRuns() {
		TeamState team = fresh();
		assertEquals(PurchaseResult.NOT_CHARGED, team.checkAbility(AbilityType.RAGE, 0));
		team.addRage(60, 0);
		assertEquals(PurchaseResult.NOT_CHARGED, team.checkAbility(AbilityType.RAGE, 0));
		team.addRage(60, 0);
		assertEquals(100, team.rageCharge(), 1e-9);
		assertEquals(PurchaseResult.OK, team.checkAbility(AbilityType.RAGE, 0));
		assertEquals(1.0, team.heroDamageMultiplier(0), 1e-9);
		team.startAbilityCooldown(AbilityType.RAGE, 0);
		assertTrue(team.rageActive(10));
		assertEquals(0, team.rageCharge(), 1e-9);
		assertEquals(1.6, team.heroDamageMultiplier(10), 1e-9);
		assertEquals(PurchaseResult.ON_COOLDOWN, team.checkAbility(AbilityType.RAGE, 10));
		// keine Ladung während der Raserei
		team.addRage(50, 10);
		assertEquals(0, team.rageCharge(), 1e-9);
		assertFalse(team.rageActive(200));
		assertEquals(1.0, team.heroDamageMultiplier(200), 1e-9);
	}

	@Test
	void rageRanksExtendDurationAndLifesteal() {
		TeamState team = fresh();
		team.addHeroXp(60);
		team.buyAbilityRank(AbilityType.RAGE);
		team.addRage(100, 0);
		team.startAbilityCooldown(AbilityType.RAGE, 0);
		assertTrue(team.rageActive(239));
		assertFalse(team.rageActive(240));
		assertEquals(0.25, team.rageLifesteal(), 1e-9);
		assertEquals(1.7, team.heroDamageMultiplier(0), 1e-9);
		team.endRage();
		assertFalse(team.rageActive(1));
	}

	@Test
	void goldmineRaisesIncome() {
		TeamState team = fresh();
		team.addGold(1000);
		assertEquals(PurchaseResult.OK, team.buyUpgrade(UpgradeKey.stronghold(UpgradeType.GOLDMINE)));
		assertEquals(3.0, team.incomePerSecond(), 1e-9);
		int before = team.gold();
		for (int i = 0; i < 20 * 10; i++) {
			team.tickIncome();
		}
		assertEquals(before + 30, team.gold());
	}

	@Test
	void trainingRaisesStartingLevel() {
		TeamState team = fresh();
		team.addGold(5000);
		assertEquals(1, team.startingUnitLevel(5));
		assertEquals(PurchaseResult.REPUTATION_TOO_LOW, team.buyUpgrade(UpgradeKey.stronghold(UpgradeType.TRAINING)));
		team.addClanXp(400 * 3);
		assertEquals(PurchaseResult.OK, team.buyUpgrade(UpgradeKey.stronghold(UpgradeType.TRAINING)));
		assertEquals(2, team.startingUnitLevel(5));
		assertEquals(PurchaseResult.OK, team.buyUpgrade(UpgradeKey.stronghold(UpgradeType.TRAINING)));
		assertEquals(3, team.startingUnitLevel(5));
		assertEquals(3, team.startingUnitLevel(3));
	}

	@Test
	void enduranceBoostsWholeArmy() {
		TeamState team = fresh();
		team.addGold(5000);
		team.addClanXp(400 * 2);
		team.buyUpgrade(UpgradeKey.stronghold(UpgradeType.ENDURANCE));
		team.buyUpgrade(UpgradeKey.stronghold(UpgradeType.ENDURANCE));
		assertEquals(1.2, team.armySpeedMultiplier(), 1e-9);
		assertEquals(1.2, team.armyHealthMultiplier(), 1e-9);
	}

	@Test
	void cannonNeedsReputation() {
		TeamState team = fresh();
		team.addGold(5000);
		assertFalse(team.hasCannon());
		assertEquals(PurchaseResult.REPUTATION_TOO_LOW, team.buyUpgrade(UpgradeKey.stronghold(UpgradeType.CANNON)));
		team.addClanXp(400);
		assertEquals(PurchaseResult.OK, team.buyUpgrade(UpgradeKey.stronghold(UpgradeType.CANNON)));
		assertTrue(team.hasCannon());
	}

	@Test
	void newUnitsUnlockByReputation() {
		TeamState team = fresh();
		team.addGold(5000);
		assertEquals(PurchaseResult.REPUTATION_TOO_LOW, team.checkRecruit(UnitType.SHAMAN, 0));
		assertEquals(PurchaseResult.REPUTATION_TOO_LOW, team.checkRecruit(UnitType.CATAPULT, 0));
		team.addClanXp(400 * 4);
		assertEquals(4, team.reputation());
		for (UnitType type : UnitType.soldiers()) {
			assertEquals(PurchaseResult.OK, team.checkRecruit(type, 0), type.id());
		}
	}

	@Test
	void newSpellsUnlockByReputation() {
		TeamState team = fresh();
		team.addGold(5000);
		assertEquals(PurchaseResult.REPUTATION_TOO_LOW, team.checkCast(SpellType.ROOTS, 0));
		team.addClanXp(400 * 5);
		for (SpellType spell : SpellType.values()) {
			assertEquals(PurchaseResult.OK, team.checkCast(spell, 0), spell.id());
		}
	}
}
