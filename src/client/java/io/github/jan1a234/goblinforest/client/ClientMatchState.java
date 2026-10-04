package io.github.jan1a234.goblinforest.client;

import io.github.jan1a234.goblinforest.net.MatchStatePayload;

/** Letzter vom Server empfangener Match-Zustand; HUD und Kriegsmenü lesen nur von hier. */
public final class ClientMatchState {
	private static MatchStatePayload state = MatchStatePayload.none();
	private static int goldGain;
	private static long goldGainTime = -1000;
	private static long ticks;

	private ClientMatchState() {
	}

	public static MatchStatePayload get() {
		return state;
	}

	public static boolean active() {
		return state.inMatch();
	}

	static void update(MatchStatePayload next) {
		if (state.inMatch() && next.inMatch() && next.gold() > state.gold()) {
			goldGain = next.gold() - state.gold() + (ticks - goldGainTime < 30 ? goldGain : 0);
			goldGainTime = ticks;
		}
		state = next;
	}

	static void reset() {
		state = MatchStatePayload.none();
	}

	static void tick() {
		ticks++;
	}

	public static long ticks() {
		return ticks;
	}

	/** Zuletzt dazugekommenes Gold, solange es noch angezeigt werden soll, sonst 0. */
	public static int recentGoldGain() {
		return ticks - goldGainTime < 30 ? goldGain : 0;
	}
}
