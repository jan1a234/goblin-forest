package io.github.jan1a234.goblinforest.game;

import io.github.jan1a234.goblinforest.arena.ArenaLayout;
import io.github.jan1a234.goblinforest.config.Balance;
import io.github.jan1a234.goblinforest.spell.SpellType;
import io.github.jan1a234.goblinforest.unit.GoblinUnit;
import io.github.jan1a234.goblinforest.unit.UnitType;
import io.github.jan1a234.goblinforest.upgrade.UpgradeKey;
import io.github.jan1a234.goblinforest.upgrade.UpgradeType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Random;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Führt einen Clan ohne Spieler: rekrutiert eine gemischte Armee (auf „normal“ und „schwer“ gezielt gegen die
 * Armee des Gegners), kauft Upgrades und Festungsausbauten, wirkt Zauber auf Gruppen und wählt die Haltung.
 *
 * <p>Die KI benutzt dieselben Kaufwege wie ein Spieler ({@link TeamState}-Prüfungen, Kosten, Ruf, Abklingzeiten);
 * sie bekommt nur je nach Schwierigkeit mehr oder weniger passives Gold (siehe {@link AiDifficulty}).
 */
final class AiCommander {
	/** Grundgewichte der Armeezusammensetzung. */
	private static final EnumMap<UnitType, Double> BASE_MIX = new EnumMap<>(UnitType.class);

	static {
		BASE_MIX.put(UnitType.SLAVE, 1.0);
		BASE_MIX.put(UnitType.WARRIOR, 3.0);
		BASE_MIX.put(UnitType.ARCHER, 2.2);
		BASE_MIX.put(UnitType.ASSASSIN, 0.9);
		BASE_MIX.put(UnitType.SHAMAN, 0.9);
		BASE_MIX.put(UnitType.WOLF_RIDER, 1.2);
		BASE_MIX.put(UnitType.TROLL, 1.3);
		BASE_MIX.put(UnitType.CATAPULT, 0.7);
	}

	private final Match match;
	private final TeamColor team;
	private final TeamColor enemy;
	private final AiDifficulty difficulty;
	private final Random random;
	private int cooldown;
	/** Gold, das die KI gerade für ein teureres Ziel (Einheit oder Ausbau) zurücklegt. */
	private int savingFor;

	AiCommander(Match match, TeamColor team, AiDifficulty difficulty) {
		this.match = match;
		this.team = team;
		this.enemy = team.opponent();
		this.difficulty = difficulty;
		this.random = new Random(match.id().getLeastSignificantBits());
		this.cooldown = 20;
	}

	void tick(long now) {
		if (--cooldown > 0) {
			return;
		}
		cooldown = difficulty.decisionTicks() + random.nextInt(difficulty.decisionTicks() / 2 + 1);
		Situation situation = survey();
		chooseStance(situation);
		if (difficulty.spellChance() > 0 && random.nextDouble() < difficulty.spellChance()) {
			castSpells(situation);
		}
		if (difficulty.counters()) {
			buyUpgrades(situation);
		}
		recruit(situation);
	}

	// ---------------------------------------------------------------- Lagebild

	/** Was die KI über das Schlachtfeld weiß (dasselbe, was ein Spieler sehen kann). */
	private record Situation(List<GoblinUnit> own, List<GoblinUnit> enemies, List<ServerPlayer> enemyHeroes,
			double ownStrength, double enemyStrength, int enemiesInOurHalf, EnumMap<UnitType, Integer> ownCounts,
			EnumMap<UnitType, Integer> enemyCounts) {
	}

	private Situation survey() {
		List<GoblinUnit> own = match.unitsOf(team);
		List<GoblinUnit> enemies = match.unitsOf(enemy);
		List<ServerPlayer> heroes = new ArrayList<>();
		for (ServerPlayer player : match.onlineHeroes(enemy)) {
			if (match.isTargetableHero(player)) {
				heroes.add(player);
			}
		}
		EnumMap<UnitType, Integer> ownCounts = counts(own);
		EnumMap<UnitType, Integer> enemyCounts = counts(enemies);
		int inOurHalf = 0;
		for (GoblinUnit unit : enemies) {
			if (ArenaLayout.progress(team, unit.getX()) < 0.5) {
				inOurHalf++;
			}
		}
		double enemyStrength = strength(enemies);
		for (ServerPlayer hero : heroes) {
			enemyStrength += 60 + 25 * match.team(enemy).heroLevel();
		}
		return new Situation(own, enemies, heroes, strength(own), enemyStrength, inOurHalf, ownCounts, enemyCounts);
	}

