package io.github.jan1a234.goblinforest.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.jan1a234.goblinforest.config.Balance;
import io.github.jan1a234.goblinforest.config.BalanceLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class UnitLevelingTest {
	private static Balance balance;
	private static UnitLeveling leveling;

	@BeforeAll
	static void load() {
		balance = BalanceLoader.loadDefaults();
		leveling = new UnitLeveling(balance.unitLeveling());
	}

	@Test
	void everyUnitTypeHasStats() {
		for (UnitType type : UnitType.soldiers()) {
			assertEquals(true, balance.unit(type).cost() > 0, type.id());
		}
	}

	@Test
	void levelThresholdsMatchDesign() {
		assertEquals(1, leveling.levelForXp(0));
		assertEquals(1, leveling.levelForXp(19.9));
		assertEquals(2, leveling.levelForXp(20));
		assertEquals(3, leveling.levelForXp(60));
		assertEquals(4, leveling.levelForXp(140));
		assertEquals(5, leveling.levelForXp(300));
		assertEquals(5, leveling.levelForXp(100_000));
	}

	@Test
	void bonusesScaleStats() {
		double warriorHealth = balance.unit(UnitType.WARRIOR).health();
		assertEquals(110.0, leveling.scaledHealth(warriorHealth, 1), 1e-9);
		assertEquals(137.5, leveling.scaledHealth(warriorHealth, 3), 1e-9);
		assertEquals(15.0, leveling.scaledDamage(10, 5), 1e-9);
		assertEquals(0.275, leveling.scaledSpeed(0.25, 4), 1e-9);
	}

	@Test
	void damageGrantsXp() {
		assertEquals(1.0, leveling.xpForDamage(10), 1e-9);
	}
}
