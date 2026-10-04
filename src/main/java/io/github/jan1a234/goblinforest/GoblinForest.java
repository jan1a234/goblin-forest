package io.github.jan1a234.goblinforest;

import io.github.jan1a234.goblinforest.command.GfCommand;
import io.github.jan1a234.goblinforest.config.Balance;
import io.github.jan1a234.goblinforest.config.BalanceLoader;
import io.github.jan1a234.goblinforest.game.MatchManager;
import io.github.jan1a234.goblinforest.net.ModNetworking;
import io.github.jan1a234.goblinforest.registry.ModEntities;
import io.github.jan1a234.goblinforest.registry.ModSounds;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server- und gemeinsamer Einstiegspunkt der Mod.
 *
 * <p>Alles Spielrelevante (Gold, Kämpfe, Spawns, Upgrades) wird ausschließlich auf dem Server berechnet,
 * siehe docs/DESIGN.md Abschnitt 11.2.
 */
public class GoblinForest implements ModInitializer {
	public static final String MOD_ID = "goblinforest";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static Balance balance;

	@Override
	public void onInitialize() {
		balance = BalanceLoader.loadDefaults();
		ModEntities.register();
		ModSounds.register();
		ModNetworking.registerPayloads();
		MatchManager.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> GfCommand.register(dispatcher));

		LOGGER.info("Goblin Forest {} geladen ({} Einheitentypen)", version(), balance.units().size());
	}

	/** Aktuelle Balancing-Werte aus data/goblinforest/balance.json. */
	public static Balance balance() {
		return balance;
	}

	public static String version() {
		return FabricLoader.getInstance().getModContainer(MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("dev");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
