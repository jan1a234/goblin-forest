package io.github.jan1a234.goblinforest.client;

import io.github.jan1a234.goblinforest.arena.ArenaLayout;
import io.github.jan1a234.goblinforest.game.MatchPhase;
import io.github.jan1a234.goblinforest.game.TeamColor;
import io.github.jan1a234.goblinforest.net.ActionPayload;
import io.github.jan1a234.goblinforest.net.MatchStatePayload;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Kommandoansicht (DESIGN.md Abschnitt 10): Die Kamera fährt schräg über das Schlachtfeld, der Häuptling bleibt stehen.
 * Mit den Bewegungstasten schwenkt man über die Lane, Bild↑/Bild↓ zoomt. Zauber und Sammelpunkt zielen auf die Bildmitte;
 * der Client meldet sie dafür regelmäßig an den Server. Die eigene Festung liegt immer links im Bild.
 *
 * <p>Die Kamera selbst setzt {@code CameraMixin}; hier wird nur berechnet, wo sie stehen soll.
 */
public final class CommandView {
	private static final double MIN_DISTANCE = 24;
	private static final double MAX_DISTANCE = 72;
	private static final double DEFAULT_DISTANCE = 42;
	private static final double ZOOM_STEP = 6;
	/** Neigung nach unten in Grad. */
	private static final float PITCH = 60.0F;
	/** Schwenkgeschwindigkeit in Blöcken pro Tick bei Standardabstand. */
	private static final double PAN_SPEED = 1.1;
	/** So lange nach einem eigenen Umschalten wird der (womöglich veraltete) Serverstand ignoriert. */
	private static final int SYNC_GRACE_TICKS = 30;

	/** Kamerapose für einen Frame. */
	public record Pose(double x, double y, double z, float yaw, float pitch) {
	}

	private static boolean active;
	private static long toggledAt = -1000;
	private static double focusX, focusZ, prevFocusX, prevFocusZ;
	private static double distance = DEFAULT_DISTANCE, prevDistance = DEFAULT_DISTANCE, targetDistance = DEFAULT_DISTANCE;
	private static double sentX = Double.NaN, sentZ = Double.NaN;
	private static long lastSent;

	private CommandView() {
	}

	public static boolean active() {
		return active;
	}

	private static boolean allowed(MatchStatePayload s) {
		return s.inMatch() && s.phase() == MatchPhase.BATTLE.ordinal() && s.respawnSeconds() <= 0;
	}

	static void toggle(Minecraft client) {
		MatchStatePayload s = ClientMatchState.get();
		if (!active && (!allowed(s) || client.player == null)) {
			return;
		}
		active = !active;
		toggledAt = ClientMatchState.ticks();
		if (active) {
			focusX = prevFocusX = client.player.getX();
			focusZ = prevFocusZ = Mth.clamp(client.player.getZ(), -ArenaLayout.HALF_WIDTH + 4, ArenaLayout.HALF_WIDTH - 4);
			distance = prevDistance = targetDistance;
			sentX = sentZ = Double.NaN;
		}
		ClientPlayNetworking.send(new ActionPayload(active ? "command_view:on" : "command_view:off"));
	}

	static void zoom(boolean in) {
		targetDistance = Mth.clamp(targetDistance + (in ? -ZOOM_STEP : ZOOM_STEP), MIN_DISTANCE, MAX_DISTANCE);
	}

	static void reset() {
		active = false;
	}

	static void tick(Minecraft client) {
		MatchStatePayload s = ClientMatchState.get();
		long now = ClientMatchState.ticks();
		if (!allowed(s) || client.player == null) {
			active = false;
			return;
		}
		// Der Server hat das letzte Wort (z. B. nach dem Tod des Häuptlings), aber erst nach einer kurzen Schonfrist.
		if (now - toggledAt > SYNC_GRACE_TICKS && s.commandView() != active) {
			active = s.commandView();
			if (active) {
				focusX = prevFocusX = client.player.getX();
				focusZ = prevFocusZ = client.player.getZ();
			}
		}
		if (!active) {
			return;
		}
		prevFocusX = focusX;
		prevFocusZ = focusZ;
		prevDistance = distance;
		distance += (targetDistance - distance) * 0.25;

		if (client.gui.screen() == null) {
			int right = (client.options.keyRight.isDown() ? 1 : 0) - (client.options.keyLeft.isDown() ? 1 : 0);
			int up = (client.options.keyUp.isDown() ? 1 : 0) - (client.options.keyDown.isDown() ? 1 : 0);
			double speed = PAN_SPEED * distance / DEFAULT_DISTANCE * (client.options.keySprint.isDown() ? 2.0 : 1.0);
			// Rot blickt nach Norden (rechts = Osten), Grün nach Süden (rechts = Westen).
			int flip = team(s) == TeamColor.GREEN ? -1 : 1;
			focusX += right * speed * flip;
			focusZ -= up * speed * flip;
		}
		focusX = Mth.clamp(focusX, -ArenaLayout.HALF_LENGTH + 2, ArenaLayout.HALF_LENGTH - 2);
		focusZ = Mth.clamp(focusZ, -ArenaLayout.HALF_WIDTH + 2, ArenaLayout.HALF_WIDTH - 2);

		boolean moved = Double.isNaN(sentX) || Math.abs(focusX - sentX) > 0.4 || Math.abs(focusZ - sentZ) > 0.4;
		if (moved && now - lastSent >= 3) {
			sentX = focusX;
			sentZ = focusZ;
			lastSent = now;
			ClientPlayNetworking.send(new ActionPayload(String.format(Locale.ROOT, "command_focus:%.2f:%.2f", focusX, focusZ)));
		}
	}

	private static TeamColor team(MatchStatePayload s) {
		return s.team() == TeamColor.GREEN.ordinal() ? TeamColor.GREEN : TeamColor.RED;
	}

	/** Wo die Kamera in diesem Frame steht, oder null, wenn die Kommandoansicht aus ist. */
	public static Pose pose(float partialTicks) {
		if (!active) {
			return null;
		}
		double fx = Mth.lerp(partialTicks, prevFocusX, focusX);
		double fz = Mth.lerp(partialTicks, prevFocusZ, focusZ);
		double dist = Mth.lerp(partialTicks, prevDistance, distance);
		float yaw = team(ClientMatchState.get()) == TeamColor.GREEN ? 0.0F : 180.0F;
		double pitch = Math.toRadians(PITCH);
		double yawRad = Math.toRadians(yaw);
		// Blickrichtung wie bei Minecraft-Wesen: (-sin(yaw)·cos(pitch), -sin(pitch), cos(yaw)·cos(pitch)).
		double dx = -Math.sin(yawRad) * Math.cos(pitch);
		double dy = -Math.sin(pitch);
		double dz = Math.cos(yawRad) * Math.cos(pitch);
		double fy = ArenaLayout.GROUND_Y + 1;
		return new Pose(fx - dx * dist, fy - dy * dist, fz - dz * dist, yaw, PITCH);
	}
}
