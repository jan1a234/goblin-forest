package io.github.jan1a234.goblinforest.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jan1a234.goblinforest.game.TeamColor;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArenaLayoutTest {
	@Test
	void fortressesAreMirrored() {
		ArenaLayout.Box red = ArenaLayout.coreBox(TeamColor.RED);
		ArenaLayout.Box green = ArenaLayout.coreBox(TeamColor.GREEN);
		assertEquals(-green.maxX(), red.minX());
		assertTrue(red.center().x() < 0);
		assertTrue(green.center().x() > 0);
	}

	@Test
	void waypointsLeadFromOwnGateToEnemyCore() {
		List<ArenaLayout.Point> red = ArenaLayout.laneWaypoints(TeamColor.RED);
		assertTrue(red.getFirst().x() < 0);
		ArenaLayout.Point last = red.getLast();
		assertTrue(last.x() > 0);
		// Der letzte Wegpunkt liegt vor dem feindlichen Kern, nicht darin.
		assertTrue(ArenaLayout.coreBox(TeamColor.GREEN).distance(last.x(), last.y(), last.z()) > 0);
		assertTrue(ArenaLayout.coreBox(TeamColor.GREEN).distance(last.x(), last.y(), last.z()) < 4);
		for (int i = 1; i < red.size(); i++) {
			assertTrue(red.get(i).x() > red.get(i - 1).x(), "Wegpunkte müssen nach Osten laufen");
		}
		List<ArenaLayout.Point> green = ArenaLayout.laneWaypoints(TeamColor.GREEN);
		assertEquals(red.size(), green.size());
		assertEquals(-red.getFirst().x() + 1, green.getFirst().x(), 1e-9);
	}

	@Test
	void progressIsRelativeToTeam() {
		assertEquals(0.0, ArenaLayout.progress(TeamColor.RED, -ArenaLayout.CORE_X), 1e-9);
		assertEquals(1.0, ArenaLayout.progress(TeamColor.RED, ArenaLayout.CORE_X), 1e-9);
		assertEquals(0.5, ArenaLayout.progress(TeamColor.GREEN, 0), 1e-9);
		assertEquals(0.0, ArenaLayout.progress(TeamColor.GREEN, ArenaLayout.CORE_X), 1e-9);
	}

	@Test
	void spawnsAreInsideOwnHealZoneAndOutsideCore() {
		for (TeamColor team : TeamColor.values()) {
			ArenaLayout.Point hero = ArenaLayout.heroSpawn(team);
			ArenaLayout.Point barracks = ArenaLayout.barracks(team);
			assertTrue(ArenaLayout.healZone(team).contains((int) Math.floor(hero.x()), (int) hero.y(), (int) Math.floor(hero.z())));
			assertTrue(ArenaLayout.healZone(team).contains((int) Math.floor(barracks.x()), (int) barracks.y(), (int) Math.floor(barracks.z())));
			assertTrue(ArenaLayout.coreBox(team).distance(hero.x(), hero.y(), hero.z()) > 0.5);
			assertTrue(ArenaLayout.coreBox(team).distance(barracks.x(), barracks.y(), barracks.z()) > 0.5);
		}
	}

	@Test
	void towerStandsBesideTheLane() {
		ArenaLayout.Box tower = ArenaLayout.towerBox(TeamColor.RED);
		for (int z = tower.minZ(); z <= tower.maxZ(); z++) {
			assertFalse(ArenaLayout.isLane(z), "Turm darf die Lane nicht blockieren");
		}
		assertTrue(Math.abs(tower.center().x()) < ArenaLayout.WALL_FRONT_X, "Turm steht vor der Mauer");
	}
}
