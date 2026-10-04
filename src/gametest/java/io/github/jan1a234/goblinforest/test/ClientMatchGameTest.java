package io.github.jan1a234.goblinforest.test;

import io.github.jan1a234.goblinforest.arena.ArenaLayout;
import io.github.jan1a234.goblinforest.client.ClientMatchState;
import io.github.jan1a234.goblinforest.client.CommandView;
import io.github.jan1a234.goblinforest.client.ModKeys;
import io.github.jan1a234.goblinforest.client.WarMenuScreen;
import io.github.jan1a234.goblinforest.game.AiDifficulty;
import io.github.jan1a234.goblinforest.game.Match;
import io.github.jan1a234.goblinforest.game.MatchManager;
import io.github.jan1a234.goblinforest.game.MatchPhase;
import io.github.jan1a234.goblinforest.game.TeamColor;
import io.github.jan1a234.goblinforest.unit.UnitType;
import io.github.jan1a234.goblinforest.upgrade.UpgradeKey;
import io.github.jan1a234.goblinforest.upgrade.UpgradeType;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Startet einen echten Minecraft-Client, spielt ein kurzes Match gegen die KI an und macht Bildschirmfotos
 * von HUD, Einheiten, Kriegsmenü und Kommandoansicht. Prüft dabei, dass Rendering, Mixins und Tasten ohne Absturz laufen.
 * Die Bilder lädt die CI als Artefakt hoch ({@code ./gradlew runClientGameTest}).
 */
public class ClientMatchGameTest implements FabricClientGameTest {
	private static final UnitType[] ARMY = {UnitType.WOLF_RIDER, UnitType.WARRIOR, UnitType.ARCHER, UnitType.TROLL,
			UnitType.SHAMAN, UnitType.CATAPULT, UnitType.WOLF_RIDER, UnitType.ASSASSIN, UnitType.SLAVE};

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(40);
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				MatchManager.join(player, TeamColor.RED);
				MatchManager.Result result = MatchManager.start(server, player, false, AiDifficulty.EASY, 3);
				if (!result.success()) {
					throw new AssertionError("Match startet nicht: " + result.message().getString());
				}
			});
			context.waitFor(client -> ClientMatchState.get().inMatch() && ClientMatchState.get().phase() == MatchPhase.BATTLE.ordinal(), 20 * 180);
			context.waitTicks(30);
			context.takeScreenshot("goblinforest-01-schlacht");

			// Eine gemischte Armee direkt vor dem Häuptling, damit Modelle, Warg und Katapult-Karren im Bild sind.
			singleplayer.getServer().runOnServer(server -> {
				Match match = MatchManager.match();
				match.grantGold(TeamColor.RED, 20000);
				match.team(TeamColor.RED).addClanXp(400 * 5);
				for (int i = 0; i < 2; i++) {
					match.buyUpgradeFor(TeamColor.RED, UpgradeKey.stronghold(UpgradeType.HUTS));
				}
				for (UnitType type : ARMY) {
					match.recruitForTest(TeamColor.RED, type);
				}
			});
			ArenaLayout.Point barracks = ArenaLayout.barracks(TeamColor.RED);
			context.getInput().lookAt(BlockPos.containing(barracks.x() + 6, barracks.y() + 1, barracks.z()));
			context.waitTicks(50);
			context.takeScreenshot("goblinforest-02-armee");

			context.getInput().pressKey(ModKeys.MENU);
			context.waitForScreen(WarMenuScreen.class);
			context.waitTicks(5);
			context.takeScreenshot("goblinforest-03-menue-einheiten");
			for (String tab : new String[] {"menu.goblinforest.tab.upgrades", "menu.goblinforest.tab.stronghold",
					"menu.goblinforest.tab.magic", "menu.goblinforest.tab.chieftain"}) {
				String label = Component.translatable(tab).getString();
				if (context.tryClickScreenButton(label)) {
					context.waitTicks(3);
					context.takeScreenshot("goblinforest-04-menue-" + tab.substring(tab.lastIndexOf('.') + 1));
				}
			}
			context.setScreen(() -> null);
			context.waitTicks(5);

			context.getInput().pressKey(ModKeys.COMMAND_VIEW);
			context.waitTicks(40);
			if (!context.computeOnClient(client -> CommandView.active())) {
				throw new AssertionError("Kommandoansicht ließ sich nicht öffnen");
			}
			context.takeScreenshot("goblinforest-05-kommandoansicht");
			context.getInput().holdKeyFor(options -> options.keyRight, 40);
			context.waitTicks(10);
			context.takeScreenshot("goblinforest-06-kommandoansicht-geschwenkt");
			boolean frozen = singleplayer.getServer().computeOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				return player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) <= 0.0001;
			});
			if (!frozen) {
				throw new AssertionError("Häuptling bewegt sich in der Kommandoansicht");
			}
			context.getInput().pressKey(ModKeys.COMMAND_VIEW);
			context.waitTicks(40);
			if (context.computeOnClient(client -> CommandView.active())) {
				throw new AssertionError("Kommandoansicht ließ sich nicht schließen");
			}

			context.waitTicks(200);
			context.takeScreenshot("goblinforest-07-gefecht");

			singleplayer.getServer().runOnServer(server -> MatchManager.stop(Component.literal("Test")));
			context.waitFor(client -> !ClientMatchState.get().inMatch(), 20 * 60);
			context.waitTicks(20);
		}
	}
}