	private static EnumMap<UnitType, Integer> counts(List<GoblinUnit> units) {
		EnumMap<UnitType, Integer> counts = new EnumMap<>(UnitType.class);
		for (UnitType type : UnitType.values()) {
			counts.put(type, 0);
		}
		for (GoblinUnit unit : units) {
			counts.merge(unit.unitType(), 1, Integer::sum);
		}
		return counts;
	}

	/** Kampfkraft grob als Goldwert, gewichtet mit Level und verbleibendem Leben. */
	private double strength(List<GoblinUnit> units) {
		double total = 0;
		for (GoblinUnit unit : units) {
			Balance.UnitStats stats = match.balance().unit(unit.unitType());
			double perUnit = stats.cost() / (double) Math.max(1, stats.groupSize());
			total += perUnit * (1 + 0.25 * (unit.unitLevel() - 1)) * (unit.getHealth() / Math.max(1, unit.getMaxHealth()));
		}
		return total;
	}

	// ---------------------------------------------------------------- Haltung

	private void chooseStance(Situation s) {
		TeamState state = match.team(team);
		Stance current = state.stance();
		Stance wanted;
		if (difficulty == AiDifficulty.EASY) {
			wanted = Stance.ADVANCE;
		} else if (s.ownStrength() < s.enemyStrength() * 0.5 && s.enemiesInOurHalf() > 0) {
			// Unterlegen und der Feind kommt: hinter die Mauern, dort helfen Turm, Kanone und Heilung.
			wanted = Stance.RETREAT;
		} else if (s.ownStrength() >= s.enemyStrength() * 0.9 || s.own().size() >= 10) {
			wanted = Stance.ADVANCE;
		} else if (current == Stance.RETREAT && s.ownStrength() < s.enemyStrength() * 0.7) {
			wanted = Stance.RETREAT;
		} else {
			wanted = Stance.HOLD;
		}
		if (wanted != current) {
			match.setStance(team, wanted);
		}
	}

	// ---------------------------------------------------------------- Rekrutieren

	private void recruit(Situation s) {
		TeamState state = match.team(team);
		for (int i = 0; i < 4; i++) {
			if (savingFor > 0 && state.gold() < savingFor) {
				return;
			}
			savingFor = 0;
			UnitType choice = chooseUnit(s);
			if (choice == null) {
				return;
			}
			int cost = match.balance().unit(choice).cost();
			if (state.gold() < cost) {
				// Lohnt sich das Warten? Höchstens ein paar Sekunden Einkommen.
				if (cost - state.gold() <= state.incomePerSecond() * 8) {
					savingFor = cost;
				}
				return;
			}
			if (!match.recruit(team, choice).ok()) {
				return;
			}
			s.ownCounts().merge(choice, match.balance().unit(choice).groupSize(), Integer::sum);
		}
	}

