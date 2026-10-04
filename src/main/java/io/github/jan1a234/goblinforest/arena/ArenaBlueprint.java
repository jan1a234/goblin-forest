package io.github.jan1a234.goblinforest.arena;

import io.github.jan1a234.goblinforest.game.TeamColor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static io.github.jan1a234.goblinforest.arena.ArenaLayout.*;

/**
 * Bauplan der Arena: für jede Blockposition, welches {@link Material} dort hingehört.
 * Reine Rechenlogik, damit sich Lane, Brücke, Tor und Festungen ohne Minecraft testen lassen;
 * {@link ArenaBuilder} übersetzt die Materialien in echte Blöcke.
 *
 * <p>Der Plan ist deterministisch (fester Seed): jede Arena sieht gleich aus, und ein Neuaufbau
 * stellt sie exakt wieder her, auch nach zerstörten Türmen.
 */
public final class ArenaBlueprint {
	/** Unterste und oberste Ebene, die gebaut bzw. geräumt wird. */
	public static final int MIN_Y = GROUND_Y - 5;
	public static final int MAX_Y = GROUND_Y + 24;

	private static final long SEED = 0x60B11F0E57L;

	public enum Material {
		AIR(false), BARRIER(true), BEDROCK(true), STONE(true), DIRT(true), GRASS(true), MOSS(true), PODZOL(true),
		COARSE_DIRT(true), ROOTED_DIRT(true), DIRT_PATH(true), GRAVEL(true), MUD(true), WATER(false),
		SPRUCE_PLANKS(true), SPRUCE_LOG(true), SPRUCE_FENCE(true), SPRUCE_SLAB(true), DARK_OAK_LOG(true), DARK_OAK_FENCE(true),
		DARK_OAK_LEAVES(true), OAK_LEAVES(true), SPRUCE_LEAVES(true), AZALEA_LEAVES(true),
		STONE_BRICKS(true), MOSSY_STONE_BRICKS(true), CRACKED_STONE_BRICKS(true), CHISELED_STONE_BRICKS(true),
		COBBLESTONE(true), MOSSY_COBBLESTONE(true), COBBLESTONE_WALL(true),
		POLISHED_BLACKSTONE_BRICKS(true), CRACKED_POLISHED_BLACKSTONE_BRICKS(true), CHISELED_POLISHED_BLACKSTONE(true),
		GILDED_BLACKSTONE(true), MUD_BRICKS(true), PACKED_MUD(true), CRACKED_DEEPSLATE_BRICKS(true), MAGMA_BLOCK(true),
		SHROOMLIGHT(true), LANTERN(false), HAY_BLOCK(true), IRON_BARS(true),
		SHORT_GRASS(false), FERN(false), RED_MUSHROOM(false), BROWN_MUSHROOM(false), LILY_PAD(false), MOSS_CARPET(false),
		RED_MUSHROOM_BLOCK(true), MUSHROOM_STEM(true),
		/** Wird je nach Seite durch einen Block in der Clanfarbe ersetzt. */
		TEAM_ACCENT(true);

		private final boolean solid;

		Material(boolean solid) {
			this.solid = solid;
		}

		/** Blockiert der Block das Durchlaufen (Kollision)? Gräser, Pilze, Laternen und Wasser nicht. */
		public boolean solid() {
			return solid;
		}
	}

	/** Ein einzelner Block, der nachträglich gesetzt wird (z. B. Trümmer eines zerstörten Turms). */
	public record Placement(int x, int y, int z, Material material) {
	}

	private final Map<Long, Material> stamps = new HashMap<>();

	private ArenaBlueprint() {
	}

	public static ArenaBlueprint create() {
		ArenaBlueprint blueprint = new ArenaBlueprint();
		blueprint.stampBorderHedge();
		blueprint.stampForest();
		blueprint.stampLaneLanterns();
		for (TeamColor team : TeamColor.values()) {
			blueprint.stampFortress(team);
			blueprint.stampTower(team);
			blueprint.stampCore(team);
		}
		blueprint.stampBridge();
		return blueprint;
	}

	// ---------------------------------------------------------------- Abfrage

