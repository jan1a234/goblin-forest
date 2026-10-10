package io.github.jan1a234.goblinforest.hero;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.jan1a234.goblinforest.config.BalanceLoader;
import org.junit.jupiter.api.Test;

class HeroProgressionTest {
	private final HeroProgression hero = new HeroProgression(BalanceLoader.loadDefaults().hero());

	@Test
	void statsPerLevelFollowDesign() {
		assertEquals(320, hero.maxHealth(1), 1e-9);
		assertEquals(360, hero.maxHealth(2), 1e-9);
		assertEquals(16, hero.damage(1), 1e-9);
		assertEquals(38.5, hero.damage(10), 1e-9);
		// 12 s + 2 s pro Level.
		assertEquals(14, hero.respawnSeconds(1));
		assertEquals(32, hero.respawnSeconds(10));
	}

	@Test
	void progressBetweenLevels() {
		assertEquals(0.0, hero.progressToNext(0), 1e-9);
		assertEquals(0.5, hero.progressToNext(30), 1e-9);
		assertEquals(1.0, hero.progressToNext(1_000_000), 1e-9);
		assertEquals(10, hero.levelForXp(1_000_000));
	}
}
