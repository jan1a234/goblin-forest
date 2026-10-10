package io.github.jan1a234.goblinforest.client;

import io.github.jan1a234.goblinforest.arena.ArenaLayout;
import io.github.jan1a234.goblinforest.game.MatchPhase;
import io.github.jan1a234.goblinforest.game.TeamColor;
import io.github.jan1a234.goblinforest.net.ActionPayload;
import io.github.jan1a234.goblinforest.net.MatchStatePayload;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Die Draufsicht (DESIGN.md Abschnitt 10): Im Match ist sie die einzige Ansicht. Die Kamera schwebt schräg über dem
 * Schlachtfeld, die eigene Festung liegt immer links im Bild. Geschwenkt wird mit W/A/S/D, den Pfeiltasten oder indem
 * man das Feld mit gedrückter linker Maustaste zieht, gezoomt mit dem Mausrad (oder +/-). Der Client meldet den
 * Blickpunkt regelmäßig an den Server, damit die (unsichtbare) Spielfigur mitwandert und die Welt um den Blickpunkt
 * geladen bleibt.
 *
 * <p>Die Kamera selbst setzt {@code CameraMixin}; hier wird nur berechnet, wo sie stehen soll. Die Eingaben kommen
 * von {@link CommandScreen}.
 */
public final class CommandView {
	static final double MIN_DISTANCE = 14;
	static final double MAX_DISTANCE = 80;
	private static final double DEFAULT_DISTANCE = 40;
	private static final double ZOOM_STEP = 5;
	/** Neigung nach unten in Grad. */
	private static final float PITCH = 60.0F;
	/** Schwenkgeschwindigkeit in Blöcken pro Tick bei Standardabstand. */
	private static final double PAN_SPEED = 1.1;
	/** Höhe des „Ohrs“ über dem Blickpunkt: Kampfgeräusche in der Bildmitte sind gut zu hören. */
	private static final double LISTENER_HEIGHT = 6;

	/** Kamerapose für einen Frame. */
	public record Pose(double x, double y, double z, float yaw, float pitch) {
	}

	private static boolean active;
	private static int lastPhase = -1;
	private static int lastTeam = -1;
	private static double focusX, focusZ, prevFocusX, prevFocusZ;
	private static double distance = DEFAULT_DISTANCE, prevDistance = DEFAULT_DISTANCE, targetDistance = DEFAULT_DISTANCE;
	private static double sentX = Double.NaN, sentZ = Double.NaN;
	private static long lastSent;
	/** Schwenkrichtung aus Tasten und Bildschirmrand (-1, 0, 1), gesetzt von {@link CommandScreen}. */
	private static int panRight, panUp;
	private static boolean panFast;
	/** Die Kamera des letzten Frames (für Sichtfeld und Mauszielen). */
	private static Camera camera;

	private CommandView() {
	}

	/** Ist die Draufsicht gerade aktiv (im Match ab dem Countdown)? */
	public static boolean active() {
		return active;
	}

	private static boolean allowed(MatchStatePayload s) {
		return s.inMatch() && s.phase() != MatchPhase.SETUP.ordinal() && s.team() < TeamColor.values().length;
	}

	static TeamColor team() {
		MatchStatePayload s = ClientMatchState.get();
		return s.team() == TeamColor.GREEN.ordinal() ? TeamColor.GREEN : TeamColor.RED;
	}

	/** Zoomen; {@code steps} > 0 holt die Kamera näher heran. Touchpads liefern Bruchteile einer Raste. */
	static void zoom(double steps) {
		targetDistance = Mth.clamp(targetDistance - steps * ZOOM_STEP, MIN_DISTANCE, MAX_DISTANCE);
	}

