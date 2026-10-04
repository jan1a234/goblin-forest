package io.github.jan1a234.goblinforest.test;

import io.github.jan1a234.goblinforest.arena.ArenaLayout;
import io.github.jan1a234.goblinforest.game.Match;
import io.github.jan1a234.goblinforest.game.MatchManager;
import io.github.jan1a234.goblinforest.game.MatchPhase;
import io.github.jan1a234.goblinforest.game.PurchaseResult;
import io.github.jan1a234.goblinforest.game.Structure;
import io.github.jan1a234.goblinforest.game.TeamColor;
import io.github.jan1a234.goblinforest.game.TeamState;
import io.github.jan1a234.goblinforest.spell.SpellType;
import io.github.jan1a234.goblinforest.unit.UnitType;
import io.github.jan1a234.goblinforest.upgrade.UpgradeKey;
import io.github.jan1a234.goblinforest.upgrade.UpgradeType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

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
						buildStrongholds(helper, match, arena);
						castSpells(helper, match);
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
		UnitType[] army = {UnitType.WARRIOR, UnitType.WARRIOR, UnitType.ARCHER, UnitType.SLAVE, UnitType.WARRIOR, UnitType.ARCHER,
				UnitType.ASSASSIN, UnitType.SHAMAN, UnitType.WOLF_RIDER, UnitType.TROLL, UnitType.CATAPULT};
		for (TeamColor team : TeamColor.values()) {
			match.grantGold(team, 20000);
			// Ruf 5: alles freigeschaltet
			match.team(team).addClanXp(400 * 5);
			for (int i = 0; i < 3; i++) {
				PurchaseResult huts = match.upgradeForTest(team, UpgradeKey.stronghold(UpgradeType.HUTS));
				helper.assertTrue(huts.ok(), "Hütten für " + team + ": " + huts);
			}
			for (UnitType type : army) {
				PurchaseResult result = match.recruitForTest(team, type);
				helper.assertTrue(result.ok(), "Rekrutieren von " + type + " für " + team + " fehlgeschlagen: " + result);
			}
			for (UnitType type : UnitType.values()) {
				helper.assertTrue(match.unitCount(team, type) > 0, "Keine Einheit vom Typ " + type + " für " + team);
			}
		}
		helper.assertTrue(match.unitCount() == 2 * 13, "Erwartet 26 Goblins, gefunden " + match.unitCount());
	}

	/** Kanone, Goldmine, Ausbildung und Ausdauer kaufen und prüfen, dass die Bauwerke in der Arena stehen. */
	private static void buildStrongholds(GameTestHelper helper, Match match, ServerLevel arena) {
		for (TeamColor team : TeamColor.values()) {
			for (UpgradeType type : new UpgradeType[] {UpgradeType.CANNON, UpgradeType.GOLDMINE, UpgradeType.GOLDMINE, UpgradeType.TRAINING, UpgradeType.ENDURANCE}) {
				PurchaseResult result = match.upgradeForTest(team, UpgradeKey.stronghold(type));
				helper.assertTrue(result.ok(), type + " für " + team + ": " + result);
			}
			ArenaLayout.Point cannon = ArenaLayout.cannonBlock(team);
			helper.assertTrue(arena.getBlockState(BlockPos.containing(cannon.x(), cannon.y(), cannon.z())).is(Blocks.DISPENSER), "Kanone " + team + " fehlt");
			ArenaLayout.Point mine = ArenaLayout.goldmine(team);
			helper.assertTrue(arena.getBlockState(BlockPos.containing(mine.x(), mine.y(), mine.z())).is(Blocks.RAW_GOLD_BLOCK), "Goldmine " + team + " fehlt");
			helper.assertTrue(match.team(team).incomePerSecond() > 3.5, "Goldmine bringt kein Einkommen");
			helper.assertTrue(match.team(team).startingUnitLevel(5) == 2, "Ausbildung wirkt nicht");
		}
		// Neue Einheiten mit Ausbildung starten als Kämpfer
		PurchaseResult trained = match.recruitForTest(TeamColor.RED, UnitType.WARRIOR);
		helper.assertTrue(trained.ok(), "Rekrutieren nach Ausbildung: " + trained);
	}

	/** Alle Zauber einmal wirken; die Effekte laufen zeitversetzt weiter und dürfen das Match nicht abbrechen. */
	private static void castSpells(GameTestHelper helper, Match match) {
		Vec3 bridge = new Vec3(0.5, ArenaLayout.GROUND_Y + 1, 0.5);
		for (SpellType spell : SpellType.values()) {
			TeamColor caster = spell == SpellType.HEALING ? TeamColor.GREEN : TeamColor.RED;
			PurchaseResult result = match.castForTest(caster, spell, bridge);
			helper.assertTrue(result.ok(), "Zauber " + spell + " fehlgeschlagen: " + result);
		}
		Vec3 greenBarracks = new Vec3(ArenaLayout.barracks(TeamColor.GREEN).x(), ArenaLayout.GROUND_Y + 1, 0.5);
		PurchaseResult again = match.castForTest(TeamColor.RED, SpellType.FIREBALL, greenBarracks);
		helper.assertTrue(again == PurchaseResult.ON_COOLDOWN, "Feuerball sollte Abklingzeit haben, war " + again);
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
