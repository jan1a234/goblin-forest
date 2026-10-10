package io.github.jan1a234.goblinforest.game;

/**
 * Einstellungen eines Matches: Übungsmodus (leerer Gegner erlaubt), KI-Gegner, Best-of-Serie und Sudden Death.
 *
 * @param practice    ein Clan darf leer bleiben
 * @param ai          Schwierigkeit des KI-Clans oder null ohne KI
 * @param series      laufende Serie oder null für ein einzelnes Match
 * @param suddenDeath nach {@code match.suddenDeathMinutes} beginnen beide Festungen zu bröckeln; sonst läuft das Match
 *                    ohne Zeitlimit, bis eine Festung fällt (Standard)
 */
public record MatchOptions(boolean practice, AiDifficulty ai, Series series, boolean suddenDeath) {
	public static MatchOptions single(boolean practice) {
		return new MatchOptions(practice, null, null, false);
	}

	public int bestOf() {
		return series == null ? 1 : series.bestOf();
	}
}
