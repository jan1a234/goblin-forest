package io.github.jan1a234.goblinforest.arena;

import io.github.jan1a234.goblinforest.arena.ArenaBlueprint.Material;
import io.github.jan1a234.goblinforest.game.TeamColor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.github.jan1a234.goblinforest.arena.ArenaLayout.*;
import static org.junit.jupiter.api.Assertions.*;

class ArenaBlueprintTest {
	private static ArenaBlueprint blueprint;

	@BeforeAll
	static void build() {
		blueprint = ArenaBlueprint.create();
	}

	private static void assertWalkable(int x, int z) {
		assertTrue(blueprint.get(x, GROUND_Y, z).solid(), "Boden fehlt bei " + x + "/" + z);
		for (int y = GROUND_Y + 1; y <= GROUND_Y + 3; y++) {
			assertFalse(blueprint.get(x, y, z).solid(), "Hindernis bei " + x + "/" + y + "/" + z + ": " + blueprint.get(x, y, z));
		}
	}

	@Test
	void laneIsWalkableFromBarracksToBarracks() {
		int end = (int) Math.floor(Math.abs(barracks(TeamColor.GREEN).x()));
		for (int x = -end; x <= end; x++) {
			for (int z = -LANE_HALF_WIDTH; z < LANE_HALF_WIDTH; z++) {
				assertWalkable(x, z);
			}
		}
	}

	@Test
	void spawnPointsAreFree() {
		for (TeamColor team : TeamColor.values()) {
			Point b = barracks(team);
			assertWalkable((int) Math.floor(b.x()), (int) Math.floor(b.z()));
			Point h = heroSpawn(team);
			assertWalkable((int) Math.floor(h.x()), (int) Math.floor(h.z()));
		}
	}

	@Test
	void riverHasBridgeOnTheLaneAndWaterBeside() {
		assertEquals(Material.SPRUCE_PLANKS, blueprint.get(0, GROUND_Y, 0));
		assertEquals(Material.WATER, blueprint.get(0, GROUND_Y, 12));
		assertEquals(Material.WATER, blueprint.get(RIVER_HALF_WIDTH, GROUND_Y - 1, -20));
		assertEquals(Material.SPRUCE_FENCE, blueprint.get(0, GROUND_Y + 1, -LANE_HALF_WIDTH - 1));
		assertEquals(Material.SPRUCE_FENCE, blueprint.get(0, GROUND_Y + 1, LANE_HALF_WIDTH));
	}

	@Test
	void structuresAreSolid() {
		for (TeamColor team : TeamColor.values()) {
			Point core = coreBox(team).center();
			assertTrue(blueprint.get((int) Math.floor(core.x()), GROUND_Y + 2, 0).solid());
			Box tower = towerBox(team);
			assertTrue(blueprint.get(tower.minX(), GROUND_Y + 6, tower.minZ()).solid());
			assertEquals(Material.SPRUCE_PLANKS, blueprint.get(tower.minX() + 1, tower.maxY(), tower.minZ() + 1));
			// Mauer neben dem Tor ist geschlossen
			int wallX = side(team) * WALL_FRONT_X;
			assertTrue(blueprint.get(wallX, GROUND_Y + 2, LANE_HALF_WIDTH + 3).solid());
		}
	}

	@Test
	void borderIsClosed() {
		for (int x = -HALF_LENGTH; x <= HALF_LENGTH; x += 7) {
			assertEquals(Material.BARRIER, blueprint.get(x, GROUND_Y + 5, HALF_WIDTH));
			assertEquals(Material.BARRIER, blueprint.get(x, GROUND_Y + 5, -HALF_WIDTH));
		}
		assertEquals(Material.BARRIER, blueprint.get(HALF_LENGTH, GROUND_Y + 1, 0));
		assertEquals(Material.BEDROCK, blueprint.get(0, ArenaBlueprint.MIN_Y, 0));
	}

	@Test
	void blueprintIsDeterministic() {
		ArenaBlueprint other = ArenaBlueprint.create();
		for (int x = -HALF_LENGTH; x <= HALF_LENGTH; x += 3) {
			for (int z = -HALF_WIDTH; z <= HALF_WIDTH; z += 3) {
				for (int y = GROUND_Y; y <= GROUND_Y + 10; y += 2) {
					assertEquals(blueprint.get(x, y, z), other.get(x, y, z));
				}
			}
		}
	}

	@Test
	void forestExistsButNotNearTheLane() {
		int trees = 0;
		for (int x = -HALF_LENGTH + 1; x < HALF_LENGTH; x++) {
			for (int z = -HALF_WIDTH + 2; z < HALF_WIDTH - 1; z++) {
				Material m = blueprint.get(x, GROUND_Y + 2, z);
				if (m == Material.DARK_OAK_LOG || m == Material.SPRUCE_LOG || m == Material.MUSHROOM_STEM) {
					boolean tower = towerBox(TeamColor.RED).distance(x, GROUND_Y + 2, z) == 0 || towerBox(TeamColor.GREEN).distance(x, GROUND_Y + 2, z) == 0;
					if (!tower && Math.abs(z) < LANE_HALF_WIDTH + 6 && Math.abs(x) < WALL_FRONT_X - 6) {
						fail("Baum zu nah an der Lane bei " + x + "/" + z);
					}
					trees++;
				}
			}
		}
		assertTrue(trees > 40, "zu wenig Wald: " + trees);
	}

	@Test
	void destroyedTowerIsOnlyAStump() {
		for (ArenaBlueprint.Placement p : ArenaBlueprint.towerBlocks(TeamColor.RED, true)) {
			if (p.y() > GROUND_Y + 5) {
				assertEquals(Material.AIR, p.material());
			}
			if (p.y() == GROUND_Y + 1) {
				assertFalse(isLane(p.z()) && p.material().solid(), "Trümmer auf der Lane");
			}
		}
	}

	@Test
	void coreStageFollowsHealth() {
		assertEquals(0, ArenaBlueprint.coreStage(1.0));
		assertEquals(1, ArenaBlueprint.coreStage(0.6));
		assertEquals(2, ArenaBlueprint.coreStage(0.4));
		assertEquals(3, ArenaBlueprint.coreStage(0.1));
		assertEquals(4, ArenaBlueprint.coreStage(0.0));
		long magma = ArenaBlueprint.coreBlocks(TeamColor.GREEN, 4).stream().filter(p -> p.material() == Material.MAGMA_BLOCK).count();
		assertTrue(magma > 0);
	}
}
