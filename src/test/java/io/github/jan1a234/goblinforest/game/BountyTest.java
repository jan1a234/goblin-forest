package io.github.jan1a234.goblinforest.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.jan1a234.goblinforest.config.Balance;
import io.github.jan1a234.goblinforest.config.BalanceLoader;
import io.github.jan1a234.goblinforest.unit.UnitType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BountyTest {
	private static Balance balance;
	private static Bounty bounty;

	@BeforeAll
	static void load() {
		balance = BalanceLoader.loadDefaults();
		bounty = new Bounty(balance.economy());
	}

	@Test
	void frontFactorIsFlatInOwnHalfAndRisesTowardsEnemy() {
		assertEquals(1.0, bounty.frontFactor(0.0), 1e-9);
		assertEquals(1.0, bounty.frontFactor(0.5), 1e-9);
		assertEquals(1.5, bounty.frontFactor(0.75), 1e-9);
		assertEquals(2.0, bounty.frontFactor(1.0), 1e-9);
		assertEquals(2.0, bounty.frontFactor(3.0), 1e-9);
	}

	@Test
	void warriorBountyFollowsDesign() {
		// Krieger kostet 40: 50 % = 20 Gold in der eigenen Hälfte, 40 Gold direkt an der feindlichen Festung.
		Balance.UnitStats warrior = balance.unit(UnitType.WARRIOR);
		assertEquals(20, bounty.killBounty(warrior, 1, 0.2));
		assertEquals(40, bounty.killBounty(warrior, 1, 1.0));
	}

	@Test
	void veteransAreWorthMore() {
		Balance.UnitStats warrior = balance.unit(UnitType.WARRIOR);
		// Level 3: +50 % (2 Level über 1 je 25 %).
		assertEquals(30, bounty.killBounty(warrior, 3, 0.0));
	}

	@Test
	void slaveBountyIsPerUnitNotPerGroup() {
		Balance.UnitStats slave = balance.unit(UnitType.SLAVE);
		// 40 Gold für 3 Sklaven: pro Sklave 13,3, davon 50 % = 6,7 -> 7.
		assertEquals(7, bounty.killBounty(slave, 1, 0.0));
	}

	@Test
	void ownLossesOnlyPayInEnemyHalf() {
		Balance.UnitStats archer = balance.unit(UnitType.ARCHER);
		assertEquals(0, bounty.ownLossRefund(archer, 1, 0.4));
		// 25 Gold Grundwert * 2,0 Frontfaktor * 25 % = 12,5 -> 13.
		assertEquals(13, bounty.ownLossRefund(archer, 1, 1.0));
	}
}
