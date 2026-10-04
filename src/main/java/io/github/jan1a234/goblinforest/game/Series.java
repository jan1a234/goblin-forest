package io.github.jan1a234.goblinforest.game;

import java.util.EnumMap;

/**
 * Best-of-Serie über mehrere Runden (z. B. Best of 3: wer zuerst zwei Runden gewinnt).
 * Eine Serie überlebt die einzelnen Matches; jede Runde ist ein neues {@link Match} mit frischer Arena.
 */
public final class Series {
	private final int bestOf;
	private final EnumMap<TeamColor, Integer> wins = new EnumMap<>(TeamColor.class);
	private int round = 1;

	public Series(int bestOf) {
		if (bestOf < 1 || bestOf % 2 == 0) {
			throw new IllegalArgumentException("Best of muss ungerade und mindestens 1 sein: " + bestOf);
		}
		this.bestOf = bestOf;
		for (TeamColor team : TeamColor.values()) {
			wins.put(team, 0);
		}
	}

	public int bestOf() {
		return bestOf;
	}

	/** Nummer der laufenden Runde, ab 1. */
	public int round() {
		return round;
	}

	public int wins(TeamColor team) {
		return wins.get(team);
	}

	public int winsNeeded() {
		return bestOf / 2 + 1;
	}

	/** Trägt den Sieger einer Runde ein. */
	public void record(TeamColor winner) {
		if (decided()) {
			return;
		}
		wins.merge(winner, 1, Integer::sum);
		if (!decided()) {
			round++;
		}
	}

	public boolean decided() {
		return champion() != null;
	}

	/** Gewinner der Serie oder null, solange sie läuft. */
	public TeamColor champion() {
		for (TeamColor team : TeamColor.values()) {
			if (wins.get(team) >= winsNeeded()) {
				return team;
			}
		}
		return null;
	}
}
