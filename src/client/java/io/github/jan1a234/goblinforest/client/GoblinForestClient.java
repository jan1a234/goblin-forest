package io.github.jan1a234.goblinforest.client;

import io.github.jan1a234.goblinforest.GoblinForest;
import io.github.jan1a234.goblinforest.net.ActionPayload;
import io.github.jan1a234.goblinforest.net.MatchStatePayload;
import io.github.jan1a234.goblinforest.net.OpenMenuPayload;
import io.github.jan1a234.goblinforest.registry.ModEntities;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;

/**
 * Client-Einstiegspunkt: Goblin-Modell, HUD, Kriegsmenü, Tastenbelegung und Third-Person-Kamera.
 * Der Client zeigt nur an und schickt Wünsche an den Server; entschieden wird alles dort.
 */
public class GoblinForestClient implements ClientModInitializer {
	/** Gleicher Takt wie die serverseitige Sperre für Schläge gegen Gebäude. */
	private static final int STRUCTURE_HIT_INTERVAL = 12;

	private static CameraType cameraBeforeMatch;
	private static long lastStructureHit;

	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.GOBLIN, GoblinRenderer::new);
		ModKeys.register();

		HudElementRegistry.addLast(GoblinForest.id("match_hud"), MatchHud::extract);
		HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR, vanilla -> inMatch(MatchHud::extractHealth, vanilla));
		HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR, vanilla -> inMatch((g, d) -> {
		}, vanilla));
		HudElementRegistry.replaceElement(VanillaHudElements.ARMOR_BAR, vanilla -> inMatch((g, d) -> {
		}, vanilla));

		ClientPlayNetworking.registerGlobalReceiver(MatchStatePayload.TYPE, (payload, context) -> ClientMatchState.update(payload));
		ClientPlayNetworking.registerGlobalReceiver(OpenMenuPayload.TYPE, (payload, context) -> openMenu(context.client()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientMatchState.reset();
			restoreCamera(client);
		});
		ClientTickEvents.END_CLIENT_TICK.register(GoblinForestClient::tick);
	}

	private static HudElement inMatch(HudElement replacement, HudElement vanilla) {
		return (graphics, delta) -> {
			if (ClientMatchState.active()) {
				replacement.extractRenderState(graphics, delta);
			} else {
				vanilla.extractRenderState(graphics, delta);
			}
		};
	}

	private static void openMenu(Minecraft client) {
		if (ClientMatchState.active() && client.gui.screen() == null) {
			client.gui.setScreen(new WarMenuScreen());
		}
	}

	private static void tick(Minecraft client) {
		ClientMatchState.tick();
		boolean active = ClientMatchState.active() && client.player != null;
		if (!active) {
			restoreCamera(client);
			drainKeys();
			return;
		}
		// Goblin Forest wird aus der Verfolgerperspektive gespielt (DESIGN.md Abschnitt 9).
		if (client.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
			if (cameraBeforeMatch == null) {
				cameraBeforeMatch = client.options.getCameraType();
			}
			client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		}
		if (client.gui.screen() != null) {
			return;
		}
		while (ModKeys.MENU.consumeClick()) {
			openMenu(client);
		}
		for (ModKeys.Binding binding : ModKeys.ACTIONS) {
			while (binding.key().consumeClick()) {
				ClientPlayNetworking.send(new ActionPayload(binding.action()));
			}
		}
		long now = ClientMatchState.ticks();
		if (client.options.keyAttack.isDown() && client.hitResult != null && client.hitResult.getType() == HitResult.Type.BLOCK
				&& now - lastStructureHit >= STRUCTURE_HIT_INTERVAL) {
			lastStructureHit = now;
			client.player.swing(InteractionHand.MAIN_HAND);
			ClientPlayNetworking.send(new ActionPayload("hit_structure"));
		}
	}

	private static void drainKeys() {
		if (ModKeys.MENU == null) {
			return;
		}
		while (ModKeys.MENU.consumeClick()) {
			// außerhalb eines Matches ohne Wirkung
		}
		for (ModKeys.Binding binding : ModKeys.ACTIONS) {
			while (binding.key().consumeClick()) {
				// außerhalb eines Matches ohne Wirkung
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
