package io.github.jan1a234.goblinforest.game;

import io.github.jan1a234.goblinforest.config.Balance;
import io.github.jan1a234.goblinforest.hero.AbilityType;
import io.github.jan1a234.goblinforest.hero.HeroProgression;
import io.github.jan1a234.goblinforest.spell.SpellType;
import io.github.jan1a234.goblinforest.unit.UnitType;
import io.github.jan1a234.goblinforest.upgrade.UpgradeKey;
import io.github.jan1a234.goblinforest.upgrade.UpgradeType;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Zustand eines Clans während eines Matches: Ressourcen, Upgrades, Festung, Häuptling, Abklingzeiten und Statistik.
 * Enthält keine Minecraft-Typen, damit alle Regeln (Kosten, Ruf-Sperren, Limits) per Unit-Test prüfbar sind.
 * Zeiten werden in Server-Ticks des Matches gezählt.
 */
public final class TeamState {
	private final TeamColor color;
	private final Balance balance;
	private final HeroProgression heroProgression;

	private double gold;
	private double clanXp;
	private int bonusReputation;
	private double heroXp;
	private int population;
	private Stance stance = Stance.ADVANCE;

	private final Map<UpgradeKey, Integer> upgrades = new HashMap<>();
	private final EnumMap<SpellType, Integer> spellLevels = new EnumMap<>(SpellType.class);
	private final Map<String, Long> cooldownUntil = new HashMap<>();
	private final Map<String, Long> cooldownLength = new HashMap<>();

	private double coreHealth;
	private double towerHealth;

	private final Statistics stats = new Statistics();

	public TeamState(TeamColor color, Balance balance) {
		this.color = color;
		this.balance = balance;
		this.heroProgression = new HeroProgression(balance.hero());
		this.gold = balance.economy().startGold();
		this.coreHealth = balance.stronghold().coreHealth();
		this.towerHealth = balance.stronghold().towerHealth();
		for (SpellType spell : SpellType.values()) {
			spellLevels.put(spell, 1);
		}
	}

	public TeamColor color() {
		return color;
	}

	// --- Gold ---

	public int gold() {
		// Kleiner Puffer gegen Rundungsfehler beim tickweisen Aufsummieren des Einkommens.
		return (int) Math.floor(gold + 1e-6);
	}

	public void addGold(double amount) {
		if (amount > 0) {
			stats.goldEarned += amount;
		}
		gold = Math.max(0, gold + amount);
	}

	public boolean canAfford(int cost) {
		return gold() >= cost;
	}

	private void spend(int cost) {
		gold -= cost;
		stats.goldSpent += cost;
	}

	/** Passives Einkommen für einen Tick. */
	public void tickIncome() {
		gold += balance.economy().passiveGoldPerSecond() / 20.0;
	}

	// --- Erfahrung und Ruf ---

	public double clanXp() {
		return clanXp;
	}

	public void addClanXp(double amount) {
		clanXp += Math.max(0, amount);
	}

	public int reputation() {
		int fromXp = (int) (clanXp / balance.economy().clanXpPerReputation());
		return Math.min(balance.economy().maxReputation(), fromXp + bonusReputation);
	}

	/** Fortschritt zur nächsten Rufstufe zwischen 0 und 1 (1 auf der Höchststufe). */
	public double reputationProgress() {
		if (reputation() >= balance.economy().maxReputation()) {
			return 1.0;
		}
		double per = balance.economy().clanXpPerReputation();
		return (clanXp % per) / per;
	}

	public void addBonusReputation(int amount) {
		bonusReputation += amount;
	}

	// --- Häuptling ---

	public HeroProgression heroProgression() {
		return heroProgression;
	}

	public double heroXp() {
		return heroXp;
	}

	public int heroLevel() {
		return heroProgression.levelForXp(heroXp);
	}

	/** Erfahrung für den Häuptling; liefert true, wenn er dadurch aufgestiegen ist. */
	public boolean addHeroXp(double amount) {
		int before = heroLevel();
		heroXp += Math.max(0, amount);
		return heroLevel() > before;
	}

	// --- Bevölkerung ---

	public int population() {
		return population;
	}

	public void setPopulation(int population) {
		this.population = Math.max(0, population);
	}

	public int populationLimit() {
		Balance.UpgradeTrack huts = balance.upgrade(UpgradeType.HUTS.id());
		return balance.economy().startPopulationLimit() + (int) Math.round(huts.valuePerLevel() * level(UpgradeKey.stronghold(UpgradeType.HUTS)));
	}

	// --- Haltung ---

