package io.github.jan1a234.goblinforest.arena;

import io.github.jan1a234.goblinforest.game.TeamColor;
import java.util.ArrayList;
import java.util.List;

/**
 * Geometrie der Arena (DESIGN.md Abschnitt 4): eine Lane entlang der X-Achse, Fluss mit Brücke in der Mitte,
 * je eine Festung an beiden Enden. Der rote Clan sitzt im Westen (negatives X), der grüne im Osten.
 * Alles hier ist reine Rechenlogik ohne Minecraft-Abhängigkeit; {@link ArenaBuilder} setzt daraus die Blöcke.
 *
 * <p>Die Lane ist als Liste von Wegpunkten beschrieben, damit später weitere Lanes nur neue Wegpunkte brauchen.
 */
public final class ArenaLayout {
	/** Oberkante des Bodens; Einheiten und Spieler stehen auf Höhe {@code GROUND_Y + 1}. */
	public static final int GROUND_Y = 64;
	/** Halbe Länge der Arena (X von -HALF_LENGTH bis +HALF_LENGTH, inklusive Rand). */
	public static final int HALF_LENGTH = 104;
	/** Halbe Breite der Arena (Z). */
	public static final int HALF_WIDTH = 32;
	/** Höhe, bis zu der beim Neuaufbau alles geräumt wird. */
	public static final int CLEAR_TOP = GROUND_Y + 40;

	/** X-Abstand der Festungskerne von der Mitte (Abstand der Kerne zueinander also 2 * CORE_X = 172). */
	public static final int CORE_X = 86;
	/** Festungsmauer: Außenkante (Richtung Mitte) und Rückseite, gemessen als |X|. */
	public static final int WALL_FRONT_X = 76;
	public static final int WALL_BACK_X = 98;
	public static final int WALL_HALF_Z = 16;
	public static final int WALL_HEIGHT = 7;
	/** Halbe Breite der Lane und des Tors. */
	public static final int LANE_HALF_WIDTH = 4;
	/** Fluss: halbe Breite in X um die Mitte, Tiefe. */
	public static final int RIVER_HALF_WIDTH = 4;
	public static final int RIVER_DEPTH = 3;
	/** Turm: Position (|X|, Z), Größe und Höhe. */
	public static final int TOWER_X = 70;
	public static final int TOWER_Z = -8;
	public static final int TOWER_HALF_SIZE = 2;
	public static final int TOWER_HEIGHT = 12;
	/** Festungskern: halbe Kantenlänge und Höhe. */
	public static final int CORE_HALF_SIZE = 3;
	public static final int CORE_HEIGHT = 7;
	/** Abstand der Wegpunkte entlang der Lane. */
	public static final int WAYPOINT_SPACING = 12;

	public record Point(double x, double y, double z) {
		public double distanceSq(Point other) {
			double dx = x - other.x, dy = y - other.y, dz = z - other.z;
			return dx * dx + dy * dy + dz * dz;
		}

		public double horizontalDistance(double ox, double oz) {
			double dx = x - ox, dz = z - oz;
			return Math.sqrt(dx * dx + dz * dz);
		}
	}

	/** Achsenparalleler Quader in Blockkoordinaten, beide Ecken inklusive. */
	public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		/** Kleinster Abstand eines Punktes zum Quader (0, wenn er drin liegt). */
		public double distance(double x, double y, double z) {
			double dx = Math.max(Math.max(minX - x, 0), x - (maxX + 1));
			double dy = Math.max(Math.max(minY - y, 0), y - (maxY + 1));
			double dz = Math.max(Math.max(minZ - z, 0), z - (maxZ + 1));
			return Math.sqrt(dx * dx + dy * dy + dz * dz);
		}

		public boolean contains(int x, int y, int z) {
			return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
		}

