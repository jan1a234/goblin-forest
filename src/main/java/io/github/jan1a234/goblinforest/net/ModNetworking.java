package io.github.jan1a234.goblinforest.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Registriert die Netzwerk-Pakete (auf Server und Client gleich). */
public final class ModNetworking {
	private ModNetworking() {
	}

	public static void registerPayloads() {
		PayloadTypeRegistry.serverboundPlay().register(ActionPayload.TYPE, ActionPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MatchStatePayload.TYPE, MatchStatePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenMenuPayload.TYPE, OpenMenuPayload.CODEC);
	}
}
