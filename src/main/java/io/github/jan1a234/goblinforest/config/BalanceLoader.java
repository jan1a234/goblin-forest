package io.github.jan1a234.goblinforest.config;

import com.google.gson.Gson;
import io.github.jan1a234.goblinforest.hero.AbilityType;
import io.github.jan1a234.goblinforest.spell.SpellType;
import io.github.jan1a234.goblinforest.unit.UnitType;
import io.github.jan1a234.goblinforest.upgrade.UpgradeType;
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
		if (balance == null || balance.match() == null || balance.economy() == null || balance.units() == null
				|| balance.traits() == null || balance.unitLeveling() == null || balance.upgrades() == null
				|| balance.stronghold() == null || balance.hero() == null || balance.abilities() == null
				|| balance.rage() == null || balance.spells() == null || balance.unitLeveling().champion() == null) {
			throw new IllegalStateException("Balancing-Datei unvollständig");
		}
		for (UnitType type : UnitType.values()) {
			Balance.UnitStats stats = balance.unit(type);
			if (type.soldier()) {
				require(stats.groupSize() >= 1 && stats.cost() > 0 && stats.health() > 0, "Einheit " + type.id() + " hat ungültige Werte");
			} else {
				require(stats.groupSize() == 1 && stats.speed() > 0, "Der Häuptling braucht groupSize 1 und ein Tempo");
			}
			require(stats.attackCooldownTicks() > 0, "Einheit " + type.id() + " braucht attackCooldownTicks > 0");
			require(stats.scale() > 0.2 && stats.scale() <= 3.0, "Einheit " + type.id() + " braucht eine Größe zwischen 0.2 und 3");
		}
		var levels = balance.unitLeveling().levels();
		require(levels != null && !levels.isEmpty() && levels.getFirst().xpRequired() == 0, "Level 1 muss bei 0 EP beginnen");
		for (int i = 1; i < levels.size(); i++) {
			require(levels.get(i).xpRequired() > levels.get(i - 1).xpRequired(), "EP-Schwellen der Level müssen aufsteigend sein");
		}
		for (UpgradeType type : UpgradeType.values()) {
			Balance.UpgradeTrack track = balance.upgrade(type.id());
			require(track.costs() != null && !track.costs().isEmpty(), "Upgrade " + type.id() + " braucht Kosten");
			require(track.unlockReputation() != null && track.unlockReputation().size() == track.costs().size(),
					"Upgrade " + type.id() + ": unlockReputation muss so lang sein wie costs");
		}
		for (AbilityType ability : AbilityType.values()) {
			Balance.Ability config = balance.ability(ability.id());
			require(ability.charged() || config.cooldownSeconds() > 0, "Fähigkeit " + ability.id() + " braucht eine Abklingzeit");
			require(config.maxRank() >= 0, "Fähigkeit " + ability.id() + ": maxRank darf nicht negativ sein");
		}
		require(balance.rage().maxCharge() > 0, "rage.maxCharge muss größer als 0 sein");
		for (SpellType spell : SpellType.values()) {
			Balance.Spell config = balance.spell(spell.id());
			require(config.upgradeCosts() != null && config.upgradeReputation() != null
					&& config.upgradeCosts().size() == config.upgradeReputation().size(),
					"Zauber " + spell.id() + ": upgradeCosts und upgradeReputation müssen gleich lang sein");
		}
		var heroXp = balance.hero().xpForLevel();
		require(heroXp != null && heroXp.size() >= balance.hero().maxLevel() && heroXp.getFirst() == 0,
				"hero.xpForLevel braucht einen Eintrag pro Level und beginnt bei 0");
		for (int i = 1; i < heroXp.size(); i++) {
			require(heroXp.get(i) > heroXp.get(i - 1), "hero.xpForLevel muss aufsteigend sein");
		}
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
	}
}
