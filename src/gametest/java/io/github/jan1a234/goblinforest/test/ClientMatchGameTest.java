package io.github.jan1a234.goblinforest.test;

import io.github.jan1a234.goblinforest.client.ClientMatchState;
import io.github.jan1a234.goblinforest.client.CommandScreen;
import io.github.jan1a234.goblinforest.client.CommandView;
import io.github.jan1a234.goblinforest.client.MatchHud;
import io.github.jan1a234.goblinforest.client.ModKeys;
import io.github.jan1a234.goblinforest.client.WarMenuScreen;
import io.github.jan1a234.goblinforest.game.AiDifficulty;
import io.github.jan1a234.goblinforest.game.Match;
import io.github.jan1a234.goblinforest.game.MatchManager;
import io.github.jan1a234.goblinforest.game.MatchPhase;
import io.github.jan1a234.goblinforest.game.TeamColor;
import io.github.jan1a234.goblinforest.net.MatchStatePayload;
import io.github.jan1a234.goblinforest.unit.UnitType;
import io.github.jan1a234.goblinforest.upgrade.UpgradeKey;
import io.github.jan1a234.goblinforest.upgrade.UpgradeType;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.lwjgl.glfw.GLFW;

/**
 * Startet einen echten Minecraft-Client, spielt ein kurzes Match gegen die KI an und bedient es wie ein Spieler:
 * Häuptling per Taste losschicken, Einheiten per Klick auf die Befehlsleiste rekrutieren, Feuerball wählen und ins Feld
 * klicken, Kamera schwenken und zoomen. Dazu Bildschirmfotos von HUD, Armee und Kriegsmenü, die die CI hochlädt
 * ({@code ./gradlew runClientGameTest}). Prüft außerdem, dass jedes Feld der Befehlsleiste vollständig im Bild liegt.
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
				MatchManager.Result result = MatchManager.start(server, player, false, AiDifficulty.EASY, 3, false);
				if (!result.success()) {
					throw new AssertionError("Match startet nicht: " + result.message().getString());
				}
			});
			context.waitFor(client -> ClientMatchState.get().inMatch() && ClientMatchState.get().phase() == MatchPhase.BATTLE.ordinal(), 20 * 180);
			context.waitTicks(30);

			// Nur die Draufsicht: Steuerung offen, Spieler als Zuschauer, kein Häuptling in der eigenen Hand.
			check(context.computeOnClient(client -> CommandView.active() && client.gui.screen() instanceof CommandScreen
					&& client.options.getCameraType() == CameraType.FIRST_PERSON), "Draufsicht samt Steuerung sollte offen sein");
			check(singleplayer.getServer().computeOnServer(server ->
					server.getPlayerList().getPlayers().getFirst().isSpectator()), "Feldherr sollte Zuschauer sein");
			checkBarFits(context);
			context.takeScreenshot("goblinforest-01-draufsicht");

			// Häuptling mit Q losschicken.
			context.getInput().pressKey(actionKey("chieftain:toggle"));
			context.waitTicks(10);
			check(singleplayer.getServer().computeOnServer(server -> MatchManager.match().chieftainDeployed(TeamColor.RED)),
					"Q sollte den Häuptling losschicken");

			// Gold geben und einen Krieger per Klick auf die Leiste rekrutieren.
			singleplayer.getServer().runOnServer(server -> MatchManager.match().grantGold(TeamColor.RED, 20000));
			context.waitTicks(15);
			int recruitedBefore = recruited(singleplayer);
			clickHotspot(context, "recruit:warrior");
			context.waitTicks(10);
			check(recruited(singleplayer) == recruitedBefore + 1, "Klick auf den Krieger sollte einen Krieger rekrutieren");

			// Feuerball wählen, Bildmitte anklicken.
			clickHotspot(context, "cast:fireball");
			context.waitTicks(2);
			check("cast:fireball".equals(context.computeOnClient(client -> CommandScreen.armedAction())), "Feuerball sollte gewählt sein");
			context.waitTicks(4);
			context.takeScreenshot("goblinforest-02-feuerball-zielen");
			int castBefore = singleplayer.getServer().computeOnServer(server -> MatchManager.match().team(TeamColor.RED).stats().spellsCast);
			clickGui(context, 0.5, 0.45);
			context.waitTicks(10);
			check(context.computeOnClient(client -> CommandScreen.armedAction()) == null, "Nach dem Klick sollte kein Zauber mehr gewählt sein");
			check(singleplayer.getServer().computeOnServer(server -> MatchManager.match().team(TeamColor.RED).stats().spellsCast) == castBefore + 1,
					"Klick ins Feld sollte den Feuerball wirken");

			// Rechtsklick bricht einen gewählten Zauber ab.
			clickHotspot(context, "cast:healing");
			context.waitTicks(2);
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			context.waitTicks(2);
			check(context.computeOnClient(client -> CommandScreen.armedAction()) == null, "Rechtsklick sollte den Zauber abbrechen");

			// Eine gemischte Armee, damit Modelle, Warg und Katapult-Karren im Bild sind.
			singleplayer.getServer().runOnServer(server -> {
				Match match = MatchManager.match();
				match.team(TeamColor.RED).addClanXp(400 * 5);
				for (int i = 0; i < 2; i++) {
					match.buyUpgradeFor(TeamColor.RED, UpgradeKey.stronghold(UpgradeType.HUTS));
				}
				for (UnitType type : ARMY) {
					match.recruitForTest(TeamColor.RED, type);
				}
			});
			context.waitTicks(60);
			context.takeScreenshot("goblinforest-03-armee");

			// Zoomen mit dem Mausrad, schwenken mit D.
			double before = context.computeOnClient(client -> CommandView.distance());
			context.getInput().scroll(3);
			context.waitTicks(20);
			check(context.computeOnClient(client -> CommandView.distance()) < before, "Mausrad sollte heranzoomen");
			context.takeScreenshot("goblinforest-04-nah");
			double focusBefore = context.computeOnClient(client -> CommandView.focusX());
			context.getInput().holdKeyFor(options -> options.keyRight, 30);
			context.waitTicks(5);
			check(context.computeOnClient(client -> CommandView.focusX()) > focusBefore + 5, "D sollte die Kamera nach rechts schwenken");
			context.takeScreenshot("goblinforest-05-geschwenkt");
			context.getInput().scroll(-6);
			context.waitTicks(20);

			// Kriegsmenü über das Buch in der Leiste.
			clickHotspot(context, "menu");
			context.waitForScreen(WarMenuScreen.class);
			context.waitTicks(5);
			context.takeScreenshot("goblinforest-06-menue-einheiten");
			for (String tab : new String[] {"menu.goblinforest.tab.upgrades", "menu.goblinforest.tab.stronghold",
					"menu.goblinforest.tab.magic", "menu.goblinforest.tab.chieftain"}) {
				String label = Component.translatable(tab).getString();
				if (context.tryClickScreenButton(label)) {
					context.waitTicks(3);
					context.takeScreenshot("goblinforest-07-menue-" + tab.substring(tab.lastIndexOf('.') + 1));
				}
			}
			context.getInput().pressKey(ModKeys.MENU);
			context.waitForScreen(CommandScreen.class);

			// Kamera zum Häuptling, dann die Schlacht laufen lassen.
			context.getInput().pressKey(ModKeys.CENTER_CHIEFTAIN);
			context.waitTicks(200);
			checkBarFits(context);
			context.takeScreenshot("goblinforest-08-gefecht");

			singleplayer.getServer().runOnServer(server -> MatchManager.stop(Component.literal("Test")));
			context.waitFor(client -> !ClientMatchState.get().inMatch(), 20 * 60);
			context.waitTicks(20);
			check(context.computeOnClient(client -> !(client.gui.screen() instanceof CommandScreen)), "Nach dem Match sollte die Steuerung zu sein");
		}
	}

	private static KeyMapping actionKey(String action) {
		return ModKeys.ACTIONS.stream().filter(b -> b.action().equals(action)).findFirst().orElseThrow().key();
	}

	private static int recruited(TestSingleplayerContext singleplayer) {
		return singleplayer.getServer().computeOnServer(server -> MatchManager.match().team(TeamColor.RED).stats().unitsRecruited);
	}

	/** Jedes Feld der Befehlsleiste und das Häuptlingsfeld müssen ganz im Bild liegen (nichts abgeschnitten). */
	private static void checkBarFits(ClientGameTestContext context) {
		String problem = context.computeOnClient(client -> {
			int w = client.getWindow().getGuiScaledWidth();
			int h = client.getWindow().getGuiScaledHeight();
			MatchStatePayload s = ClientMatchState.get();
			String[] required = {"recruit:slave", "recruit:catapult", "stance:next", "rally", "cast:fireball", "cast:meteor",
					"chieftain:toggle", "ability:rage", "menu", "center"};
			for (String action : required) {
				MatchHud.Hotspot spot = MatchHud.hotspot(action);
				if (spot == null) {
					return "Feld fehlt: " + action + " (Team " + s.team() + ")";
				}
				if (spot.x() < 0 || spot.y() < 0 || spot.x() + spot.w() > w || spot.y() + spot.h() > h) {
					return "Feld " + action + " ragt aus dem Bild: " + spot + " bei " + w + "x" + h;
				}
			}
			MatchHud.Hotspot menu = MatchHud.hotspot("menu");
			MatchHud.Hotspot center = MatchHud.hotspot("center");
			boolean overlap = center.x() < menu.x() + menu.w() && menu.x() < center.x() + center.w()
					&& center.y() < menu.y() + menu.h() && menu.y() < center.y() + center.h();
			return overlap ? "Häuptlingsfeld überdeckt die Leiste" : null;
		});
		check(problem == null, problem);
	}

	private static void clickHotspot(ClientGameTestContext context, String action) {
		double[] pos = context.computeOnClient(client -> {
			MatchHud.Hotspot spot = MatchHud.hotspot(action);
			if (spot == null) {
				return null;
			}
			return new double[] {spot.x() + spot.w() / 2.0, spot.y() + spot.h() / 2.0};
		});
		check(pos != null, "Feld nicht gefunden: " + action);
		clickAt(context, pos[0], pos[1]);
	}

	/** Klick an eine Stelle, angegeben als Anteil der Bildbreite und -höhe. */
	private static void clickGui(ClientGameTestContext context, double fx, double fy) {
		double[] pos = context.computeOnClient(client -> new double[] {client.getWindow().getGuiScaledWidth() * fx,
				client.getWindow().getGuiScaledHeight() * fy});
		clickAt(context, pos[0], pos[1]);
	}

	/** Klick an GUI-Koordinaten (umgerechnet in Fensterpixel). */
	private static void clickAt(ClientGameTestContext context, double guiX, double guiY) {
		double[] window = context.computeOnClient(client -> new double[] {
				guiX * client.getWindow().getScreenWidth() / client.getWindow().getGuiScaledWidth(),
				guiY * client.getWindow().getScreenHeight() / client.getWindow().getGuiScaledHeight()});
		context.getInput().setCursorPos(window[0], window[1]);
		context.waitTick();
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