	public Stance stance() {
		return stance;
	}

	public void setStance(Stance stance) {
		this.stance = stance;
	}

	// --- Rekrutieren ---

	public boolean isUnlocked(UnitType type) {
		return reputation() >= balance.unit(type).unlockReputation();
	}

	/** Prüft, ob eine Gruppe dieses Typs rekrutiert werden kann. {@code livingUnits} zählt alle eigenen Einheiten. */
	public PurchaseResult checkRecruit(UnitType type, int livingUnits) {
		Balance.UnitStats stats = balance.unit(type);
		if (!isUnlocked(type)) {
			return PurchaseResult.REPUTATION_TOO_LOW;
		}
		if (population + stats.population() * stats.groupSize() > populationLimit()) {
			return PurchaseResult.POPULATION_FULL;
		}
		if (livingUnits + stats.groupSize() > balance.match().maxUnitsPerTeam()) {
			return PurchaseResult.UNIT_CAP;
		}
		if (!canAfford(stats.cost())) {
			return PurchaseResult.NOT_ENOUGH_GOLD;
		}
		return PurchaseResult.OK;
	}

	/** Zieht die Kosten ab, wenn {@link #checkRecruit} OK ergibt. Die Bevölkerung zählt das Match über lebende Einheiten. */
	public PurchaseResult recruit(UnitType type, int livingUnits) {
		PurchaseResult result = checkRecruit(type, livingUnits);
		if (result.ok()) {
			Balance.UnitStats stats = balance.unit(type);
			spend(stats.cost());
			population += stats.population() * stats.groupSize();
			stats().unitsRecruited += stats.groupSize();
		}
		return result;
	}

	// --- Upgrades ---

	public int level(UpgradeKey key) {
		return upgrades.getOrDefault(key, 0);
	}

	public int unitUpgrade(UpgradeType type, UnitType unit) {
		return level(UpgradeKey.unit(type, unit));
	}

	public int strongholdUpgrade(UpgradeType type) {
		return level(UpgradeKey.stronghold(type));
	}

	public boolean isApplicable(UpgradeKey key) {
		return key.type().scope() != UpgradeType.Scope.RANGED_UNIT || balance.unit(key.unit()).ranged();
	}

	/** Kosten der nächsten Stufe oder -1, wenn bereits ausgebaut. */
	public int nextUpgradeCost(UpgradeKey key) {
		Balance.UpgradeTrack track = balance.upgrade(key.type().id());
		int current = level(key);
		return current >= track.maxLevel() ? -1 : track.costs().get(current);
	}

	public int nextUpgradeReputation(UpgradeKey key) {
		Balance.UpgradeTrack track = balance.upgrade(key.type().id());
		int current = level(key);
		return current >= track.maxLevel() ? -1 : track.unlockReputation().get(current);
	}

	public PurchaseResult checkUpgrade(UpgradeKey key) {
		if (!isApplicable(key)) {
			return PurchaseResult.NOT_AVAILABLE;
		}
		if (key.type() == UpgradeType.TOWER && towerHealth <= 0) {
			return PurchaseResult.NOT_AVAILABLE;
		}
		int cost = nextUpgradeCost(key);
		if (cost < 0) {
			return PurchaseResult.MAX_LEVEL;
		}
		if (reputation() < nextUpgradeReputation(key)) {
			return PurchaseResult.REPUTATION_TOO_LOW;
		}
		if (!canAfford(cost)) {
			return PurchaseResult.NOT_ENOUGH_GOLD;
		}
		return PurchaseResult.OK;
	}

	public PurchaseResult buyUpgrade(UpgradeKey key) {
		PurchaseResult result = checkUpgrade(key);
		if (result.ok()) {
			spend(nextUpgradeCost(key));
			upgrades.merge(key, 1, Integer::sum);
			if (key.type() == UpgradeType.WALLS) {
				// Neue Mauern verstärken auch den aktuellen Zustand der Festung.
				coreHealth += balance.upgrade(UpgradeType.WALLS.id()).valuePerLevel();
			}
		}
		return result;
	}

	// --- Zauber ---

	public int spellLevel(SpellType spell) {
		return spellLevels.get(spell);
	}

	public boolean isUnlocked(SpellType spell) {
		return reputation() >= balance.spell(spell.id()).unlockReputation();
	}

	public int nextSpellUpgradeCost(SpellType spell) {
		Balance.Spell config = balance.spell(spell.id());
		int level = spellLevel(spell);
		return level >= config.maxLevel() ? -1 : config.upgradeCosts().get(level - 1);
	}

