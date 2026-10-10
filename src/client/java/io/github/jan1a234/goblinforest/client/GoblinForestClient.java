package io.github.jan1a234.goblinforest.client;

import io.github.jan1a234.goblinforest.GoblinForest;
import io.github.jan1a234.goblinforest.net.MatchStatePayload;
import io.github.jan1a234.goblinforest.net.OpenMenuPayload;
import io.github.jan1a234.goblinforest.registry.ModEntities;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Client-Einstiegspunkt: Goblin-Modell, HUD, Kriegsmenü, Tastenbelegung und die Draufsicht.
 * Der Client zeigt nur an und schickt Wünsche an den Server; entschieden wird alles dort.
 */
public class GoblinForestClient implements ClientModInitializer {
	/** Vanilla-Anzeigen, die in der Draufsicht nichts bedeuten (der Spieler ist nur unsichtbarer Beobachter). */
	private static final List<Identifier> HIDDEN_IN_MATCH = List.of(VanillaHudElements.CROSSHAIR, VanillaHudElements.HOTBAR,
			VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR, VanillaHudElements.ARMOR_BAR, VanillaHudElements.AIR_BAR,
			VanillaHudElements.MOUNT_HEALTH, VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL,
			VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.SPECTATOR_MENU, VanillaHudElements.SPECTATOR_TOOLTIP,
			VanillaHudElements.MOB_EFFECTS);

	private static CameraType cameraBeforeMatch;

	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.GOBLIN, GoblinRenderer::new);
		ModKeys.register();

		HudElementRegistry.addLast(GoblinForest.id("match_hud"), MatchHud::extract);
		for (Identifier id : HIDDEN_IN_MATCH) {
			HudElementRegistry.replaceElement(id, vanilla -> (graphics, delta) -> {
				if (!ClientMatchState.active()) {
					vanilla.extractRenderState(graphics, delta);
				}
			});
		}
		// Chat und Aktionsleisten-Text über die Befehlsleiste schieben, damit sie nichts verdecken.
		HudElementRegistry.replaceElement(VanillaHudElements.CHAT, vanilla -> raisedInMatch(vanilla, MatchHud.BAR_HEIGHT));
		HudElementRegistry.replaceElement(VanillaHudElements.OVERLAY_MESSAGE, vanilla -> raisedInMatch(vanilla, 20));

		ClientPlayNetworking.registerGlobalReceiver(MatchStatePayload.TYPE, (payload, context) -> ClientMatchState.update(payload));
		ClientPlayNetworking.registerGlobalReceiver(OpenMenuPayload.TYPE, (payload, context) -> openMenu(context.client()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientMatchState.reset();
			CommandView.reset();
			CommandScreen.disarm();
			WarDrums.reset(client);
			restoreCamera(client);
		});
		ClientTickEvents.END_CLIENT_TICK.register(GoblinForestClient::tick);
	}

	private static HudElement raisedInMatch(HudElement vanilla, int offset) {
		return (graphics, delta) -> {
			if (!CommandView.active()) {
				vanilla.extractRenderState(graphics, delta);
				return;
			}
			graphics.pose().pushMatrix();
			graphics.pose().translate(0, -offset);
			vanilla.extractRenderState(graphics, delta);
			graphics.pose().popMatrix();
		};
	}

	private static void openMenu(Minecraft client) {
		if (ClientMatchState.active() && (client.gui.screen() == null || client.gui.screen() instanceof CommandScreen)) {
			client.gui.setScreen(new WarMenuScreen());
		}
	}

	private static void tick(Minecraft client) {
		ClientMatchState.tick(client);
		WarDrums.tick(client);
		CommandView.tick(client);
		if (!CommandView.active() || client.player == null) {
			CommandScreen.disarm();
			restoreCamera(client);
			drainKeys();
			return;
		}
		// Die Draufsicht ist die einzige Ansicht im Match (DESIGN.md Abschnitt 10). Die Kamera setzt CameraMixin;
		// in der Ich-Perspektive zeichnet Minecraft die (unsichtbare) Spielfigur nicht.
		if (client.options.getCameraType() != CameraType.FIRST_PERSON) {
			if (cameraBeforeMatch == null) {
				cameraBeforeMatch = client.options.getCameraType();
			}
			client.options.setCameraType(CameraType.FIRST_PERSON);
		}
		// Ohne anderes Fenster liegt immer die Steuerung offen, damit die Maus frei ist.
		if (client.gui.screen() == null) {
			client.gui.setScreen(new CommandScreen());
		}
		drainKeys();
	}

	/** Tasten werden im Match über {@link CommandScreen} ausgewertet; liegengebliebene Klicks verwerfen. */
	private static void drainKeys() {
		if (ModKeys.MENU == null) {
			return;
		}
		while (ModKeys.MENU.consumeClick() || ModKeys.CENTER_CHIEFTAIN.consumeClick()) {
			// ohne Wirkung
		}
		for (ModKeys.Binding binding : ModKeys.ACTIONS) {
			while (binding.key().consumeClick()) {
				// ohne Wirkung
			}
		}
	}

	private static void restoreCamera(Minecraft client) {
		if (cameraBeforeMatch != null) {
			client.options.setCameraType(cameraBeforeMatch);
			cameraBeforeMatch = null;
		}
	}
}
