package io.github.jan1a234.goblinforest.game;

/**
 * Einstellungen eines Matches: Übungsmodus (leerer Gegner erlaubt), KI-Gegner und Best-of-Serie.
 *
 * @param practice ein Clan darf leer bleiben
 * @param ai       Schwierigkeit des KI-Clans oder null ohne KI
 * @param series   laufende Serie oder null für ein einzelnes Match
 */
public record MatchOptions(boolean practice, AiDifficulty ai, Series series) {
	public static MatchOptions single(boolean practice) {
		return new MatchOptions(practice, null, null);
	}

	public int bestOf() {
		return series == null ? 1 : series.bestOf();
	}
}