	public int nextSpellUpgradeReputation(SpellType spell) {
		Balance.Spell config = balance.spell(spell.id());
		int level = spellLevel(spell);
		return level >= config.maxLevel() ? -1 : config.upgradeReputation().get(level - 1);
	}

	public PurchaseResult checkSpellUpgrade(SpellType spell) {
		int cost = nextSpellUpgradeCost(spell);
		if (cost < 0) {
			return PurchaseResult.MAX_LEVEL;
		}
		if (!isUnlocked(spell) || reputation() < nextSpellUpgradeReputation(spell)) {
			return PurchaseResult.REPUTATION_TOO_LOW;
		}
		if (!canAfford(cost)) {
			return PurchaseResult.NOT_ENOUGH_GOLD;
		}
		return PurchaseResult.OK;
	}

	public PurchaseResult buySpellUpgrade(SpellType spell) {
		PurchaseResult result = checkSpellUpgrade(spell);
		if (result.ok()) {
			spend(nextSpellUpgradeCost(spell));
			spellLevels.merge(spell, 1, Integer::sum);
		}
		return result;
	}

	/** Prüft Ruf, Gold und Abklingzeit für das Wirken eines Zaubers. */
	public PurchaseResult checkCast(SpellType spell, long now) {
		if (!isUnlocked(spell)) {
			return PurchaseResult.REPUTATION_TOO_LOW;
		}
		if (cooldownRemaining(spell.id(), now) > 0) {
			return PurchaseResult.ON_COOLDOWN;
		}
		if (!canAfford(balance.spell(spell.id()).cost())) {
			return PurchaseResult.NOT_ENOUGH_GOLD;
		}
		return PurchaseResult.OK;
	}

	/** Bezahlt den Zauber und startet die Abklingzeit. Vorher {@link #checkCast} prüfen. */
	public void payCast(SpellType spell, long now) {
		Balance.Spell config = balance.spell(spell.id());
		spend(config.cost());
		startCooldown(spell.id(), now, config.cooldownSeconds() * 20L);
		stats.spellsCast++;
	}

	// --- Fähigkeiten ---

	public PurchaseResult checkAbility(AbilityType ability, long now) {
		return cooldownRemaining(ability.id(), now) > 0 ? PurchaseResult.ON_COOLDOWN : PurchaseResult.OK;
	}

	public void startAbilityCooldown(AbilityType ability, long now) {
		startCooldown(ability.id(), now, balance.ability(ability.id()).cooldownSeconds() * 20L);
	}

	// --- Abklingzeiten ---

	public void startCooldown(String id, long now, long ticks) {
		cooldownUntil.put(id, now + ticks);
		cooldownLength.put(id, ticks);
	}

	public long cooldownRemaining(String id, long now) {
		return Math.max(0, cooldownUntil.getOrDefault(id, 0L) - now);
	}

	public long cooldownLength(String id) {
		return cooldownLength.getOrDefault(id, 0L);
	}

	// --- Festung ---

	public double coreMaxHealth() {
		return balance.stronghold().coreHealth()
				+ balance.upgrade(UpgradeType.WALLS.id()).valuePerLevel() * strongholdUpgrade(UpgradeType.WALLS);
	}

	public double coreHealth() {
		return coreHealth;
	}

	public double towerMaxHealth() {
		return balance.stronghold().towerHealth();
	}

	public double towerHealth() {
		return towerHealth;
	}

	public boolean towerAlive() {
		return towerHealth > 0;
	}

	public boolean coreDestroyed() {
		return coreHealth <= 0;
	}

	/** Schaden am Festungskern; liefert den tatsächlich abgezogenen Wert. */
	public double damageCore(double amount) {
		double before = coreHealth;
		coreHealth = Math.max(0, coreHealth - Math.max(0, amount));
		return before - coreHealth;
	}

	/** Schaden am Turm; liefert true genau dann, wenn der Turm dadurch zerstört wurde. */
	public boolean damageTower(double amount) {
		if (towerHealth <= 0) {
			return false;
		}
		towerHealth = Math.max(0, towerHealth - Math.max(0, amount));
		return towerHealth <= 0;
	}

	public Statistics stats() {
		return stats;
	}

	/** Zahlen für die Statistik am Matchende. */
	public static final class Statistics {
		public int unitsRecruited;
		public int unitKills;
		public int unitsLost;
		public int heroKills;
		public int heroDeaths;
		public int highestUnitLevel = 1;
		public int spellsCast;
		public double goldEarned;
		public double goldSpent;
		public double structureDamage;
	}
}
