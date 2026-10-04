package io.github.jan1a234.goblinforest.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.jan1a234.goblinforest.config.BalanceLoader;
import org.junit.jupiter.api.Test;

class UnitCombatTest {
	private final UnitCombat combat = new UnitCombat(BalanceLoader.loadDefaults());

	@Test
	void damageCombinesLevelAndAttackUpgrade() {
		assertEquals(10.0, combat.damage(UnitType.WARRIOR, 1, 0), 1e-9);
		// Level 2 (+10 %) und Angriff Stufe 2 (+20 %): 10 * 1,1 * 1,2.
		assertEquals(13.2, combat.damage(UnitType.WARRIOR, 2, 2), 1e-9);
	}

	@Test
	void armorReducesDamageButNeverCompletely() {
		assertEquals(1.0, combat.armorMultiplier(0), 1e-9);
		assertEquals(0.6, combat.armorMultiplier(5), 1e-9);
		assertEquals(UnitCombat.MIN_DAMAGE_TAKEN, combat.armorMultiplier(100), 1e-9);
	}

	@Test
	void archerRangeGrowsWithUpgrade() {
		assertEquals(14.0, combat.attackRange(UnitType.ARCHER, 0), 1e-9);
		assertEquals(20.0, combat.attackRange(UnitType.ARCHER, 3), 1e-9);
		assertEquals(2.2, combat.attackRange(UnitType.WARRIOR, 3), 1e-9);
	}

	@Test
	void bloodlustSpeedsUpAttacks() {
		assertEquals(22, combat.attackCooldownTicks(UnitType.WARRIOR, false));
		assertEquals(17, combat.attackCooldownTicks(UnitType.WARRIOR, true));
	}

	@Test
	void warriorBlocksOnlyFromTheFront() {
		assertEquals(0.7, combat.warriorBlockMultiplier(10), 1e-9);
		assertEquals(1.0, combat.warriorBlockMultiplier(150), 1e-9);
	}

	@Test
	void towerScalesWithUpgrades() {
		assertEquals(15.0, combat.towerDamage(0), 1e-9);
		assertEquals(30.0, combat.towerDamage(3), 1e-9);
		assertEquals(30.0, combat.towerRange(3), 1e-9);
		assertEquals(20, combat.towerShotIntervalTicks());
	}
}