		public Point center() {
			return new Point((minX + maxX + 1) / 2.0, (minY + maxY + 1) / 2.0, (minZ + maxZ + 1) / 2.0);
		}
	}

	private ArenaLayout() {
	}

	/** +1 für Grün (Osten), -1 für Rot (Westen). */
	public static int side(TeamColor team) {
		return team == TeamColor.GREEN ? 1 : -1;
	}

	/** Richtung, in die ein Clan angreift (X-Vorzeichen). */
	public static int attackDirection(TeamColor team) {
		return -side(team);
	}

	public static Box coreBox(TeamColor team) {
		int cx = side(team) * CORE_X;
		return new Box(cx - CORE_HALF_SIZE, GROUND_Y + 1, -CORE_HALF_SIZE, cx + CORE_HALF_SIZE, GROUND_Y + CORE_HEIGHT, CORE_HALF_SIZE);
	}

	public static Box towerBox(TeamColor team) {
		int tx = side(team) * TOWER_X;
		return new Box(tx - TOWER_HALF_SIZE, GROUND_Y + 1, TOWER_Z - TOWER_HALF_SIZE, tx + TOWER_HALF_SIZE, GROUND_Y + TOWER_HEIGHT, TOWER_Z + TOWER_HALF_SIZE);
	}

	/** Punkt, von dem der Turm schießt (Mitte der Turmspitze). */
	public static Point towerMuzzle(TeamColor team) {
		return new Point(side(team) * TOWER_X + 0.5, GROUND_Y + TOWER_HEIGHT + 1.5, TOWER_Z + 0.5);
	}

	/** Z-Position der Festungskanone auf der Frontmauer (neben dem Tor, in einer Schießscharte). */
	public static final int CANNON_Z = -7;

	/** Position der Kanone (Dispenser) oben auf der inneren Reihe der Frontmauer. */
	public static Point cannonBlock(TeamColor team) {
		return new Point(side(team) * (WALL_FRONT_X + 1), GROUND_Y + WALL_HEIGHT + 1, CANNON_Z);
	}

	/** Punkt, an dem die Kanonenkugel die Mündung verlässt. */
	public static Point cannonMuzzle(TeamColor team) {
		return new Point(side(team) * WALL_FRONT_X + 0.5, GROUND_Y + WALL_HEIGHT + 1.5, CANNON_Z + 0.5);
	}

	/** Mitte der Goldmine im Hof (für Funken und Anzeige). */
	public static Point goldmine(TeamColor team) {
		return new Point(side(team) * (WALL_FRONT_X + 11) + 0.5, GROUND_Y + 2, 11.5);
	}

	/** Sammelpunkt der Kaserne direkt hinter dem Tor, an dem neue Einheiten erscheinen. */
	public static Point barracks(TeamColor team) {
		return new Point(side(team) * (WALL_FRONT_X + 4) + 0.5, GROUND_Y + 1, 0.5);
	}

	/** Startpunkt des Häuptlings innerhalb der eigenen Festung. */
	public static Point heroSpawn(TeamColor team) {
		return new Point(side(team) * (WALL_FRONT_X + 6) + 0.5, GROUND_Y + 1, 9.5);
	}

	/** Startblick der Draufsicht: knapp vor dem eigenen Tor, sodass Festung und Vorfeld im Bild sind. */
	public static Point overviewStart(TeamColor team) {
		return new Point(side(team) * (WALL_FRONT_X - 6) + 0.5, GROUND_Y + 1, 0.5);
	}

	/** Blickrichtung (Yaw) zum Gegner: Rot schaut nach Osten (-90°), Grün nach Westen (90°). */
	public static float facingYaw(TeamColor team) {
		return team == TeamColor.RED ? -90f : 90f;
	}

	/** Heilzone innerhalb der eigenen Mauern (für Rückzug und Häuptling). */
	public static Box healZone(TeamColor team) {
		int s = side(team);
		int x1 = s * WALL_FRONT_X, x2 = s * WALL_BACK_X;
		return new Box(Math.min(x1, x2), GROUND_Y + 1, -WALL_HALF_Z, Math.max(x1, x2), GROUND_Y + 12, WALL_HALF_Z);
	}

	/**
	 * Wegpunkte der Lane für einen Clan, vom eigenen Tor bis vor den feindlichen Festungskern.
	 * Die Einheiten laufen sie der Reihe nach ab.
	 */
	public static List<Point> laneWaypoints(TeamColor team) {
		int dir = attackDirection(team);
		int startX = side(team) * WALL_FRONT_X;
		int endX = -side(team) * (CORE_X - CORE_HALF_SIZE - 2);
		List<Point> points = new ArrayList<>();
		for (int x = startX; dir > 0 ? x < endX : x > endX; x += dir * WAYPOINT_SPACING) {
			points.add(new Point(x + 0.5, GROUND_Y + 1, 0.5));
		}
		points.add(new Point(endX + 0.5, GROUND_Y + 1, 0.5));
		return points;
	}

	/**
	 * Fortschritt entlang der Lane aus Sicht eines Clans: 0 am eigenen Kern, 1 am feindlichen Kern.
	 * Grundlage für den Frontfaktor beim Kopfgeld.
	 */
	public static double progress(TeamColor team, double x) {
		double own = side(team) * CORE_X;
		double enemy = -own;
		return Math.clamp((x - own) / (enemy - own), 0.0, 1.0);
	}

	/** Liegt die Position im Wasser des Flusses (für den Bau der Brücke)? */
	public static boolean isRiver(int x) {
		return Math.abs(x) <= RIVER_HALF_WIDTH;
	}

	public static boolean isLane(int z) {
		return z >= -LANE_HALF_WIDTH && z < LANE_HALF_WIDTH;
	}

	public static boolean insideArena(double x, double z) {
		return Math.abs(x) < HALF_LENGTH && Math.abs(z) < HALF_WIDTH;
	}

	/** Chunk-Bereich, der während eines Matches geladen bleiben muss. */
	public static int minChunkX() {
		return Math.floorDiv(-HALF_LENGTH, 16);
	}

	public static int maxChunkX() {
		return Math.floorDiv(HALF_LENGTH, 16);
	}

	public static int minChunkZ() {
		return Math.floorDiv(-HALF_WIDTH, 16);
	}

	public static int maxChunkZ() {
		return Math.floorDiv(HALF_WIDTH, 16);
	}
}
