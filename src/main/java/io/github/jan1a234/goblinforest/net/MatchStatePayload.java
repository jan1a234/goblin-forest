package io.github.jan1a234.goblinforest.net;

import io.github.jan1a234.goblinforest.GoblinForest;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Alles, was HUD und Kriegsmenü eines Spielers anzeigen. Wird etwa zweimal pro Sekunde geschickt.
 * {@code team} = -1 bedeutet: der Spieler ist in keinem Match (HUD aus, Kamera zurück).
 * {@code chieftainState}: {@link #CHIEFTAIN_HOME} (bewacht die Festung), {@link #CHIEFTAIN_FIELD} (auf dem Schlachtfeld)
 * oder {@link #CHIEFTAIN_DEAD} (wartet auf die Wiederbelebung).
 *
 * <p>Kaufbare Einträge kommen fertig berechnet vom Server ({@link ShopEntry}), damit Client und Server
 * nie unterschiedliche Preise oder Sperren anzeigen.
 */
public record MatchStatePayload(
		int phase,
		int team,
		int phaseSeconds,
		int matchSeconds,
		int gold,
		int reputation,
		float reputationProgress,
		int population,
		int populationLimit,
		int stance,
		int heroLevel,
		float heroProgress,
		int chieftainState,
		int chieftainRespawnSeconds,
		float chieftainHealth,
		float chieftainMaxHealth,
		float chieftainX,
		float chieftainZ,
		boolean chieftainSent,
		float[] coreHealth,
		float[] coreMaxHealth,
		float[] towerHealth,
		float[] towerMaxHealth,
		int[] unitCounts,
		int enemyUnits,
		int enemyHeroLevel,
		int enemyReputation,
		boolean rallySet,
		List<ShopEntry> shop,
		List<Cooldown> cooldowns,
		float[] spellRadii,
		int abilityPoints,
		float rageCharge,
		int rageSeconds,
		int suddenDeathSeconds,
		float income,
		int[] roundWins,
		int bestOf
) implements CustomPacketPayload {
	public static final int CHIEFTAIN_HOME = 0;
	public static final int CHIEFTAIN_FIELD = 1;
	public static final int CHIEFTAIN_DEAD = 2;

	public static final Type<MatchStatePayload> TYPE = new Type<>(GoblinForest.id("match_state"));
	public static final StreamCodec<FriendlyByteBuf, MatchStatePayload> CODEC = CustomPacketPayload.codec(MatchStatePayload::write, MatchStatePayload::read);

	/** Leerer Zustand: kein Match. */
	public static MatchStatePayload none() {
		return new MatchStatePayload(0, -1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, CHIEFTAIN_HOME, 0, 0, 0, 0, 0, false,
				new float[2], new float[2], new float[2], new float[2], new int[9], 0, 1, 0, false, List.of(), List.of(),
				new float[0], 0, 0, 0, -1, 0, new int[2], 1);
	}

	/** Läuft gerade der Sudden Death (Festungskerne verlieren Leben)? */
	public boolean suddenDeath() {
		return suddenDeathSeconds == 0;
	}

	public boolean chieftainDead() {
		return chieftainState == CHIEFTAIN_DEAD;
	}

	public boolean chieftainOnField() {
		return chieftainState == CHIEFTAIN_FIELD;
	}

	public boolean inMatch() {
		return team >= 0;
	}

	/**
	 * Kaufbarer Eintrag im Kriegsmenü. {@code id} z. B. {@code recruit:warrior}, {@code upgrade:armor:warrior},
	 * {@code spell_upgrade:fireball}. {@code status} ist die Ordinalzahl von {@code PurchaseResult}.
	 */
	public record ShopEntry(String id, int level, int maxLevel, int cost, int reputation, int status) {
	}

	/** Abklingzeit in Ticks; {@code total} 0 = nie benutzt. */
	public record Cooldown(String id, int remaining, int total) {
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(phase);
		buf.writeVarInt(team);
		buf.writeVarInt(phaseSeconds);
		buf.writeVarInt(matchSeconds);
		buf.writeVarInt(gold);
		buf.writeVarInt(reputation);
		buf.writeFloat(reputationProgress);
		buf.writeVarInt(population);
		buf.writeVarInt(populationLimit);
		buf.writeVarInt(stance);
		buf.writeVarInt(heroLevel);
		buf.writeFloat(heroProgress);
		buf.writeVarInt(chieftainState);
		buf.writeVarInt(chieftainRespawnSeconds);
		buf.writeFloat(chieftainHealth);
		buf.writeFloat(chieftainMaxHealth);
		buf.writeFloat(chieftainX);
		buf.writeFloat(chieftainZ);
		buf.writeBoolean(chieftainSent);
		for (float[] array : new float[][] {coreHealth, coreMaxHealth, towerHealth, towerMaxHealth}) {
			buf.writeFloat(array[0]);
			buf.writeFloat(array[1]);
		}
		buf.writeVarInt(unitCounts.length);
		for (int count : unitCounts) {
			buf.writeVarInt(count);
		}
		buf.writeVarInt(enemyUnits);
		buf.writeVarInt(enemyHeroLevel);
		buf.writeVarInt(enemyReputation);
		buf.writeBoolean(rallySet);
		buf.writeVarInt(shop.size());
		for (ShopEntry entry : shop) {
			buf.writeUtf(entry.id(), 64);
			buf.writeVarInt(entry.level());
			buf.writeVarInt(entry.maxLevel());
			buf.writeVarInt(entry.cost());
			buf.writeVarInt(entry.reputation());
			buf.writeVarInt(entry.status());
		}
		buf.writeVarInt(cooldowns.size());
		for (Cooldown cooldown : cooldowns) {
			buf.writeUtf(cooldown.id(), 64);
			buf.writeVarInt(cooldown.remaining());
			buf.writeVarInt(cooldown.total());
		}
		buf.writeVarInt(spellRadii.length);
		for (float radius : spellRadii) {
			buf.writeFloat(radius);
		}
		buf.writeVarInt(abilityPoints);
		buf.writeFloat(rageCharge);
		buf.writeVarInt(rageSeconds);
		buf.writeVarInt(suddenDeathSeconds + 1);
		buf.writeFloat(income);
		buf.writeVarInt(roundWins[0]);
		buf.writeVarInt(roundWins[1]);
		buf.writeVarInt(bestOf);
	}

	private static MatchStatePayload read(FriendlyByteBuf buf) {
		int phase = buf.readVarInt();
		int team = buf.readVarInt();
		int phaseSeconds = buf.readVarInt();
		int matchSeconds = buf.readVarInt();
		int gold = buf.readVarInt();
		int reputation = buf.readVarInt();
		float reputationProgress = buf.readFloat();
		int population = buf.readVarInt();
		int populationLimit = buf.readVarInt();
		int stance = buf.readVarInt();
		int heroLevel = buf.readVarInt();
		float heroProgress = buf.readFloat();
		int chieftainState = buf.readVarInt();
		int chieftainRespawnSeconds = buf.readVarInt();
		float chieftainHealth = buf.readFloat();
		float chieftainMaxHealth = buf.readFloat();
		float chieftainX = buf.readFloat();
		float chieftainZ = buf.readFloat();
		boolean chieftainSent = buf.readBoolean();
		float[][] arrays = new float[4][2];
		for (float[] array : arrays) {
			array[0] = buf.readFloat();
			array[1] = buf.readFloat();
		}
		int[] unitCounts = new int[Math.min(16, buf.readVarInt())];
		for (int i = 0; i < unitCounts.length; i++) {
			unitCounts[i] = buf.readVarInt();
		}
		int enemyUnits = buf.readVarInt();
		int enemyHeroLevel = buf.readVarInt();
		int enemyReputation = buf.readVarInt();
		boolean rallySet = buf.readBoolean();
		int shopSize = Math.min(128, buf.readVarInt());
		List<ShopEntry> shop = new ArrayList<>(shopSize);
		for (int i = 0; i < shopSize; i++) {
			shop.add(new ShopEntry(buf.readUtf(64), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
		}
		int cooldownSize = Math.min(32, buf.readVarInt());
		List<Cooldown> cooldowns = new ArrayList<>(cooldownSize);
		for (int i = 0; i < cooldownSize; i++) {
			cooldowns.add(new Cooldown(buf.readUtf(64), buf.readVarInt(), buf.readVarInt()));
		}
		float[] spellRadii = new float[Math.min(16, buf.readVarInt())];
		for (int i = 0; i < spellRadii.length; i++) {
			spellRadii[i] = buf.readFloat();
		}
		int abilityPoints = buf.readVarInt();
		float rageCharge = buf.readFloat();
		int rageSeconds = buf.readVarInt();
		int suddenDeathSeconds = buf.readVarInt() - 1;
		float income = buf.readFloat();
		int[] roundWins = {buf.readVarInt(), buf.readVarInt()};
		int bestOf = buf.readVarInt();
		return new MatchStatePayload(phase, team, phaseSeconds, matchSeconds, gold, reputation, reputationProgress, population,
				populationLimit, stance, heroLevel, heroProgress,
				chieftainState, chieftainRespawnSeconds, chieftainHealth, chieftainMaxHealth, chieftainX, chieftainZ, chieftainSent, arrays[0], arrays[1], arrays[2], arrays[3],
				unitCounts, enemyUnits, enemyHeroLevel, enemyReputation, rallySet, shop, cooldowns,
				spellRadii, abilityPoints, rageCharge, rageSeconds, suddenDeathSeconds, income, roundWins, bestOf);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public ShopEntry shopEntry(String id) {
		for (ShopEntry entry : shop) {
			if (entry.id().equals(id)) {
				return entry;
			}
		}
		return null;
	}

	/** Wirkungsradius eines Zaubers in Blöcken (für den Zielkreis), Ordinalzahl von {@code SpellType}. */
	public float spellRadius(int spell) {
		return spell >= 0 && spell < spellRadii.length ? spellRadii[spell] : 3f;
	}

	public Cooldown cooldown(String id) {
		for (Cooldown cooldown : cooldowns) {
			if (cooldown.id().equals(id)) {
				return cooldown;
			}
		}
		return null;
	}
}