	public Material get(int x, int y, int z) {
		if (Math.abs(x) > HALF_LENGTH || Math.abs(z) > HALF_WIDTH || y < MIN_Y || y > MAX_Y) {
			return Material.AIR;
		}
		Material stamped = stamps.get(key(x, y, z));
		return stamped != null ? stamped : terrain(x, y, z);
	}

	/** Seite (Team), zu der eine X-Koordinate gehört; für Clanfarben-Blöcke. */
	public static TeamColor sideOf(int x) {
		return x < 0 ? TeamColor.RED : TeamColor.GREEN;
	}

	// ---------------------------------------------------------------- Gelände

	private static Material terrain(int x, int y, int z) {
		if (y == MIN_Y) {
			return Material.BEDROCK;
		}
		boolean border = Math.abs(x) == HALF_LENGTH || Math.abs(z) == HALF_WIDTH;
		if (border) {
			return y <= GROUND_Y ? Material.STONE : Material.BARRIER;
		}
		if (isRiver(x)) {
			int bed = GROUND_Y - RIVER_DEPTH;
			if (y < bed) {
				return y < GROUND_Y - 3 ? Material.STONE : Material.DIRT;
			}
			if (y == bed) {
				double r = chance(x, z, 11);
				return r < 0.45 ? Material.GRAVEL : r < 0.8 ? Material.MUD : Material.DIRT;
			}
			if (y <= GROUND_Y) {
				return Material.WATER;
			}
			if (y == GROUND_Y + 1 && !isBridgeZ(z) && chance(x, z, 12) < 0.05) {
				return Material.LILY_PAD;
			}
			return Material.AIR;
		}
		if (y < GROUND_Y - 3) {
			return Material.STONE;
		}
		if (y < GROUND_Y) {
			return Material.DIRT;
		}
		if (y == GROUND_Y) {
			return surface(x, z);
		}
		if (y == GROUND_Y + 1) {
			return groundCover(x, z);
		}
		return Material.AIR;
	}

	private static boolean inCourtyard(int x, int z) {
		int ax = Math.abs(x);
		return ax > WALL_FRONT_X + 1 && ax < WALL_BACK_X - 1 && Math.abs(z) < WALL_HALF_Z - 1;
	}

	private static boolean inFortressFootprint(int x, int z, int margin) {
		int ax = Math.abs(x);
		return ax >= WALL_FRONT_X - margin && ax <= WALL_BACK_X + margin && Math.abs(z) <= WALL_HALF_Z + margin;
	}

	private static boolean isLaneMargin(int z) {
		return z == -LANE_HALF_WIDTH - 1 || z == LANE_HALF_WIDTH;
	}

	private static Material surface(int x, int z) {
		int ax = Math.abs(x);
		if (inCourtyard(x, z)) {
			if (isLane(z)) {
				double r = chance(x, z, 21);
				return r < 0.15 ? Material.MOSSY_STONE_BRICKS : r < 0.25 ? Material.CRACKED_STONE_BRICKS : Material.STONE_BRICKS;
			}
			double r = chance(x, z, 22);
			return r < 0.45 ? Material.PACKED_MUD : r < 0.75 ? Material.COARSE_DIRT : Material.ROOTED_DIRT;
		}
		if (isLane(z) && ax <= WALL_BACK_X) {
			return chance(x, z, 23) < 0.12 ? Material.COARSE_DIRT : Material.DIRT_PATH;
		}
		if (ax == RIVER_HALF_WIDTH + 1) {
			return chance(x, z, 24) < 0.6 ? Material.MUD : Material.COARSE_DIRT;
		}
		if (isLaneMargin(z) && ax <= WALL_FRONT_X) {
			return chance(x, z, 25) < 0.5 ? Material.COARSE_DIRT : Material.GRASS;
		}
		double n = noise(x, z);
		if (n > 0.72) {
			return Material.MOSS;
		}
		if (n < 0.18) {
			return Material.PODZOL;
		}
		return chance(x, z, 26) < 0.05 ? Material.COARSE_DIRT : Material.GRASS;
	}

