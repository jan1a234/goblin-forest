package io.github.jan1a234.goblinforest.game;

/** Zustände eines Matches (DESIGN.md Abschnitt 3): Lobby → Aufbau → Countdown → Kampf → Ende. */
public enum MatchPhase {
	LOBBY,
	SETUP,
	COUNTDOWN,
	BATTLE,
	ENDED
}