	private UnitType chooseUnit(Situation s) {
		TeamState state = match.team(team);
		int living = match.unitsOf(team).size();
		EnumMap<UnitType, Double> weights = new EnumMap<>(UnitType.class);
		double total = 0;
		for (UnitType type : UnitType.values()) {
			PurchaseResult check = state.checkRecruit(type, living);
			if (check != PurchaseResult.OK && check != PurchaseResult.NOT_ENOUGH_GOLD) {
				continue;
			}
			double weight = BASE_MIX.get(type) * (difficulty.counters() ? counterFactor(type, s) : 1.0);
			if (weight <= 0) {
				continue;
			}
			weights.put(type, weight);
			total += weight;
		}
		if (weights.isEmpty()) {
			return null;
		}
		if (!difficulty.counters()) {
			// Leicht: einfach zufällig nach Grundmischung.
			double roll = random.nextDouble() * total;
			for (var entry : weights.entrySet()) {
				roll -= entry.getValue();
				if (roll <= 0) {
					return entry.getKey();
				}
			}
			return weights.keySet().iterator().next();
		}
		// Normal/Schwer: der Typ, der in der eigenen Armee am weitesten unter seinem Soll-Anteil liegt.
		int ownTotal = 0;
		for (int count : s.ownCounts().values()) {
			ownTotal += count;
		}
		UnitType best = null;
		double bestDeficit = Double.NEGATIVE_INFINITY;
		for (var entry : weights.entrySet()) {
			double wanted = entry.getValue() / total;
			double have = ownTotal == 0 ? 0 : s.ownCounts().get(entry.getKey()) / (double) ownTotal;
			double deficit = wanted - have + random.nextDouble() * 0.05;
			if (deficit > bestDeficit) {
				bestDeficit = deficit;
				best = entry.getKey();
			}
		}
		return best;
	}

	/** Gegenmittel zur gegnerischen Armee. */
	private double counterFactor(UnitType type, Situation s) {
		EnumMap<UnitType, Integer> e = s.enemyCounts();
		int enemyRanged = e.get(UnitType.ARCHER) + e.get(UnitType.SHAMAN) + e.get(UnitType.CATAPULT);
		int enemyMelee = e.get(UnitType.SLAVE) + e.get(UnitType.WARRIOR) + e.get(UnitType.ASSASSIN) + e.get(UnitType.WOLF_RIDER);
		int enemyHeavy = e.get(UnitType.TROLL) + e.get(UnitType.WARRIOR) / 2;
		int army = s.own().size();
		return switch (type) {
			case WOLF_RIDER, ASSASSIN -> enemyRanged >= 3 ? 1.8 : 1.0;
			case ARCHER -> enemyHeavy >= 2 || enemyMelee >= 6 ? 1.5 : 1.0;
			case TROLL -> enemyMelee >= 6 ? 1.4 : 1.0;
			case SHAMAN -> army >= 6 && s.ownCounts().get(UnitType.SHAMAN) < 3 ? 1.2 : army < 4 ? 0.3 : 0.6;
			case CATAPULT -> army >= 6 && s.ownCounts().get(UnitType.CATAPULT) < 2 ? 1.0 : 0.0;
			case SLAVE -> s.enemiesInOurHalf() > 4 ? 1.6 : 0.8;
			default -> 1.0;
		};
	}

	// ---------------------------------------------------------------- Upgrades und Ausbau

	private void buyUpgrades(Situation s) {
		TeamState state = match.team(team);
		UpgradeKey best = null;
		double bestScore = 0;
		for (UpgradeKey key : UpgradeKey.all(type -> match.balance().unit(type).ranged())) {
			PurchaseResult check = state.checkUpgrade(key);
			if (check != PurchaseResult.OK && check != PurchaseResult.NOT_ENOUGH_GOLD) {
				continue;
			}
			double score = upgradeScore(key, s, state);
			if (score > bestScore) {
				bestScore = score;
				best = key;
			}
		}
		// Mit vollem Beutel lohnen sich auch kleinere Verbesserungen.
		double threshold = state.gold() > 500 ? 0.3 : 1.0;
		if (best == null || bestScore < threshold) {
			return;
		}
		int cost = state.nextUpgradeCost(best);
		// Nicht die ganze Armee für Upgrades verhungern lassen: kleine Armee braucht zuerst Soldaten.
		boolean urgent = bestScore >= 3.0;
		if (!urgent && s.own().size() < 5) {
			return;
		}
		if (state.gold() >= cost) {
			match.buyUpgradeFor(team, best);
			savingFor = 0;
		} else if (urgent || cost - state.gold() <= state.incomePerSecond() * 10) {
			savingFor = Math.max(savingFor, cost);
		}
	}

