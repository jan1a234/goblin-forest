package io.github.jan1a234.goblinforest.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SeriesTest {
	@Test
	void bestOfThreeEndsAfterTwoWins() {
		Series series = new Series(3);
		assertEquals(2, series.winsNeeded());
		assertEquals(1, series.round());
		series.record(TeamColor.RED);
		assertFalse(series.decided());
		assertEquals(2, series.round());
		series.record(TeamColor.GREEN);
		assertFalse(series.decided());
		assertEquals(3, series.round());
		series.record(TeamColor.RED);
		assertTrue(series.decided());
		assertEquals(TeamColor.RED, series.champion());
		assertEquals(3, series.round());
		assertEquals(2, series.wins(TeamColor.RED));
		assertEquals(1, series.wins(TeamColor.GREEN));
	}

	@Test
	void sweepEndsEarly() {
		Series series = new Series(5);
		for (int i = 0; i < 3; i++) {
			assertNull(series.champion());
			series.record(TeamColor.GREEN);
		}
		assertEquals(TeamColor.GREEN, series.champion());
		// Nach der Entscheidung zählt nichts mehr.
		series.record(TeamColor.RED);
		assertEquals(0, series.wins(TeamColor.RED));
	}

	@Test
	void rejectsEvenSeries() {
		assertThrows(IllegalArgumentException.class, () -> new Series(2));
		assertThrows(IllegalArgumentException.class, () -> new Series(0));
	}

	@Test
	void parsesDifficultyInBothLanguages() {
		assertEquals(AiDifficulty.EASY, AiDifficulty.parse("leicht"));
		assertEquals(AiDifficulty.EASY, AiDifficulty.parse("Easy"));
		assertEquals(AiDifficulty.NORMAL, AiDifficulty.parse("normal"));
		assertEquals(AiDifficulty.HARD, AiDifficulty.parse("SCHWER"));
		assertEquals(AiDifficulty.HARD, AiDifficulty.parse("hard"));
		assertNull(AiDifficulty.parse("bo3"));
	}

	@Test
	void aiIncomeMultiplierScalesOnlyBaseIncome() {
		TeamState team = new TeamState(TeamColor.GREEN, io.github.jan1a234.goblinforest.config.BalanceLoader.loadDefaults());
		team.setIncomeMultiplier(AiDifficulty.HARD.incomeMultiplier());
		assertEquals(2.0 * 1.4, team.incomePerSecond(), 1e-9);
	}
}
