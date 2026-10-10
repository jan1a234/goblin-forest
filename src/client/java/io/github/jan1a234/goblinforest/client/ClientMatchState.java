package io.github.jan1a234.goblinforest.client;

import io.github.jan1a234.goblinforest.arena.ArenaLayout;
import io.github.jan1a234.goblinforest.game.MatchPhase;
import io.github.jan1a234.goblinforest.game.TeamColor;
import io.github.jan1a234.goblinforest.net.MatchStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Letzter vom Server empfangener Match-Zustand; HUD und Kriegsmenü lesen nur von hier. */
public final class ClientMatchState {
	private static MatchStatePayload state = MatchStatePayload.none();
	private static int goldGain;
	private static long goldGainTime = -1000;
	private static long ticks;
	/** Hat der Häuptling in dieser Runde die eigene Festung schon verlassen? Bis dahin zeigt das HUD den Weg hinaus. */
	private static boolean leftFortress;

	private ClientMatchState() {
	}

	public static MatchStatePayload get() {
		return state;
	}

	public static boolean active() {
		return state.inMatch();
	}

	static void update(MatchStatePayload next) {
		// Nur echte Einnahmen (Kopfgeld, Rückerstattung) anzeigen, nicht das laufende Grundeinkommen.
		int gain = next.gold() - state.gold();
		if (state.inMatch() && next.inMatch() && gain > Math.ceil(next.income())) {
			goldGain = gain + (ticks - goldGainTime < 30 ? goldGain : 0);
			goldGainTime = ticks;
		}
		state = next;
	}

	static void reset() {
		state = MatchStatePayload.none();
		leftFortress = false;
	}

	static void tick(Minecraft client) {
		ticks++;
		if (!state.inMatch() || state.phase() != MatchPhase.BATTLE.ordinal() || state.team() >= TeamColor.values().length) {
			// Neue Runde oder neues Match: der Hinweis erscheint wieder.
			leftFortress = false;
			return;
		}
		if (!leftFortress && client.player != null && state.respawnSeconds() <= 0) {
			BlockPos pos = client.player.blockPosition();
			leftFortress = !ArenaLayout.healZone(TeamColor.values()[state.team()]).contains(pos.getX(), pos.getY(), pos.getZ());
		}
	}

	/** Steht der Häuptling noch in der eigenen Festung, seit die Schlacht (bzw. Runde) begonnen hat? */
	public static boolean stillInFortress() {
		return state.inMatch() && !leftFortress;
	}

	public static long ticks() {
		return ticks;
	}

	/** Zuletzt dazugekommenes Gold, solange es noch angezeigt werden soll, sonst 0. */
	public static int recentGoldGain() {
		return ticks - goldGainTime < 30 ? goldGain : 0;
	}
}