	private double upgradeScore(UpgradeKey key, Situation s, TeamState state) {
		int level = state.level(key);
		boolean hard = difficulty == AiDifficulty.HARD;
		double seconds = match.battleTicks() / 20.0;
		switch (key.type()) {
			case HUTS -> {
				return state.population() >= state.populationLimit() - 3 ? 3.5 : 0;
			}
			case GOLDMINE -> {
				int maxLevel = hard ? 3 : 2;
				return level < maxLevel && seconds > 40 + level * 90 ? 2.0 : 0;
			}
			case WALLS -> {
				return state.coreHealth() < state.coreMaxHealth() * 0.6 ? 2.5 : 0;
			}
			case TOWER -> {
				return state.towerAlive() && s.enemiesInOurHalf() >= 4 && level < 2 ? 1.6 : 0;
			}
			case CANNON -> {
				return s.enemiesInOurHalf() >= 3 || level > 0 && level < 3 && seconds > 300 ? 2.2 : 0;
			}
			case TRAINING -> {
				return hard && s.own().size() >= 6 ? 1.5 : 0;
			}
			case ENDURANCE -> {
				return s.own().size() >= 8 && level < 2 ? 1.3 : 0;
			}
			case ARMOR, ATTACK, RANGE -> {
				int count = s.ownCounts().get(key.unit());
				if (count < 3) {
					return 0;
				}
				double value = count / 3.0 * (key.type() == UpgradeType.ATTACK ? 1.0 : 0.9);
				return value / (1 + level * 0.6);
			}
			default -> {
				return 0;
			}
		}
	}

	// ---------------------------------------------------------------- Zauber

	private void castSpells(Situation s) {
		TeamState state = match.team(team);
		int reserve = difficulty == AiDifficulty.HARD ? 20 : 50;
		for (SpellType spell : SpellType.values()) {
			if (state.checkCast(spell, match.currentTick()) != PurchaseResult.OK) {
				continue;
			}
			Balance.Spell config = match.balance().spell(spell.id());
			if (state.gold() < config.cost() + reserve) {
				continue;
			}
			Vec3 target = spell == SpellType.HEALING ? healingTarget(s, config.radius()) : attackTarget(spell, s, config.radius());
			if (target != null) {
				match.castSpellFor(team, spell, target);
				return;
			}
		}
	}

	/** Bester Punkt für einen Angriffszauber, oder null, wenn sich der Zauber gerade nicht lohnt. */
	private Vec3 attackTarget(SpellType spell, Situation s, double radius) {
		List<LivingEntity> targets = new ArrayList<>(s.enemies());
		targets.addAll(s.enemyHeroes());
		LivingEntity bestCenter = null;
		double bestValue = 0;
		for (LivingEntity center : targets) {
			double value = 0;
			for (LivingEntity other : targets) {
				if (other.distanceToSqr(center) <= radius * radius) {
					value += other instanceof ServerPlayer ? 2.5 : 1.0;
				}
			}
			if (value > bestValue) {
				bestValue = value;
				bestCenter = center;
			}
		}
		if (bestCenter == null) {
			return null;
		}
		boolean nearHome = ArenaLayout.progress(team, bestCenter.getX()) < 0.45;
		double needed = switch (spell) {
			case FIREBALL -> 3;
			case ROOTS -> nearHome ? 3 : 5;
			case LIGHTNING -> 4;
			case METEOR -> 6;
			default -> Double.MAX_VALUE;
		};
		if (difficulty == AiDifficulty.HARD) {
			needed -= 1;
		}
		return bestValue >= needed ? bestCenter.position() : null;
	}

	/** Eigene Gruppe mit dem meisten fehlenden Leben, wenn sich Heilen lohnt. */
	private Vec3 healingTarget(Situation s, double radius) {
		GoblinUnit bestCenter = null;
		double bestMissing = 0;
		for (GoblinUnit center : s.own()) {
			double missing = 0;
			for (GoblinUnit other : s.own()) {
				if (other.distanceToSqr(center) <= radius * radius) {
					missing += other.getMaxHealth() - other.getHealth();
				}
			}
			if (missing > bestMissing) {
				bestMissing = missing;
				bestCenter = center;
			}
		}
		return bestCenter != null && bestMissing >= 150 ? bestCenter.position() : null;
	}
}