	/**
	 * Feld mit der Maus ziehen: Die Welt folgt dem Mauszeiger. {@code dx}/{@code dy} in GUI-Pixeln,
	 * {@code guiHeight} die GUI-Höhe (bestimmt, wie viele Blöcke ein Pixel in Bildmitte sind).
	 */
	static void drag(double dx, double dy, int guiHeight) {
		if (!active || guiHeight <= 0) {
			return;
		}
		double blocksPerPixel = 2.0 * distance * Math.tan(Math.toRadians(fov()) / 2.0) / guiHeight;
		int flip = team() == TeamColor.GREEN ? -1 : 1;
		// Bildschirm-oben liegt am Boden schräg: ein Pixel nach oben ist dort 1/sin(Neigung) Blöcke weit.
		double depth = 1.0 / Math.sin(Math.toRadians(PITCH));
		focusX = Mth.clamp(focusX - dx * blocksPerPixel * flip, -ArenaLayout.HALF_LENGTH + 2, ArenaLayout.HALF_LENGTH - 2);
		focusZ = Mth.clamp(focusZ - dy * blocksPerPixel * depth * flip, -ArenaLayout.HALF_WIDTH + 2, ArenaLayout.HALF_WIDTH - 2);
	}

	private static double fov() {
		return camera != null && camera.getFov() > 1 ? camera.getFov() : Minecraft.getInstance().options.fov().get();
	}

	static void setPan(int right, int up, boolean fast) {
		panRight = Mth.clamp(right, -1, 1);
		panUp = Mth.clamp(up, -1, 1);
		panFast = fast;
	}

	/** Springt mit der Kamera zu einem Punkt (z. B. zum eigenen Häuptling). */
	static void jumpTo(double x, double z) {
		focusX = Mth.clamp(x, -ArenaLayout.HALF_LENGTH + 2, ArenaLayout.HALF_LENGTH - 2);
		focusZ = Mth.clamp(z, -ArenaLayout.HALF_WIDTH + 2, ArenaLayout.HALF_WIDTH - 2);
		prevFocusX = focusX;
		prevFocusZ = focusZ;
	}

	static void reset() {
		active = false;
		lastPhase = -1;
		lastTeam = -1;
		setPan(0, 0, false);
	}

	/** Wird vom {@code CameraMixin} in jedem Frame gesetzt. */
	public static void rememberCamera(Camera current) {
		camera = current;
	}

	public static double focusX() {
		return focusX;
	}

	public static double focusZ() {
		return focusZ;
	}

	public static double distance() {
		return targetDistance;
	}

	static void tick(Minecraft client) {
		MatchStatePayload s = ClientMatchState.get();
		long now = ClientMatchState.ticks();
		if (!allowed(s) || client.player == null) {
			active = false;
			lastPhase = s.inMatch() ? s.phase() : -1;
			return;
		}
		// Neues Match oder neue Runde: zurück zum eigenen Tor.
		boolean newRound = !active || lastTeam != s.team() || lastPhase != s.phase() && s.phase() == MatchPhase.COUNTDOWN.ordinal();
		lastPhase = s.phase();
		lastTeam = s.team();
		active = true;
		if (newRound) {
			ArenaLayout.Point start = ArenaLayout.overviewStart(team());
			jumpTo(start.x(), start.z());
			sentX = sentZ = Double.NaN;
		}
		prevFocusX = focusX;
		prevFocusZ = focusZ;
		prevDistance = distance;
		distance += (targetDistance - distance) * 0.25;

		double speed = PAN_SPEED * distance / DEFAULT_DISTANCE * (panFast ? 2.0 : 1.0);
		// Rot blickt nach Norden (rechts = Osten), Grün nach Süden (rechts = Westen).
		int flip = team() == TeamColor.GREEN ? -1 : 1;
		focusX += panRight * speed * flip;
		focusZ -= panUp * speed * flip;
		focusX = Mth.clamp(focusX, -ArenaLayout.HALF_LENGTH + 2, ArenaLayout.HALF_LENGTH - 2);
		focusZ = Mth.clamp(focusZ, -ArenaLayout.HALF_WIDTH + 2, ArenaLayout.HALF_WIDTH - 2);

		boolean moved = Double.isNaN(sentX) || Math.abs(focusX - sentX) > 2 || Math.abs(focusZ - sentZ) > 2;
		if (moved && now - lastSent >= 5) {
			sentX = focusX;
			sentZ = focusZ;
			lastSent = now;
			ClientPlayNetworking.send(new ActionPayload("focus:" + point(focusX, focusZ)));
		}
	}

