package io.github.jan1a234.goblinforest.config;

import com.google.gson.Gson;
import io.github.jan1a234.goblinforest.unit.UnitType;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Liest die Balancing-Datei.
 *
 * <p>Aktuell aus der Mod-Jar selbst. Später kann ein Datapack die Datei überschreiben
 * (Reload-Listener auf {@code data/goblinforest/balance.json}).
 */
public final class BalanceLoader {
	public static final String RESOURCE_PATH = "/data/goblinforest/balance.json";

	private static final Gson GSON = new Gson();

	private BalanceLoader() {
	}

	public static Balance loadDefaults() {
		try (InputStream in = BalanceLoader.class.getResourceAsStream(RESOURCE_PATH)) {
			if (in == null) {
				throw new IllegalStateException("Balancing-Datei fehlt: " + RESOURCE_PATH);
			}
			return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static Balance parse(Reader reader) {
		Balance balance = GSON.fromJson(reader, Balance.class);
		validate(balance);
		return balance;
	}

	private static void validate(Balance balance) {
		if (balance == null || balance.economy() == null || balance.units() == null
				|| balance.unitLeveling() == null || balance.stronghold() == null || balance.hero() == null) {
			throw new IllegalStateException("Balancing-Datei unvollständig");
		}
		for (UnitType type : UnitType.values()) {
			balance.unit(type);
		}
		var levels = balance.unitLeveling().levels();
		if (levels == null || levels.isEmpty() || levels.getFirst().xpRequired() != 0) {
			throw new IllegalStateException("Level 1 muss bei 0 EP beginnen");
		}
		for (int i = 1; i < levels.size(); i++) {
			if (levels.get(i).xpRequired() <= levels.get(i - 1).xpRequired()) {
				throw new IllegalStateException("EP-Schwellen der Level müssen aufsteigend sein");
			}
		}
	}
}