	/** Gras, Farne und Pilze auf freien Wiesen; nie auf der Lane oder in der Festung. */
	private static Material groundCover(int x, int z) {
		if (isLane(z) || isLaneMargin(z) || inFortressFootprint(x, z, 0) || Math.abs(x) <= RIVER_HALF_WIDTH + 1) {
			return Material.AIR;
		}
		Material ground = surface(x, z);
		double r = chance(x, z, 31);
		if (ground == Material.PODZOL) {
			if (r < 0.06) {
				return Material.RED_MUSHROOM;
			}
			if (r < 0.12) {
				return Material.BROWN_MUSHROOM;
			}
			return r < 0.35 ? Material.FERN : Material.AIR;
		}
		if (ground == Material.MOSS) {
			return r < 0.3 ? Material.MOSS_CARPET : r < 0.4 ? Material.SHORT_GRASS : Material.AIR;
		}
		if (ground == Material.GRASS) {
			return r < 0.2 ? Material.SHORT_GRASS : r < 0.26 ? Material.FERN : Material.AIR;
		}
		return Material.AIR;
	}

	// ---------------------------------------------------------------- Strukturen

	private void stampBorderHedge() {
		// Dichte Hecke direkt vor der unsichtbaren Barriere, damit der Rand wie Wald aussieht.
		for (int x = -HALF_LENGTH + 1; x <= HALF_LENGTH - 1; x++) {
			for (int side = -1; side <= 1; side += 2) {
				int z = side * (HALF_WIDTH - 1);
				int height = 2 + (int) (chance(x, z, 41) * 3);
				for (int y = GROUND_Y + 1; y <= GROUND_Y + height; y++) {
					put(x, y, z, hedge(x, y, z));
				}
			}
		}
		for (int z = -HALF_WIDTH + 1; z <= HALF_WIDTH - 1; z++) {
			for (int side = -1; side <= 1; side += 2) {
				int x = side * (HALF_LENGTH - 1);
				int height = 2 + (int) (chance(x, z, 42) * 3);
				for (int y = GROUND_Y + 1; y <= GROUND_Y + height; y++) {
					put(x, y, z, hedge(x, y, z));
				}
			}
		}
	}

	private static Material hedge(int x, int y, int z) {
		double r = chance(x * 7 + y, z, 43);
		return r < 0.5 ? Material.DARK_OAK_LEAVES : r < 0.8 ? Material.OAK_LEAVES : Material.AZALEA_LEAVES;
	}

	private void stampForest() {
		Random random = new Random(SEED);
		int cell = 6;
		for (int cx = -HALF_LENGTH + 2; cx < HALF_LENGTH - 2; cx += cell) {
			for (int cz = -HALF_WIDTH + 2; cz < HALF_WIDTH - 2; cz += cell) {
				int x = cx + random.nextInt(cell);
				int z = cz + random.nextInt(cell);
				double roll = random.nextDouble();
				int kind = random.nextInt(100);
				int size = random.nextInt(3);
				int az = Math.abs(z);
				double density = az >= 22 ? 0.9 : az >= 14 ? 0.4 : 0.0;
				if (roll >= density || !treeAllowed(x, z)) {
					continue;
				}
				if (kind < 55) {
					stampRoundTree(x, z, 5 + size, Material.DARK_OAK_LOG, Material.DARK_OAK_LEAVES);
				} else if (kind < 80) {
					stampSpruce(x, z, 6 + size);
				} else if (kind < 92) {
					stampGiantMushroom(x, z, 4 + size);
				} else {
					stampBoulder(x, z, size);
				}
			}
		}
	}

	private static boolean treeAllowed(int x, int z) {
		if (Math.abs(x) >= HALF_LENGTH - 2 || Math.abs(z) >= HALF_WIDTH - 2) {
			return false;
		}
		if (Math.abs(z) < LANE_HALF_WIDTH + 7) {
			return false;
		}
		if (Math.abs(x) <= RIVER_HALF_WIDTH + 3) {
			return false;
		}
		if (inFortressFootprint(x, z, 4)) {
			return false;
		}
		for (TeamColor team : TeamColor.values()) {
			Box tower = towerBox(team);
			if (tower.distance(x + 0.5, GROUND_Y + 1, z + 0.5) < 7) {
				return false;
			}
		}
		return true;
	}

