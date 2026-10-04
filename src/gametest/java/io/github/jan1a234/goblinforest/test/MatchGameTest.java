package io.github.jan1a234.goblinforest.test;

import io.github.jan1a234.goblinforest.arena.ArenaLayout;
import io.github.jan1a234.goblinforest.game.Match;
import io.github.jan1a234.goblinforest.game.MatchManager;
import io.github.jan1a234.goblinforest.game.MatchPhase;
import io.github.jan1a234.goblinforest.game.PurchaseResult;
import io.github.jan1a234.goblinforest.game.Structure;
import io.github.jan1a234.goblinforest.game.TeamColor;
import io.github.jan1a234.goblinforest.game.TeamState;
import io.github.jan1a234.goblinforest.unit.UnitType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

/**
 * Spielt auf einem echten Minecraft-Server ein Match ohne Spieler durch: Arena bauen, Countdown,
 * beide Clans rekrutieren, die Armeen laufen die Lane entlang und kämpfen, Einheiten sterben und steigen auf,
 * und am Ende fällt eine Festung. Läuft in der CI mit {@code ./gradlew runGameTest}.
 */
public class MatchGameTest {
	private static final int SETUP_LIMIT = 20 * 30;

	/** Ein Test für alles, weil es nur eine Arena gibt (Gametests einer Gruppe laufen parallel). */
	@GameTest(maxTicks = 20 * 240)
	public void fullMatchWithoutPlayers(GameTestHelper helper) {
		// Der Gametest-Server kennt keine Datenpaket-Dimensionen; die Arena entsteht deshalb in der Testwelt.
		Match match = MatchManager.startWithoutPlayers(helper.getLevel().getServer(), helper.getLevel());
		ServerLevel arena = match.arena();
		int[] stage = {0};
		helper.onEachTick(() -> {
			switch (stage[0]) {
				case 0 -> {
					if (MatchManager.match() != match) {
						helper.fail("Match wurde unerwartet beendet (Fehler im Match-Tick?)");
					} else if (match.phase() == MatchPhase.SETUP && helper.getTick() > SETUP_LIMIT) {
						helper.fail("Arena-Aufbau dauert zu lange");
					} else if (match.phase() == MatchPhase.BATTLE) {
						checkArenaBuilt(helper, arena);
						recruitArmies(helper, match);
						stage[0] = 1;
					}
				}
				case 1 -> {
					if (MatchManager.match() != match) {
						helper.fail("Match wurde während des Kampfes beendet (Fehler im Match-Tick?)");
						return;
					}
					TeamState red = match.team(TeamColor.RED);
					TeamState green = match.team(TeamColor.GREEN);
					int kills = red.stats().unitKills + green.stats().unitKills;
					int bestLevel = Math.max(red.stats().highestUnitLevel, green.stats().highestUnitLevel);
					if (kills >= 3 && bestLevel >= 2) {
						io.github.jan1a234.goblinforest.GoblinForest.LOGGER.info(
								"[Selbsttest] Tick {}: {} Kills, beste Stufe {}, Gold rot {} / grün {}, Goblins {}",
								helper.getTick(), kills, bestLevel, red.gold(), green.gold(), match.unitCount());
						destroyGreen(helper, match);
						stage[0] = 2;
					}
				}
				default -> {
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(stage[0] == 2, "Kampf noch nicht entschieden (Phase " + stage[0] + ")");
			helper.assertTrue(match.finished() && MatchManager.match() == null, "Match wurde nach dem Ende nicht aufgeräumt");
			helper.assertTrue(match.unitCount() == 0, "Einheiten nicht entfernt");
		});
	}

	private static void recruitArmies(GameTestHelper helper, Match match) {
		for (TeamColor team : TeamColor.values()) {
			match.grantGold(team, 5000);
			for (UnitType type : new UnitType[] {UnitType.WARRIOR, UnitType.WARRIOR, UnitType.ARCHER, UnitType.SLAVE, UnitType.WARRIOR, UnitType.ARCHER}) {
				PurchaseResult result = match.recruitForTest(team, type);
				helper.assertTrue(result.ok(), "Rekrutieren von " + type + " für " + team + " fehlgeschlagen: " + result);
			}
		}
		helper.assertTrue(match.unitCount() == 2 * 8, "Erwartet 16 Goblins, gefunden " + match.unitCount());
	}

	private static void destroyGreen(GameTestHelper helper, Match match) {
		TeamState green = match.team(TeamColor.GREEN);
		match.damageStructure(TeamColor.GREEN, Structure.TOWER, green.towerMaxHealth() + 1, null, TeamColor.RED);
		helper.assertTrue(!green.towerAlive(), "Turm sollte zerstört sein");
		helper.assertTrue(match.team(TeamColor.RED).reputation() >= 1, "Turmzerstörung gibt keinen Ruf");
		match.damageStructure(TeamColor.GREEN, Structure.CORE, green.coreMaxHealth() * 0.6, null, TeamColor.RED);
		ArenaLayout.Point core = ArenaLayout.coreBox(TeamColor.GREEN).center();
		helper.assertTrue(!match.arena().getBlockState(BlockPos.containing(core.x(), ArenaLayout.GROUND_Y + 1, core.z())).isAir(), "Kern sollte noch stehen");
		helper.assertTrue(match.phase() == MatchPhase.BATTLE, "Match darf bei 40 % Kern noch nicht enden");
		match.damageStructure(TeamColor.GREEN, Structure.CORE, green.coreMaxHealth(), null, TeamColor.RED);
		helper.assertTrue(match.phase() == MatchPhase.ENDED, "Match sollte nach zerstörtem Kern enden");
	}

	private static void checkArenaBuilt(GameTestHelper helper, ServerLevel arena) {
		for (TeamColor team : TeamColor.values()) {
			ArenaLayout.Point barracks = ArenaLayout.barracks(team);
			BlockPos floor = BlockPos.containing(barracks.x(), barracks.y() - 1, barracks.z());
			helper.assertTrue(!arena.getBlockState(floor).isAir(), "Kein Boden an der Kaserne " + team);
			helper.assertTrue(arena.getBlockState(floor.above()).isAir(), "Kaserne " + team + " ist zugebaut");
			ArenaLayout.Point tower = ArenaLayout.towerBox(team).center();
			helper.assertTrue(!arena.getBlockState(BlockPos.containing(tower.x(), ArenaLayout.GROUND_Y + 3, tower.z())).isAir(), "Turm " + team + " fehlt");
		}
	}

}