	/** Punkt im Format der Aktionen ({@code x:z}, zwei Nachkommastellen). */
	static String point(double x, double z) {
		return String.format(Locale.ROOT, "%.2f:%.2f", x, z);
	}

	private static float yaw() {
		return team() == TeamColor.GREEN ? 0.0F : 180.0F;
	}

	/** Blickrichtung der Kamera als Einheitsvektor. */
	private static Vec3 forward() {
		double pitch = Math.toRadians(PITCH);
		double yawRad = Math.toRadians(yaw());
		// Blickrichtung wie bei Minecraft-Wesen: (-sin(yaw)·cos(pitch), -sin(pitch), cos(yaw)·cos(pitch)).
		return new Vec3(-Math.sin(yawRad) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yawRad) * Math.cos(pitch));
	}

	/** Wo die Kamera in diesem Frame steht, oder null, wenn die Draufsicht aus ist. */
	public static Pose pose(float partialTicks) {
		if (!active) {
			return null;
		}
		double fx = Mth.lerp(partialTicks, prevFocusX, focusX);
		double fz = Mth.lerp(partialTicks, prevFocusZ, focusZ);
		double dist = Mth.lerp(partialTicks, prevDistance, distance);
		Vec3 f = forward();
		double fy = ArenaLayout.GROUND_Y + 1;
		return new Pose(fx - f.x * dist, fy - f.y * dist, fz - f.z * dist, yaw(), PITCH);
	}

	/** Wo Geräusche gehört werden: knapp über der Bildmitte statt oben an der Kamera, sonst wäre die Schlacht stumm. */
	public static Vec3 listenerPosition() {
		return active ? new Vec3(focusX, ArenaLayout.GROUND_Y + 1 + LISTENER_HEIGHT, focusZ) : null;
	}

	/**
	 * Punkt am Boden unter dem Mauszeiger. {@code mouseX}/{@code mouseY} in GUI-Koordinaten, {@code width}/{@code height}
	 * die GUI-Größe. Gerechnet wird mit der Kamerapose und dem Sichtfeld des letzten Frames; null ohne Draufsicht.
	 */
	public static Vec3 groundPoint(double mouseX, double mouseY, int width, int height) {
		Pose pose = pose(1.0F);
		if (pose == null || width <= 0 || height <= 0) {
			return null;
		}
		double tanHalf = Math.tan(Math.toRadians(fov()) / 2.0);
		double aspect = width / (double) height;
		double ndcX = 2.0 * mouseX / width - 1.0;
		double ndcY = 1.0 - 2.0 * mouseY / height;
		Vec3 f = forward();
		// Rechts = Blickrichtung × Welt-Oben, Oben = Rechts × Blickrichtung (wie bei der Minecraft-Kamera).
		Vec3 right = f.cross(new Vec3(0, 1, 0)).normalize();
		Vec3 up = right.cross(f).normalize();
		Vec3 dir = f.add(right.scale(ndcX * tanHalf * aspect)).add(up.scale(ndcY * tanHalf));
		if (dir.y >= -1.0E-4) {
			return null;
		}
		double groundY = ArenaLayout.GROUND_Y + 1;
		double t = (groundY - pose.y()) / dir.y;
		double x = pose.x() + dir.x * t;
		double z = pose.z() + dir.z * t;
		return new Vec3(Mth.clamp(x, -ArenaLayout.HALF_LENGTH + 1, ArenaLayout.HALF_LENGTH - 1), groundY,
				Mth.clamp(z, -ArenaLayout.HALF_WIDTH + 1, ArenaLayout.HALF_WIDTH - 1));
	}
}