	private void stampRoundTree(int x, int z, int trunk, Material log, Material leaves) {
		int top = GROUND_Y + trunk;
		int radius = trunk >= 6 ? 3 : 2;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					double d = Math.sqrt(dx * dx + dy * dy * 1.6 + dz * dz);
					if (d <= radius + 0.3 && chance(x + dx, z + dz, 50 + dy) < 0.92) {
						putIfFree(x + dx, top + dy, z + dz, leaves);
					}
				}
			}
		}
		for (int y = GROUND_Y + 1; y <= top; y++) {
			put(x, y, z, log);
		}
	}

	private void stampSpruce(int x, int z, int height) {
		int top = GROUND_Y + height;
		for (int y = GROUND_Y + 3; y <= top + 1; y++) {
			int radius = Math.max(0, (top + 1 - y) / 2);
			if (y == top + 1) {
				radius = 0;
			}
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.abs(dx) + Math.abs(dz) <= radius + 1) {
						putIfFree(x + dx, y, z + dz, Material.SPRUCE_LEAVES);
					}
				}
			}
		}
		for (int y = GROUND_Y + 1; y <= top; y++) {
			put(x, y, z, Material.SPRUCE_LOG);
		}
	}

	private void stampGiantMushroom(int x, int z, int height) {
		int top = GROUND_Y + height;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
					continue;
				}
				putIfFree(x + dx, top + 1, z + dz, Material.RED_MUSHROOM_BLOCK);
				if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
					putIfFree(x + dx, top, z + dz, Material.RED_MUSHROOM_BLOCK);
				}
			}
		}
		for (int y = GROUND_Y + 1; y <= top; y++) {
			put(x, y, z, Material.MUSHROOM_STEM);
		}
	}

	private void stampBoulder(int x, int z, int size) {
		for (int dx = 0; dx <= 1; dx++) {
			for (int dz = 0; dz <= 1; dz++) {
				put(x + dx, GROUND_Y + 1, z + dz, chance(x + dx, z + dz, 61) < 0.5 ? Material.MOSSY_COBBLESTONE : Material.COBBLESTONE);
				if (size > 0 && chance(x + dx, z + dz, 62) < 0.5) {
					put(x + dx, GROUND_Y + 2, z + dz, Material.MOSSY_COBBLESTONE);
				}
			}
		}
	}

	private void stampLaneLanterns() {
		for (int ax = 16; ax < WALL_FRONT_X - 4; ax += 16) {
			for (int s = -1; s <= 1; s += 2) {
				for (int z : new int[] {-LANE_HALF_WIDTH - 2, LANE_HALF_WIDTH + 1}) {
					int x = s * ax;
					put(x, GROUND_Y + 1, z, Material.SPRUCE_FENCE);
					put(x, GROUND_Y + 2, z, Material.SPRUCE_FENCE);
					put(x, GROUND_Y + 3, z, Material.LANTERN);
				}
			}
		}
	}

	private void stampFortress(TeamColor team) {
		int s = side(team);
		int wf = WALL_FRONT_X, wb = WALL_BACK_X, hz = WALL_HALF_Z;
		int top = GROUND_Y + WALL_HEIGHT;
		for (int ax = wf; ax <= wb; ax++) {
			for (int z = -hz; z <= hz; z++) {
				boolean front = ax <= wf + 1;
				boolean back = ax >= wb - 1;
				boolean sideWall = Math.abs(z) >= hz - 1;
				if (!front && !back && !sideWall) {
					continue;
				}
				boolean outer = ax == wf || ax == wb || Math.abs(z) == hz;
				int x = s * ax;
				for (int y = GROUND_Y + 1; y <= top; y++) {
					boolean gate = front && !sideWall && isLane(z) && y <= GROUND_Y + 4
							&& !(y == GROUND_Y + 4 && (z == -LANE_HALF_WIDTH || z == LANE_HALF_WIDTH - 1));
					if (gate) {
						put(x, y, z, Material.AIR);
						continue;
					}
					if (front && !sideWall && isLane(z) && y == GROUND_Y + 5) {
						put(x, y, z, Material.CHISELED_STONE_BRICKS);
						continue;
					}
					if (outer && y == top - 2) {
						put(x, y, z, Material.TEAM_ACCENT);
						continue;
					}
					put(x, y, z, wallStone(x, y, z));
				}
				// Zinnen auf der Außenkante
				int along = (ax == wf || ax == wb) ? z : ax;
				if (outer && Math.floorMod(along, 2) == 0) {
					put(x, top + 1, z, wallStone(x, top + 1, z));
				}
			}
		}
		stampCornerTower(s, wf - 1, -hz - 1);
		stampCornerTower(s, wf - 1, hz - 2);
		stampCornerTower(s, wb - 2, -hz - 1);
		stampCornerTower(s, wb - 2, hz - 2);
		// Fahnen über dem Tor
		for (int z : new int[] {-LANE_HALF_WIDTH - 1, LANE_HALF_WIDTH}) {
			int x = s * wf;
			for (int y = top + 1; y <= top + 4; y++) {
				put(x, y, z, Material.DARK_OAK_FENCE);
			}
			put(x - s, top + 4, z, Material.TEAM_ACCENT);
			put(x - s, top + 3, z, Material.TEAM_ACCENT);
			put(x - 2 * s, top + 4, z, Material.TEAM_ACCENT);
		}
		stampBarracks(s);
		stampThrone(s);
		// Laternen im Hof
		for (int[] p : new int[][] {{wf + 3, -6}, {wf + 3, 5}, {wb - 4, -6}, {wb - 4, 5}}) {
			int x = s * p[0];
			put(x, GROUND_Y + 1, p[1], Material.DARK_OAK_FENCE);
			put(x, GROUND_Y + 2, p[1], Material.DARK_OAK_FENCE);
			put(x, GROUND_Y + 3, p[1], Material.LANTERN);
		}
	}

	private static Material wallStone(int x, int y, int z) {
		double r = chance(x * 31 + y, z, 71);
		return r < 0.2 ? Material.MOSSY_STONE_BRICKS : r < 0.28 ? Material.CRACKED_STONE_BRICKS : Material.STONE_BRICKS;
	}

	/** Eckturm der Festungsmauer: 4x4, drei Blöcke höher als die Mauer. ax0/z0 = kleinste Ecke (|X|, Z). */
	private void stampCornerTower(int s, int ax0, int z0) {
		int top = GROUND_Y + WALL_HEIGHT + 3;
		for (int dax = 0; dax < 4; dax++) {
			for (int dz = 0; dz < 4; dz++) {
				int x = s * (ax0 + dax);
				int z = z0 + dz;
				boolean corner = (dax == 0 || dax == 3) && (dz == 0 || dz == 3);
				for (int y = GROUND_Y + 1; y <= top; y++) {
					Material m;
					if (corner) {
						m = Material.DARK_OAK_LOG;
					} else if (y == top - 1) {
						m = Material.TEAM_ACCENT;
					} else {
						m = chance(x + y * 13, z, 81) < 0.3 ? Material.MOSSY_COBBLESTONE : Material.COBBLESTONE;
					}
					put(x, y, z, m);
				}
				boolean edge = dax == 0 || dax == 3 || dz == 0 || dz == 3;
				if (edge && (dax + dz) % 2 == 0) {
					put(x, top + 1, z, Material.COBBLESTONE_WALL);
				}
			}
		}
		put(s * (ax0 + 1), top + 1, z0 + 1, Material.LANTERN);
	}

	/** Kaserne: Holzhütte neben der Lane, aus der die Einheiten kommen. */
	private void stampBarracks(int s) {
		int ax0 = WALL_FRONT_X + 4, ax1 = WALL_FRONT_X + 11;
		int z0 = -WALL_HALF_Z + 3, z1 = -LANE_HALF_WIDTH - 3;
		int top = GROUND_Y + 4;
		for (int ax = ax0; ax <= ax1; ax++) {
			for (int z = z0; z <= z1; z++) {
				int x = s * ax;
				boolean wallX = ax == ax0 || ax == ax1;
				boolean wallZ = z == z0 || z == z1;
				for (int y = GROUND_Y + 1; y <= top; y++) {
					Material m = Material.AIR;
					if (wallX && wallZ) {
						m = Material.SPRUCE_LOG;
					} else if (wallX || wallZ) {
						boolean door = z == z1 && Math.abs(ax - (ax0 + ax1) / 2) <= 1 && y <= GROUND_Y + 3;
						boolean window = wallX && y == GROUND_Y + 3 && Math.abs(z - (z0 + z1) / 2) <= 1;
						m = door ? Material.AIR : window ? Material.IRON_BARS : Material.SPRUCE_PLANKS;
					}
					put(x, y, z, m);
				}
				put(x, top + 1, z, (wallX || wallZ) ? Material.SPRUCE_SLAB : Material.HAY_BLOCK);
			}
		}
		put(s * (ax0 + 2), GROUND_Y + 1, z0 + 1, Material.HAY_BLOCK);
		put(s * (ax1 - 1), GROUND_Y + 1, z0 + 1, Material.LANTERN);
	}

	/** Häuptlingsthron hinten im Hof. */
	private void stampThrone(int s) {
		int ax = WALL_BACK_X - 3;
		for (int z = 8; z <= 12; z++) {
			put(s * ax, GROUND_Y + 1, z, Material.POLISHED_BLACKSTONE_BRICKS);
			put(s * (ax - 1), GROUND_Y + 1, z, Material.POLISHED_BLACKSTONE_BRICKS);
		}
		for (int y = GROUND_Y + 2; y <= GROUND_Y + 4; y++) {
			put(s * ax, y, 9, Material.GILDED_BLACKSTONE);
			put(s * ax, y, 11, Material.GILDED_BLACKSTONE);
		}
		put(s * ax, GROUND_Y + 2, 10, Material.CHISELED_POLISHED_BLACKSTONE);
		put(s * ax, GROUND_Y + 3, 10, Material.TEAM_ACCENT);
		put(s * ax, GROUND_Y + 4, 10, Material.TEAM_ACCENT);
		put(s * ax, GROUND_Y + 5, 10, Material.SHROOMLIGHT);
	}

	private void stampTower(TeamColor team) {
		for (Placement p : towerBlocks(team, false)) {
			put(p.x, p.y, p.z, p.material);
		}
	}

	private void stampCore(TeamColor team) {
		for (Placement p : coreBlocks(team, 0)) {
			put(p.x, p.y, p.z, p.material);
		}
	}

	private void stampBridge() {
		int z0 = -LANE_HALF_WIDTH - 1, z1 = LANE_HALF_WIDTH;
		for (int x = -RIVER_HALF_WIDTH; x <= RIVER_HALF_WIDTH; x++) {
			for (int z = z0; z <= z1; z++) {
				put(x, GROUND_Y, z, Material.SPRUCE_PLANKS);
				put(x, GROUND_Y + 1, z, (z == z0 || z == z1) ? Material.SPRUCE_FENCE : Material.AIR);
			}
		}
		for (int x : new int[] {-RIVER_HALF_WIDTH, 0, RIVER_HALF_WIDTH}) {
			for (int z : new int[] {z0, z1}) {
				for (int y = GROUND_Y - RIVER_DEPTH + 1; y < GROUND_Y; y++) {
					put(x, y, z, Material.SPRUCE_LOG);
				}
			}
		}
		for (int x : new int[] {-RIVER_HALF_WIDTH, RIVER_HALF_WIDTH}) {
			for (int z : new int[] {z0, z1}) {
				put(x, GROUND_Y + 2, z, Material.LANTERN);
			}
		}
	}

	private static boolean isBridgeZ(int z) {
		return z >= -LANE_HALF_WIDTH - 1 && z <= LANE_HALF_WIDTH;
	}

	// ---------------------------------------------------------------- Turm und Kern (auch für Schadensstufen)

	/** Alle Blöcke des Wachturms; zerstört bleibt nur ein Stumpf mit Trümmern. */
	public static List<Placement> towerBlocks(TeamColor team, boolean destroyed) {
		List<Placement> out = new ArrayList<>();
		Box box = towerBox(team);
		int cx = (box.minX() + box.maxX()) / 2, cz = (box.minZ() + box.maxZ()) / 2;
		int platform = box.maxY();
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				boolean corner = (x == box.minX() || x == box.maxX()) && (z == box.minZ() || z == box.maxZ());
				boolean face = x == box.minX() || x == box.maxX() || z == box.minZ() || z == box.maxZ();
				for (int y = box.minY(); y <= platform + 1; y++) {
					Material m;
					if (y == platform + 1) {
						if (corner) {
							m = Material.COBBLESTONE_WALL;
						} else if (x == cx && z == cz) {
							m = Material.LANTERN;
						} else if (face) {
							m = Material.SPRUCE_FENCE;
						} else {
							m = Material.AIR;
						}
					} else if (y == platform) {
						m = Material.SPRUCE_PLANKS;
					} else if (corner) {
						m = Material.SPRUCE_LOG;
					} else if (face && y == GROUND_Y + 8) {
						m = Material.TEAM_ACCENT;
					} else {
						m = chance(x * 5 + y, z, 91) < 0.3 ? Material.MOSSY_COBBLESTONE : Material.COBBLESTONE;
					}
					if (destroyed && y > GROUND_Y + 4) {
						m = Material.AIR;
						if (y == GROUND_Y + 5 && chance(x, z, 92) < 0.5) {
							m = chance(x, z, 93) < 0.5 ? Material.GRAVEL : Material.COBBLESTONE;
						}
					}
					out.add(new Placement(x, y, z, m));
				}
			}
		}
		if (destroyed) {
			// Trümmer rund um den Turm, aber nie auf der Lane.
			for (int x = box.minX() - 3; x <= box.maxX() + 3; x++) {
				for (int z = box.minZ() - 3; z <= box.maxZ() + 3; z++) {
					boolean inside = x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ();
					if (inside || isLane(z) || isLaneMargin(z) || chance(x, z, 94) > 0.18) {
						continue;
					}
					out.add(new Placement(x, GROUND_Y + 1, z, chance(x, z, 95) < 0.5 ? Material.MOSSY_COBBLESTONE : Material.COBBLESTONE));
				}
			}
		}
		return out;
	}

	/**
	 * Alle Blöcke des Festungskerns für eine Schadensstufe: 0 = unversehrt, 1–3 = zunehmend rissig,
	 * 4 = zerstört (obere Hälfte eingestürzt, das Herz glüht als Magma).
	 */
	public static List<Placement> coreBlocks(TeamColor team, int stage) {
		List<Placement> out = new ArrayList<>();
		Box box = coreBox(team);
		int cx = (box.minX() + box.maxX()) / 2;
		int frontX = team == TeamColor.RED ? box.maxX() : box.minX();
		double crack = stage <= 0 ? 0 : Math.min(0.8, 0.22 * stage);
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				boolean corner = (x == box.minX() || x == box.maxX()) && (z == box.minZ() || z == box.maxZ());
				boolean heartColumn = x == frontX && Math.abs(z) <= 1;
				for (int y = box.minY(); y <= box.maxY(); y++) {
					Material m;
					if (y == box.minY()) {
						m = Material.POLISHED_BLACKSTONE_BRICKS;
					} else if (y == box.maxY()) {
						m = (x == cx && z == 0) ? Material.SHROOMLIGHT
								: (x + z) % 2 == 0 ? Material.GILDED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS;
					} else if (corner) {
						m = Material.DARK_OAK_LOG;
					} else if (heartColumn && y >= GROUND_Y + 3 && y <= GROUND_Y + 5) {
						m = stage >= 4 ? Material.MAGMA_BLOCK : Material.SHROOMLIGHT;
					} else if (y == GROUND_Y + 4) {
						m = Material.TEAM_ACCENT;
					} else {
						m = Material.MUD_BRICKS;
					}
					if (crack > 0 && chance(x * 3 + y, z, 101) < crack) {
						if (m == Material.MUD_BRICKS) {
							m = Material.CRACKED_DEEPSLATE_BRICKS;
						} else if (m == Material.POLISHED_BLACKSTONE_BRICKS) {
							m = Material.CRACKED_POLISHED_BLACKSTONE_BRICKS;
						}
					}
					if (stage >= 4 && y >= GROUND_Y + 5 && !heartColumn) {
						m = y == GROUND_Y + 5 && chance(x, z, 102) < 0.4 ? Material.GRAVEL : Material.AIR;
					} else if (stage >= 3 && y == box.maxY() && chance(x, z, 103) < 0.4) {
						m = Material.AIR;
					}
					out.add(new Placement(x, y, z, m));
				}
			}
		}
		// Fahnenmast auf dem Kern
		boolean standing = stage < 3;
		for (int y = box.maxY() + 1; y <= box.maxY() + 4; y++) {
			out.add(new Placement(cx, y, 0, standing ? Material.DARK_OAK_FENCE : Material.AIR));
		}
		int dir = -side(team);
		out.add(new Placement(cx + dir, box.maxY() + 4, 0, standing ? Material.TEAM_ACCENT : Material.AIR));
		out.add(new Placement(cx + dir, box.maxY() + 3, 0, standing ? Material.TEAM_ACCENT : Material.AIR));
		out.add(new Placement(cx + 2 * dir, box.maxY() + 4, 0, standing ? Material.TEAM_ACCENT : Material.AIR));
		return out;
	}

	/** Schadensstufe des Kerns aus dem Lebensanteil (1.0 = voll). */
	public static int coreStage(double healthFraction) {
		if (healthFraction <= 0) {
			return 4;
		}
		if (healthFraction < 0.25) {
			return 3;
		}
		if (healthFraction < 0.5) {
			return 2;
		}
		if (healthFraction < 0.75) {
			return 1;
		}
		return 0;
	}

	// ---------------------------------------------------------------- Hilfsfunktionen

	private void put(int x, int y, int z, Material material) {
		if (Math.abs(x) >= HALF_LENGTH || Math.abs(z) >= HALF_WIDTH || y <= MIN_Y || y > MAX_Y) {
			return;
		}
		stamps.put(key(x, y, z), material);
	}

	/** Setzt nur, wenn dort noch nichts Gestempeltes und kein festes Gelände ist (Blätter überschreiben keine Stämme). */
	private void putIfFree(int x, int y, int z, Material material) {
		if (Math.abs(x) >= HALF_LENGTH || Math.abs(z) >= HALF_WIDTH || y <= GROUND_Y || y > MAX_Y) {
			return;
		}
		Material existing = stamps.get(key(x, y, z));
		if (existing == null || !existing.solid()) {
			stamps.put(key(x, y, z), material);
		}
	}

	private static long key(int x, int y, int z) {
		return ((long) (x + 512) << 32) | ((long) (y + 512) << 16) | (z + 512);
	}

	/** Deterministischer Zufallswert in [0, 1) je Position. */
	static double chance(int x, int z, int salt) {
		long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L ^ SEED;
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		h *= 0xC4CEB9FE1A85EC53L;
		h ^= h >>> 33;
		return (h >>> 11) * 0x1.0p-53;
	}

	/** Weiches Rauschen für Flecken aus Moos und Podsol (bilinear interpoliertes Gitter im Abstand 6). */
	static double noise(int x, int z) {
		int gx = Math.floorDiv(x, 6), gz = Math.floorDiv(z, 6);
		double fx = (x - gx * 6) / 6.0, fz = (z - gz * 6) / 6.0;
		double a = chance(gx, gz, 1), b = chance(gx + 1, gz, 1), c = chance(gx, gz + 1, 1), d = chance(gx + 1, gz + 1, 1);
		double sx = fx * fx * (3 - 2 * fx), sz = fz * fz * (3 - 2 * fz);
		return (a * (1 - sx) + b * sx) * (1 - sz) + (c * (1 - sx) + d * sx) * sz;
	}
}
