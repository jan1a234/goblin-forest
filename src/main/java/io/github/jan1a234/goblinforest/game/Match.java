package io.github.jan1a234.goblinforest.game;

import io.github.jan1a234.goblinforest.GoblinForest;
import io.github.jan1a234.goblinforest.arena.ArenaBlueprint;
import io.github.jan1a234.goblinforest.arena.ArenaBuilder;
import io.github.jan1a234.goblinforest.arena.ArenaLayout;
import io.github.jan1a234.goblinforest.config.Balance;
import io.github.jan1a234.goblinforest.hero.AbilityType;
import io.github.jan1a234.goblinforest.net.MatchStatePayload;
import io.github.jan1a234.goblinforest.registry.ModEntities;
import io.github.jan1a234.goblinforest.spell.SpellType;
import io.github.jan1a234.goblinforest.registry.ModSounds;
import io.github.jan1a234.goblinforest.unit.GoblinUnit;
import io.github.jan1a234.goblinforest.unit.Knockback;
import io.github.jan1a234.goblinforest.unit.UnitCombat;
import io.github.jan1a234.goblinforest.unit.UnitType;
import io.github.jan1a234.goblinforest.upgrade.UpgradeKey;
import io.github.jan1a234.goblinforest.upgrade.UpgradeType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

/**
 * Ein laufendes Match: Arena-Aufbau, Countdown, Kampf und Ende (DESIGN.md Abschnitt 3).
 * Hält beide Clans ({@link TeamState}), die Häuptlinge, alle Einheiten und die Festungen.
 * Alles hier läuft ausschließlich auf dem Server-Thread.
 */
public final class Match {
	/** Ab so vielen Ticks ohne Spieler online verliert ein Clan kampflos. */
	private static final int FORFEIT_TICKS = 20 * 120;
	/** Höhe über dem Boden, auf der die (unsichtbare) Spielfigur über dem Blickpunkt der Draufsicht schwebt. */
	private static final int COMMANDER_HEIGHT = 18;

	/**
	 * Feldherr eines Clans (ein Spieler). Er kämpft nicht selbst: Seine Spielfigur schwebt unsichtbar im Zuschauermodus
	 * über dem Schlachtfeld, gespielt wird nur in der Draufsicht (DESIGN.md Abschnitt 10).
	 */
	static final class Hero {
		final UUID uuid;
		final String name;
		final TeamColor team;
		boolean prepared;
		long lastAttackWarning = -1000;
		/** Blickpunkt der Draufsicht auf dem Boden; dorthin wird die Spielfigur mitgeführt. */
		Vec3 focus;

		Hero(UUID uuid, String name, TeamColor team) {
			this.uuid = uuid;
			this.name = name;
			this.team = team;
		}
	}

	/** Der Häuptling eines Clans: eine Heldeneinheit, die der Spieler aufs Schlachtfeld schickt (DESIGN.md Abschnitt 6). */
	static final class Chieftain {
		/** Die Einheit auf dem Feld; null, solange er tot ist oder die Runde noch nicht begonnen hat. */
		GoblinUnit unit;
		/** Auf dem Schlachtfeld (folgt der Haltung der Armee) oder in der Festung (bewacht das Tor)? */
		boolean deployed;
		/** Wurde er in dieser Runde schon einmal losgeschickt? Bis dahin zeigt das HUD einen Hinweis. */
		boolean sentOnce;
		/** Ticks bis zur Wiederbelebung; 0, solange er lebt. */
		int respawnTicks;
		boolean raging;

		boolean alive() {
			return unit != null && unit.isAlive() && !unit.isRemoved();
		}
	}

	private record Scheduled(long at, Runnable task) {
	}

	private final UUID id = UUID.randomUUID();
	private final MinecraftServer server;
	private final Balance balance;
	private final UnitCombat combat;
	private final Bounty bounty;
	private final boolean practice;
	private final MatchOptions options;
	/** Clan, den die KI führt (null ohne KI). */
	private final TeamColor aiTeam;
	private final AiCommander ai;
	/** Die Runde ist vorbei, die Serie geht weiter: Spieler bleiben in der Arena. */
	private boolean nextRound;
	private final EnumMap<TeamColor, TeamState> teams = new EnumMap<>(TeamColor.class);
	private final EnumMap<TeamColor, List<ArenaLayout.Point>> waypoints = new EnumMap<>(TeamColor.class);
	private final EnumMap<TeamColor, Vec3> rallyPoints = new EnumMap<>(TeamColor.class);
	private final EnumMap<TeamColor, Integer> coreStages = new EnumMap<>(TeamColor.class);
	private final EnumMap<TeamColor, Integer> towerCooldowns = new EnumMap<>(TeamColor.class);
	private final EnumMap<TeamColor, Integer> cannonCooldowns = new EnumMap<>(TeamColor.class);
	private final EnumMap<TeamColor, Integer> lastReputation = new EnumMap<>(TeamColor.class);
	private final EnumMap<TeamColor, Integer> offlineTicks = new EnumMap<>(TeamColor.class);
	private final EnumMap<TeamColor, PlayerTeam> scoreboardTeams = new EnumMap<>(TeamColor.class);
	private final Map<UUID, Hero> heroes = new LinkedHashMap<>();
	private final EnumMap<TeamColor, Chieftain> chieftains = new EnumMap<>(TeamColor.class);
	private final Set<GoblinUnit> units = new HashSet<>();
	private final List<Scheduled> scheduled = new ArrayList<>();
	private final ServerLevel arena;
	private ArenaBuilder builder;
	private MatchPhase phase = MatchPhase.SETUP;
	private long tick;
	private long battleTicks;
	private int phaseTicks;
	private TeamColor winner;
	private boolean finished;

	Match(MinecraftServer server, ServerLevel arena, Map<UUID, TeamColor> players, Map<UUID, String> names, boolean practice) {
		this(server, arena, players, names, MatchOptions.single(practice));
	}

	Match(MinecraftServer server, ServerLevel arena, Map<UUID, TeamColor> players, Map<UUID, String> names, MatchOptions options) {
		this.server = server;
		this.arena = arena;
		this.balance = GoblinForest.balance();
		this.combat = new UnitCombat(balance);
		this.bounty = new Bounty(balance.economy());
		this.options = options;
		this.practice = options.practice() || options.ai() != null;
		for (TeamColor team : TeamColor.values()) {
			teams.put(team, new TeamState(team, balance));
			waypoints.put(team, ArenaLayout.laneWaypoints(team));
			coreStages.put(team, 0);
			towerCooldowns.put(team, 0);
			cannonCooldowns.put(team, 0);
			lastReputation.put(team, 0);
			offlineTicks.put(team, 0);
			chieftains.put(team, new Chieftain());
		}
		players.forEach((uuid, team) -> heroes.put(uuid, new Hero(uuid, names.getOrDefault(uuid, "?"), team)));
		if (options.ai() != null) {
			aiTeam = players.containsValue(TeamColor.GREEN) && !players.containsValue(TeamColor.RED) ? TeamColor.RED : TeamColor.GREEN;
			TeamState state = teams.get(aiTeam);
			state.setIncomeMultiplier(options.ai().incomeMultiplier());
			state.addGold(options.ai().bonusStartGold());
			ai = new AiCommander(this, aiTeam, options.ai());
		} else {
			aiTeam = null;
			ai = null;
		}
	}

	// ================================================================ Abfragen für Einheiten und Ereignisse

	public UUID id() {
		return id;
	}

	public Balance balance() {
		return balance;
	}

	public UnitCombat combat() {
		return combat;
	}

	public MatchPhase phase() {
		return phase;
	}

	public boolean isBattle() {
		return phase == MatchPhase.BATTLE;
	}

	public boolean finished() {
		return finished;
	}

	public boolean practice() {
		return practice;
	}

	public MatchOptions options() {
		return options;
	}

	/** Clan der KI oder null. */
	public TeamColor aiTeam() {
		return aiTeam;
	}

	/** Diese Runde ist vorbei und die Best-of-Serie geht mit einer neuen Runde weiter. */
	public boolean continuesSeries() {
		return nextRound;
	}

	/** Spieler und ihre Clans, für die nächste Runde einer Serie. */
	Map<UUID, TeamColor> assignment() {
		Map<UUID, TeamColor> map = new LinkedHashMap<>();
		heroes.forEach((uuid, hero) -> map.put(uuid, hero.team));
		return map;
	}

	Map<UUID, String> names() {
		Map<UUID, String> map = new LinkedHashMap<>();
		heroes.forEach((uuid, hero) -> map.put(uuid, hero.name));
		return map;
	}

	public ServerLevel arena() {
		return arena;
	}

	public TeamState team(TeamColor color) {
		return teams.get(color);
	}

	public boolean owns(GoblinUnit unit) {
		return id.equals(unit.matchId());
	}

	public List<ArenaLayout.Point> waypoints(TeamColor team) {
		return waypoints.get(team);
	}

	public Vec3 rallyPoint(TeamColor team) {
		return rallyPoints.get(team);
	}

	public boolean isMember(UUID uuid) {
		return heroes.containsKey(uuid);
	}

	public TeamColor teamOf(UUID uuid) {
		Hero hero = heroes.get(uuid);
		return hero == null ? null : hero.team;
	}

	/** Clan eines Entities: Einheit, Häuptling oder (für Pfeile) der Schütze. Null für alles Neutrale. */
	public TeamColor teamOf(Entity entity) {
		if (entity == null) {
			return null;
		}
		if (entity instanceof GoblinUnit unit) {
			return owns(unit) ? unit.team() : null;
		}
		if (entity instanceof ServerPlayer player) {
			return teamOf(player.getUUID());
		}
		if (entity instanceof net.minecraft.world.entity.projectile.Projectile projectile) {
			return teamOf(projectile.getOwner());
		}
		return null;
	}

	/** Der lebende Häuptling eines Clans oder null (tot, oder die Runde hat noch nicht begonnen). */
	public GoblinUnit chieftain(TeamColor team) {
		Chieftain chieftain = chieftains.get(team);
		return chieftain.alive() ? chieftain.unit : null;
	}

	/** Ist der Häuptling auf dem Schlachtfeld (sonst bewacht er die eigene Festung)? */
	public boolean chieftainDeployed(TeamColor team) {
		return chieftains.get(team).deployed;
	}

	/** Sekunden bis zur Wiederbelebung des Häuptlings; 0, solange er lebt. */
	public int chieftainRespawnSeconds(TeamColor team) {
		return (chieftains.get(team).respawnTicks + 19) / 20;
	}

	/** Ist diese Einheit der Häuptling ihres Clans in diesem Match? */
	public boolean isChieftain(GoblinUnit unit) {
		return unit != null && owns(unit) && chieftains.get(unit.team()).unit == unit;
	}

	public boolean structureAlive(TeamColor team, Structure structure) {
		TeamState state = teams.get(team);
		return structure == Structure.TOWER ? state.towerAlive() : !state.coreDestroyed();
	}

	public int livingUnits(TeamColor team) {
		int count = 0;
		for (GoblinUnit unit : units) {
			if (unit.isAlive() && unit.team() == team) {
				count++;
			}
		}
		return count;
	}

	private List<ServerPlayer> onlineHeroes() {
		List<ServerPlayer> list = new ArrayList<>();
		for (Hero hero : heroes.values()) {
			ServerPlayer player = server.getPlayerList().getPlayer(hero.uuid);
			if (player != null) {
				list.add(player);
			}
		}
		return list;
	}

	List<ServerPlayer> onlineHeroes(TeamColor team) {
		List<ServerPlayer> list = new ArrayList<>();
		for (Hero hero : heroes.values()) {
			if (hero.team == team) {
				ServerPlayer player = server.getPlayerList().getPlayer(hero.uuid);
				if (player != null) {
					list.add(player);
				}
			}
		}
		return list;
	}

	// ================================================================ Ablauf

	void tick() {
		tick++;
		switch (phase) {
			case SETUP -> tickSetup();
			case COUNTDOWN -> tickCountdown();
			case BATTLE -> tickBattle();
			case ENDED -> tickEnded();
			default -> {
			}
		}
		runScheduled();
		if (!finished && tick % 10 == 0) {
			syncAll();
		}
	}

	private void tickSetup() {
		if (builder == null) {
			forceChunks(true);
			clearArenaEntities();
			createScoreboardTeams();
			builder = new ArenaBuilder(arena);
			broadcast(Component.translatable("message.goblinforest.building").withStyle(ChatFormatting.GRAY));
		}
		boolean done = builder.tick();
		if (tick % 10 == 0) {
			int percent = (int) Math.round(builder.progress() * 100);
			for (ServerPlayer player : onlineHeroes()) {
				player.sendOverlayMessage(Component.translatable("message.goblinforest.building_progress", percent).withStyle(ChatFormatting.GOLD));
			}
		}
		if (done) {
			for (ServerPlayer player : onlineHeroes()) {
				prepareHero(player, heroes.get(player.getUUID()));
			}
			for (TeamColor team : TeamColor.values()) {
				spawnChieftain(team);
			}
			phase = MatchPhase.COUNTDOWN;
			phaseTicks = balance.match().countdownSeconds() * 20;
		}
	}

	private void tickCountdown() {
		if (phaseTicks % 20 == 0) {
			int seconds = phaseTicks / 20;
			for (ServerPlayer player : onlineHeroes()) {
				if (seconds > 0) {
					title(player, Component.literal(String.valueOf(seconds)).withStyle(seconds <= 3 ? ChatFormatting.RED : ChatFormatting.GOLD),
							Component.translatable("title.goblinforest.countdown"), 0, 25, 0);
					playTo(player, SoundEvents.NOTE_BLOCK_HAT.value(), 1.0f, seconds <= 3 ? 1.5f : 1.0f);
				}
			}
		}
		if (phaseTicks <= 0) {
			phase = MatchPhase.BATTLE;
			for (ServerPlayer player : onlineHeroes()) {
				title(player, Component.translatable("title.goblinforest.fight").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
						Component.translatable("title.goblinforest.fight_sub"), 0, 30, 15);
				playTo(player, ModSounds.WAR_HORN, 1.0f, 1.0f);
			}
			return;
		}
		phaseTicks--;
	}

	private void tickBattle() {
		battleTicks++;
		for (TeamState state : teams.values()) {
			state.tickIncome();
		}
		tickTowers();
		tickCannons();
		tickChieftains();
		tickCommanders();
		if (ai != null) {
			ai.tick(tick);
		}
		if (tick % 20 == 0) {
			recountPopulation();
			checkReputation();
			drawRallyMarkers();
			checkForfeit();
			emitStructureSmoke();
			emitGoldmineSparkles();
			tickSuddenDeath();
		}
	}

	/** Gewonnene Runden eines Clans in der laufenden Best-of-Serie (0 ohne Serie). */
	public int roundWins(TeamColor team) {
		return options.series() == null ? 0 : options.series().wins(team);
	}

	public int bestOf() {
		return options.bestOf();
	}

	/** Ist der Sudden Death in diesem Match eingeschaltet ({@code /gf start sd})? Ohne ihn gibt es kein Zeitlimit. */
	public boolean suddenDeathEnabled() {
		return options.suddenDeath() && balance.match().suddenDeathMinutes() > 0;
	}

	/** Sekunden bis zum Sudden Death; 0, sobald er läuft; -1, wenn er abgeschaltet ist. */
	public long secondsUntilSuddenDeath() {
		if (!suddenDeathEnabled()) {
			return -1;
		}
		return Math.max(0, (balance.match().suddenDeathTicks() - battleTicks + 19) / 20);
	}

	private void tickSuddenDeath() {
		if (!suddenDeathEnabled()) {
			return;
		}
		long remaining = balance.match().suddenDeathTicks() - battleTicks;
		if (remaining == 60 * 20 || remaining == 10 * 20) {
			for (ServerPlayer player : onlineHeroes()) {
				player.sendSystemMessage(Component.translatable("message.goblinforest.sudden_death_warning", remaining / 20).withStyle(ChatFormatting.RED));
				playTo(player, SoundEvents.BELL_BLOCK, 1.0f, 0.6f);
			}
		}
		if (remaining > 0) {
			return;
		}
		if (remaining == 0) {
			for (ServerPlayer player : onlineHeroes()) {
				title(player, Component.translatable("title.goblinforest.sudden_death").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
						Component.translatable("title.goblinforest.sudden_death_sub"), 10, 50, 15);
				playTo(player, SoundEvents.WITHER_SPAWN, 0.8f, 0.7f);
			}
		}
		double percent = balance.match().suddenDeathPercentPerSecond();
		for (TeamColor team : TeamColor.values()) {
			if (phase != MatchPhase.BATTLE) {
				return;
			}
			TeamState state = teams.get(team);
			ArenaLayout.Point center = ArenaLayout.coreBox(team).center();
			arena.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, center.x(), center.y() + 2, center.z(), 8, 2.5, 1.5, 2.5, 0.01);
			damageStructure(team, Structure.CORE, state.coreMaxHealth() * percent, null, null);
		}
	}

	private void tickEnded() {
		if (--phaseTicks <= 0) {
			if (nextRound) {
				finishRound();
			} else {
				finish(null);
			}
			return;
		}
		if (nextRound && phaseTicks % 20 == 0 && phaseTicks <= 10 * 20) {
			for (ServerPlayer player : onlineHeroes()) {
				player.sendOverlayMessage(Component.translatable("message.goblinforest.next_round_in",
						options.series().round(), phaseTicks / 20).withStyle(ChatFormatting.GOLD));
			}
		}
		if (winner != null && phaseTicks % 15 == 0 && phaseTicks > balance.match().endDelaySeconds() * 20 - 120) {
			ArenaLayout.Point core = ArenaLayout.coreBox(winner.opponent()).center();
			double ox = (arena.getRandom().nextDouble() - 0.5) * 8;
			double oz = (arena.getRandom().nextDouble() - 0.5) * 8;
			arena.sendParticles(ParticleTypes.EXPLOSION_EMITTER, core.x() + ox, core.y() + 2, core.z() + oz, 1, 0, 0, 0, 0);
			arena.playSound(null, core.x(), core.y(), core.z(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 2.0f, 0.8f);
		}
		if (winner != null && phaseTicks % 10 == 0) {
			ArenaLayout.Point core = ArenaLayout.coreBox(winner).center();
			arena.sendParticles(ParticleTypes.FIREWORK, core.x(), core.y() + 6, core.z(), 30, 2, 2, 2, 0.15);
		}
	}

	private void runScheduled() {
		if (scheduled.isEmpty()) {
			return;
		}
		List<Scheduled> due = new ArrayList<>();
		Iterator<Scheduled> it = scheduled.iterator();
		while (it.hasNext()) {
			Scheduled task = it.next();
			if (task.at() <= tick) {
				due.add(task);
				it.remove();
			}
		}
		for (Scheduled task : due) {
			task.task().run();
		}
	}

	/** Führt eine Aufgabe nach {@code delayTicks} Ticks aus (Flugbahnen, verzögerte Explosionen). */
	public void schedule(int delayTicks, Runnable task) {
		scheduled.add(new Scheduled(tick + Math.max(1, delayTicks), task));
	}

	// ================================================================ Arena

	private void forceChunks(boolean force) {
		for (int cx = ArenaLayout.minChunkX(); cx <= ArenaLayout.maxChunkX(); cx++) {
			for (int cz = ArenaLayout.minChunkZ(); cz <= ArenaLayout.maxChunkZ(); cz++) {
				arena.setChunkForced(cx, cz, force);
			}
		}
	}

	private void clearArenaEntities() {
		AABB bounds = new AABB(-ArenaLayout.HALF_LENGTH - 16, ArenaBlueprint.MIN_Y - 16, -ArenaLayout.HALF_WIDTH - 16,
				ArenaLayout.HALF_LENGTH + 16, ArenaBlueprint.MAX_Y + 64, ArenaLayout.HALF_WIDTH + 16);
		for (Entity entity : arena.getEntities((Entity) null, bounds, e -> !(e instanceof Player))) {
			entity.discard();
		}
	}

	private void createScoreboardTeams() {
		Scoreboard scoreboard = server.getScoreboard();
		for (TeamColor team : TeamColor.values()) {
			String name = "gf_" + team.id();
			PlayerTeam existing = scoreboard.getPlayerTeam(name);
			if (existing != null) {
				scoreboard.removePlayerTeam(existing);
			}
			PlayerTeam playerTeam = scoreboard.addPlayerTeam(name);
			playerTeam.setDisplayName(Component.translatable(team.translationKey()));
			playerTeam.setColor(Optional.of(team == TeamColor.RED ? net.minecraft.world.scores.TeamColor.RED : net.minecraft.world.scores.TeamColor.GREEN));
			playerTeam.setAllowFriendlyFire(false);
			playerTeam.setSeeFriendlyInvisibles(true);
			playerTeam.setNameTagVisibility(Team.Visibility.ALWAYS);
			playerTeam.setDeathMessageVisibility(Team.Visibility.NEVER);
			playerTeam.setCollisionRule(Team.CollisionRule.PUSH_OTHER_TEAMS);
			scoreboardTeams.put(team, playerTeam);
		}
	}

	private void removeScoreboardTeams() {
		Scoreboard scoreboard = server.getScoreboard();
		for (PlayerTeam playerTeam : scoreboardTeams.values()) {
			if (scoreboard.getPlayerTeam(playerTeam.getName()) != null) {
				scoreboard.removePlayerTeam(playerTeam);
			}
		}
		scoreboardTeams.clear();
	}

	private void joinScoreboardTeam(Entity entity, TeamColor team) {
		PlayerTeam playerTeam = scoreboardTeams.get(team);
		if (playerTeam != null) {
			server.getScoreboard().addPlayerToTeam(entity.getScoreboardName(), playerTeam);
		}
	}

	// ================================================================ Feldherren (Spieler)

	/**
	 * Stellt einen Spieler als Feldherrn auf: Inventar gesichert, Zuschauermodus (unsichtbar, unverwundbar, ohne Kollision),
	 * Spielfigur über dem eigenen Tor. Der Client schaltet dazu fest in die Draufsicht.
	 */
	private void prepareHero(ServerPlayer player, Hero hero) {
		if (hero == null) {
			return;
		}
		if (!PlayerBackup.exists(server, player.getUUID())) {
			PlayerBackup.save(player);
		}
		player.closeContainer();
		player.removeAllEffects();
		player.getInventory().clearContent();
		player.setGameMode(GameType.SPECTATOR);
		player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, -1, 0, true, false, false));
		joinScoreboardTeam(player, hero.team);
		if (hero.focus == null) {
			hero.focus = vec(ArenaLayout.overviewStart(hero.team));
		}
		moveCommander(player, hero, true);
		hero.prepared = true;
		player.sendSystemMessage(Component.translatable("message.goblinforest.welcome",
				Component.translatable(hero.team.translationKey()).withColor(hero.team.rgb())).withStyle(ChatFormatting.GOLD));
		player.sendSystemMessage(Component.translatable("message.goblinforest.controls").withStyle(ChatFormatting.GRAY));
	}

	/**
	 * Führt die unsichtbare Spielfigur über dem Blickpunkt der Draufsicht mit, damit der Server Einheiten und Chunks
	 * rund um den gerade betrachteten Teil des Schlachtfelds an den Client schickt.
	 */
	private void moveCommander(ServerPlayer player, Hero hero, boolean force) {
		Vec3 target = new Vec3(hero.focus.x, ArenaLayout.GROUND_Y + COMMANDER_HEIGHT, hero.focus.z);
		if (!force && player.level() == arena && player.position().distanceToSqr(target) < 16) {
			return;
		}
		player.teleport(new TeleportTransition(arena, target, Vec3.ZERO, ArenaLayout.facingYaw(hero.team), 60, TeleportTransition.DO_NOTHING));
	}

	/** Hält die Feldherren im Zuschauermodus und in der Arena (z. B. nach /gamemode oder einem Portal). */
	private void tickCommanders() {
		if (tick % 20 != 0) {
			return;
		}
		for (ServerPlayer player : onlineHeroes()) {
			Hero hero = heroes.get(player.getUUID());
			if (!hero.prepared) {
				continue;
			}
			if (!player.isSpectator()) {
				player.setGameMode(GameType.SPECTATOR);
			}
			if (player.level() != arena) {
				moveCommander(player, hero, true);
			}
		}
	}

	private void restoreHero(ServerPlayer player) {
		player.removeAllEffects();
		server.getScoreboard().removePlayerFromTeam(player.getScoreboardName());
		player.closeContainer();
		PlayerBackup.restore(player);
		ServerPlayNetworking.send(player, MatchStatePayload.none());
	}

	/** Feldherren sterben nicht (Zuschauermodus); nur zur Sicherheit, falls doch Schaden durchkommt (z. B. /kill). */
	public boolean onHeroDeath(ServerPlayer player, DamageSource source) {
		player.setHealth(player.getMaxHealth());
		player.clearFire();
		return false;
	}

	/** Feldherren nehmen im Match keinen Schaden. */
	public boolean allowHeroDamage(ServerPlayer player, DamageSource source) {
		return !heroes.containsKey(player.getUUID());
	}

	public void onPlayerJoin(ServerPlayer player) {
		Hero hero = heroes.get(player.getUUID());
		if (hero == null || phase == MatchPhase.SETUP) {
			return;
		}
		if (phase == MatchPhase.ENDED) {
			restoreHero(player);
			return;
		}
		prepareHero(player, hero);
	}

	// ================================================================ Häuptlinge

	/** Stellt den Häuptling eines Clans an seinem Posten in der Festung auf (Rundenbeginn und Wiederbelebung). */
	private void spawnChieftain(TeamColor team) {
		Chieftain chieftain = chieftains.get(team);
		if (chieftain.alive()) {
			return;
		}
		GoblinUnit unit = ModEntities.GOBLIN.create(arena, EntitySpawnReason.MOB_SUMMONED);
		if (unit == null) {
			return;
		}
		ArenaLayout.Point post = ArenaLayout.heroSpawn(team);
		unit.snapTo(post.x(), post.y(), post.z(), ArenaLayout.facingYaw(team), 0);
		unit.setYHeadRot(ArenaLayout.facingYaw(team));
		chieftain.unit = unit;
		chieftain.respawnTicks = 0;
		chieftain.raging = false;
		unit.setup(UnitType.CHIEFTAIN, team, this);
		if (!arena.addFreshEntity(unit)) {
			chieftain.unit = null;
			return;
		}
		joinScoreboardTeam(unit, team);
		arena.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, post.x(), post.y() + 1, post.z(), 30, 0.4, 0.8, 0.4, 0.3);
	}

	/**
	 * Schickt den Häuptling aufs Schlachtfeld oder ruft ihn in die Festung zurück ({@code send} null = umschalten).
	 * Ist er gerade tot, gilt der Befehl für die Zeit nach der Wiederbelebung.
	 */
	public void commandChieftain(TeamColor team, Boolean send) {
		if (phase == MatchPhase.SETUP || phase == MatchPhase.ENDED) {
			return;
		}
		Chieftain chieftain = chieftains.get(team);
		boolean deploy = send == null ? !chieftain.deployed : send;
		if (deploy == chieftain.deployed) {
			return;
		}
		chieftain.deployed = deploy;
		chieftain.sentOnce |= deploy;
		GoblinUnit unit = chieftain(team);
		if (unit != null) {
			unit.onOrdersChanged();
			arena.playSound(null, unit.getX(), unit.getY(), unit.getZ(), ModSounds.WAR_HORN, SoundSource.HOSTILE, 1.0f, deploy ? 1.1f : 0.8f);
		}
		String key = !deploy ? "message.goblinforest.chieftain_recalled"
				: unit == null ? "message.goblinforest.chieftain_sent_later" : "message.goblinforest.chieftain_sent";
		for (ServerPlayer player : onlineHeroes(team)) {
			player.sendOverlayMessage(Component.translatable(key).withStyle(deploy ? ChatFormatting.GOLD : ChatFormatting.AQUA));
			if (unit == null) {
				playTo(player, SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 1.0f);
			}
		}
	}

	private void tickChieftains() {
		for (TeamColor team : TeamColor.values()) {
			Chieftain chieftain = chieftains.get(team);
			if (chieftain.unit != null && chieftain.unit.isRemoved() && chieftain.respawnTicks <= 0) {
				// Ohne Todesereignis verschwunden (z. B. durch einen Befehl): wie ein Tod ohne Kopfgeld behandeln.
				chieftain.unit = null;
				chieftain.respawnTicks = respawnTicks(team);
			}
			if (chieftain.unit == null && chieftain.respawnTicks > 0 && --chieftain.respawnTicks == 0) {
				spawnChieftain(team);
				for (ServerPlayer player : onlineHeroes(team)) {
					player.sendOverlayMessage(Component.translatable(chieftain.deployed ? "message.goblinforest.chieftain_back_marching"
							: "message.goblinforest.chieftain_back").withStyle(ChatFormatting.GREEN));
					playTo(player, SoundEvents.PLAYER_LEVELUP, 0.6f, 1.2f);
				}
			}
			tickRage(team, chieftain);
		}
	}

	private int respawnTicks(TeamColor team) {
		TeamState state = teams.get(team);
		return state.heroProgression().respawnSeconds(state.heroLevel()) * 20;
	}

	/** Der Häuptling ist gefallen: Kopfgeld und Erfahrung für den Gegner, Wiederbelebung nach einer Wartezeit. */
	private void onChieftainKilled(GoblinUnit unit, Entity killer, TeamColor killerTeam) {
		TeamColor team = unit.team();
		Chieftain chieftain = chieftains.get(team);
		server.getScoreboard().removePlayerFromTeam(unit.getScoreboardName());
		if (chieftain.unit == unit) {
			chieftain.unit = null;
		}
		if (chieftain.raging) {
			endRage(team);
		}
		if (phase != MatchPhase.BATTLE) {
			return;
		}
		chieftain.respawnTicks = respawnTicks(team);
		TeamColor enemy = team.opponent();
		teams.get(team).stats().heroDeaths++;
		TeamState enemyState = teams.get(enemy);
		enemyState.stats().heroKills++;
		enemyState.addGold(bounty.heroKillBounty());
		enemyState.addClanXp(bounty.heroKillClanXp());
		boolean byChieftain = killer instanceof GoblinUnit killerUnit && isChieftain(killerUnit);
		addHeroXp(enemy, byChieftain ? bounty.heroKillClanXp() : bounty.heroKillClanXp() * enemyState.heroProgression().armyXpShare());
		if (killer instanceof GoblinUnit killerUnit && owns(killerUnit) && !byChieftain) {
			killerUnit.addXp(combat.leveling().xpForKill() * 3);
			killerUnit.onKilledEnemy(arena, this);
		}
		arena.sendParticles(ParticleTypes.SOUL, unit.getX(), unit.getY() + 1, unit.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
		arena.playSound(null, unit.getX(), unit.getY(), unit.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.5f, 1.6f);
		Component who = Component.translatable("message.goblinforest.chieftain_of",
				Component.translatable(team.translationKey()).withColor(team.rgb()));
		broadcast(Component.translatable("message.goblinforest.hero_killed", who, bounty.heroKillBounty(),
				Component.translatable(enemy.translationKey()).withColor(enemy.rgb())));
		for (ServerPlayer player : onlineHeroes(team)) {
			title(player, Component.translatable("title.goblinforest.died").withStyle(ChatFormatting.DARK_RED),
					Component.translatable("title.goblinforest.died_sub", chieftain.respawnTicks / 20), 5, 50, 10);
		}
		for (ServerPlayer player : onlineHeroes(enemy)) {
			playTo(player, ModSounds.COINS, 0.9f, 0.8f);
		}
	}

	private void addHeroXp(TeamColor team, double amount) {
		TeamState state = teams.get(team);
		if (!state.addHeroXp(amount)) {
			return;
		}
		GoblinUnit unit = chieftain(team);
		if (unit != null) {
			unit.refreshChieftain(true);
			arena.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, unit.getX(), unit.getY() + 1, unit.getZ(), 40, 0.4, 1.0, 0.4, 0.4);
			arena.playSound(null, unit.getX(), unit.getY(), unit.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.HOSTILE, 1.0f, 0.8f);
		}
		for (ServerPlayer player : onlineHeroes(team)) {
			title(player, Component.translatable("title.goblinforest.hero_level", state.heroLevel()).withStyle(ChatFormatting.GOLD),
					Component.translatable("title.goblinforest.hero_level_sub",
							(int) state.heroProgression().maxHealth(state.heroLevel()),
							(int) state.heroProgression().damage(state.heroLevel())), 5, 40, 10);
			playTo(player, SoundEvents.PLAYER_LEVELUP, 1.0f, 0.8f);
		}
	}

	/** Schaden, den der Häuptling austeilt, ohne Rüstung und Raserei: Heldenlevel. Raserei-Faktor kommt dazu. */
	public double chieftainDamage(TeamColor team) {
		TeamState state = teams.get(team);
		return state.heroProgression().damage(state.heroLevel()) * state.heroDamageMultiplier(tick);
	}

	/** Lebenspunkte des Häuptlings auf dem aktuellen Heldenlevel. */
	public double chieftainMaxHealth(TeamColor team) {
		TeamState state = teams.get(team);
		return state.heroProgression().maxHealth(state.heroLevel());
	}

	/** Tempofaktor des Häuptlings (Raserei macht schneller). */
	public double chieftainSpeedMultiplier(TeamColor team) {
		return chieftains.get(team).raging ? 1.0 + balance.rage().speedBonus() : 1.0;
	}

	/** Der Häuptling hat Schaden ausgeteilt: Raserei laden, während der Raserei Lebensraub. */
	public void onChieftainDealtDamage(GoblinUnit unit, double amount) {
		if (!isChieftain(unit) || phase != MatchPhase.BATTLE || amount <= 0) {
			return;
		}
		TeamState state = teams.get(unit.team());
		if (state.rageActive(tick)) {
			float heal = (float) (amount * state.rageLifesteal());
			if (heal > 0 && unit.getHealth() < unit.getMaxHealth()) {
				unit.heal(heal);
				arena.sendParticles(new DustParticleOptions(0x9C0A0A, 1.0f), unit.getX(), unit.getY() + 1.2, unit.getZ(), 4, 0.3, 0.4, 0.3, 0);
			}
		} else {
			addRage(unit.team(), amount * balance.rage().chargePerDamageDealt());
		}
	}

	/** Der Häuptling hat Schaden eingesteckt: lädt die Raserei. */
	public void onChieftainDamaged(GoblinUnit unit, double amount) {
		if (!isChieftain(unit) || phase != MatchPhase.BATTLE || amount <= 0 || teams.get(unit.team()).rageActive(tick)) {
			return;
		}
		addRage(unit.team(), amount * balance.rage().chargePerDamageTaken());
	}

	// ================================================================ Einheiten

	PurchaseResult recruit(TeamColor team, UnitType type) {
		TeamState state = teams.get(team);
		PurchaseResult result = state.recruit(type, livingUnits(team));
		if (!result.ok()) {
			return result;
		}
		int count = balance.unit(type).groupSize();
		ArenaLayout.Point barracks = ArenaLayout.barracks(team);
		for (int i = 0; i < count; i++) {
			GoblinUnit unit = ModEntities.GOBLIN.create(arena, EntitySpawnReason.MOB_SUMMONED);
			if (unit == null) {
				continue;
			}
			double z = barracks.z() + (i - (count - 1) / 2.0) * 1.2;
			unit.snapTo(barracks.x(), barracks.y(), z, ArenaLayout.facingYaw(team), 0);
			unit.setYHeadRot(ArenaLayout.facingYaw(team));
			unit.setup(type, team, this);
			if (arena.addFreshEntity(unit)) {
				units.add(unit);
				joinScoreboardTeam(unit, team);
			}
		}
		arena.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, barracks.x(), barracks.y() + 0.5, barracks.z(), 6, 0.6, 0.2, 1.2, 0.01);
		arena.playSound(null, barracks.x(), barracks.y(), barracks.z(), SoundEvents.PIGLIN_ANGRY, SoundSource.HOSTILE, 0.8f, 1.5f);
		return result;
	}

	public void onUnitLevelUp(GoblinUnit unit) {
		TeamState state = teams.get(unit.team());
		state.stats().highestUnitLevel = Math.max(state.stats().highestUnitLevel, unit.unitLevel());
		if (unit.unitLevel() >= 4) {
			for (ServerPlayer player : onlineHeroes(unit.team())) {
				player.sendOverlayMessage(Component.translatable("message.goblinforest.unit_promoted",
						Component.translatable(unit.unitType().translationKey()),
						Component.translatable("unit.goblinforest.level." + unit.unitLevel())).withStyle(ChatFormatting.YELLOW));
			}
		}
	}

	public void onUnitKilled(GoblinUnit unit, Entity killer, TeamColor killerTeam) {
		if (unit.unitType() == UnitType.CHIEFTAIN) {
			onChieftainKilled(unit, killer, killerTeam);
			return;
		}
		units.remove(unit);
		server.getScoreboard().removePlayerFromTeam(unit.getScoreboardName());
		TeamColor owner = unit.team();
		TeamColor enemy = owner.opponent();
		Balance.UnitStats stats = balance.unit(unit.unitType());
		int level = unit.unitLevel();
		teams.get(owner).stats().unitsLost++;
		if (phase != MatchPhase.BATTLE) {
			return;
		}
		if (killerTeam == enemy) {
			TeamState state = teams.get(enemy);
			int gold = bounty.killBounty(stats, level, ArenaLayout.progress(enemy, unit.getX()));
			state.addGold(gold);
			state.stats().unitKills++;
			double clanXp = bounty.clanXpForKill(stats, level);
			state.addClanXp(clanXp);
			boolean byChieftain = killer instanceof GoblinUnit killerUnit && isChieftain(killerUnit);
			addHeroXp(enemy, byChieftain ? clanXp : clanXp * state.heroProgression().armyXpShare());
			if (killer instanceof GoblinUnit killerUnit && owns(killerUnit) && !byChieftain) {
				killerUnit.addXp(combat.leveling().xpForKill());
				killerUnit.onKilledEnemy(arena, this);
			}
			if (killer instanceof ServerPlayer player) {
				player.sendOverlayMessage(Component.translatable("message.goblinforest.bounty", gold).withStyle(ChatFormatting.GOLD));
				playTo(player, ModSounds.COINS, 0.5f, 0.9f + 0.2f * arena.getRandom().nextFloat());
			}
			arena.sendParticles(new DustParticleOptions(0xF2C744, 1.0f), unit.getX(), unit.getY() + 1.2, unit.getZ(), 6, 0.2, 0.3, 0.2, 0);
		}
		int refund = bounty.ownLossRefund(stats, level, ArenaLayout.progress(owner, unit.getX()));
		if (refund > 0) {
			teams.get(owner).addGold(refund);
		}
	}

	private void recountPopulation() {
		EnumMap<TeamColor, Integer> population = new EnumMap<>(TeamColor.class);
		for (TeamColor team : TeamColor.values()) {
			population.put(team, 0);
		}
		units.removeIf(unit -> unit.isRemoved() || !unit.isAlive());
		for (GoblinUnit unit : units) {
			Balance.UnitStats stats = balance.unit(unit.unitType());
			population.merge(unit.team(), stats.population(), Integer::sum);
		}
		population.forEach((team, value) -> teams.get(team).setPopulation(value));
	}

	private void checkReputation() {
		for (TeamColor team : TeamColor.values()) {
			int reputation = teams.get(team).reputation();
			int before = lastReputation.get(team);
			if (reputation > before) {
				lastReputation.put(team, reputation);
				for (ServerPlayer player : onlineHeroes(team)) {
					title(player, Component.empty(), Component.translatable("title.goblinforest.reputation", reputation).withStyle(ChatFormatting.AQUA), 5, 40, 10);
					playTo(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.2f);
					List<Component> unlocked = unlockedAt(team, reputation);
					if (!unlocked.isEmpty()) {
						MutableComponent list = Component.empty();
						for (int i = 0; i < unlocked.size(); i++) {
							if (i > 0) {
								list.append(", ");
							}
							list.append(unlocked.get(i));
						}
						player.sendSystemMessage(Component.translatable("message.goblinforest.unlocked", reputation, list).withStyle(ChatFormatting.AQUA));
					}
				}
			}
		}
	}

	private List<Component> unlockedAt(TeamColor team, int reputation) {
		List<Component> list = new ArrayList<>();
		for (UnitType type : UnitType.soldiers()) {
			if (balance.unit(type).unlockReputation() == reputation) {
				list.add(Component.translatable(type.translationKey()));
			}
		}
		for (SpellType spell : SpellType.values()) {
			if (balance.spell(spell.id()).unlockReputation() == reputation) {
				list.add(Component.translatable(spell.translationKey()));
			}
		}
		return list;
	}

	// ================================================================ Festungen

	private void tickTowers() {
		for (TeamColor team : TeamColor.values()) {
			TeamState state = teams.get(team);
			if (!state.towerAlive()) {
				continue;
			}
			int cooldown = towerCooldowns.get(team) - 1;
			if (cooldown > 0) {
				towerCooldowns.put(team, cooldown);
				continue;
			}
			int level = state.strongholdUpgrade(UpgradeType.TOWER);
			double range = combat.towerRange(level);
			ArenaLayout.Point muzzle = ArenaLayout.towerMuzzle(team);
			LivingEntity target = null;
			double best = Double.MAX_VALUE;
			AABB box = new AABB(muzzle.x() - range, ArenaLayout.GROUND_Y - 4, muzzle.z() - range, muzzle.x() + range, muzzle.y() + 6, muzzle.z() + range);
			for (LivingEntity candidate : arena.getEntitiesOfClass(LivingEntity.class, box, e -> isEnemyOf(team, e))) {
				double distance = muzzle.horizontalDistance(candidate.getX(), candidate.getZ());
				if (distance <= range && distance < best) {
					best = distance;
					target = candidate;
				}
			}
			if (target == null) {
				towerCooldowns.put(team, 5);
				continue;
			}
			towerCooldowns.put(team, combat.towerShotIntervalTicks());
			fireTower(team, muzzle, target, combat.towerDamage(level));
		}
	}

	/** Festungskanone: feuert auf die dichteste Gruppe von Gegnern vor dem Tor (Flächenschaden). */
	private void tickCannons() {
		for (TeamColor team : TeamColor.values()) {
			TeamState state = teams.get(team);
			if (!state.hasCannon()) {
				continue;
			}
			int cooldown = cannonCooldowns.get(team) - 1;
			if (cooldown > 0) {
				cannonCooldowns.put(team, cooldown);
				continue;
			}
			int level = state.strongholdUpgrade(UpgradeType.CANNON);
			Vec3 muzzle = vec(ArenaLayout.cannonMuzzle(team));
			double range = balance.stronghold().cannonRange();
			double radius = balance.stronghold().cannonRadius();
			AABB box = new AABB(muzzle.x - range, ArenaLayout.GROUND_Y - 2, muzzle.z - range, muzzle.x + range, muzzle.y + 4, muzzle.z + range);
			List<LivingEntity> enemies = arena.getEntitiesOfClass(LivingEntity.class, box,
					e -> isEnemyOf(team, e) && horizontalDistance(muzzle, e.position()) <= range && horizontalDistance(muzzle, e.position()) >= 3);
			LivingEntity best = null;
			int bestCount = 0;
			for (LivingEntity candidate : enemies) {
				int count = 0;
				for (LivingEntity other : enemies) {
					if (other.distanceToSqr(candidate) <= radius * radius) {
						count++;
					}
				}
				if (count > bestCount) {
					bestCount = count;
					best = candidate;
				}
			}
			if (best == null) {
				cannonCooldowns.put(team, 5);
				continue;
			}
			cannonCooldowns.put(team, combat.cannonReloadTicks(level));
			fireCannon(team, muzzle, best.position(), combat.cannonDamage(level), radius);
		}
	}

	private static double horizontalDistance(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	private void fireCannon(TeamColor team, Vec3 muzzle, Vec3 target, double damage, double radius) {
		arena.sendParticles(ParticleTypes.EXPLOSION, muzzle.x, muzzle.y, muzzle.z, 1, 0, 0, 0, 0);
		arena.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 12, 0.3, 0.3, 0.3, 0.05);
		arena.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.4f, 0.6f);
		int flight = (int) Math.clamp(muzzle.distanceTo(target) / 1.6, 5, 16);
		double arc = 1.5 + muzzle.distanceTo(target) * 0.12;
		for (int i = 1; i <= flight; i++) {
			int step = i;
			schedule(step, () -> {
				double t = step / (double) flight;
				Vec3 p = muzzle.lerp(target, t).add(0, Math.sin(Math.PI * t) * arc, 0);
				arena.sendParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 3, 0.05, 0.05, 0.05, 0);
				arena.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COAL_BLOCK.defaultBlockState()), p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0);
				if (step == flight) {
					arena.sendParticles(ParticleTypes.EXPLOSION, target.x, target.y + 0.5, target.z, 3, radius * 0.4, 0.3, radius * 0.4, 0);
					arena.playSound(null, target.x, target.y, target.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.2f, 1.0f);
					areaDamage(team, target.add(0, 0.5, 0), radius, damage, arena.damageSources().magic(), 0.7);
				}
			});
		}
	}

	/**
	 * Flächenschaden an allen Gegnern eines Clans im Umkreis (Einheiten, auch getarnte, und Häuptlinge).
	 *
	 * @return Anzahl der getroffenen Gegner
	 */
	public int areaDamage(TeamColor attackerTeam, Vec3 center, double radius, double damage, DamageSource source, double knockback) {
		AABB box = new AABB(center.x - radius, center.y - radius - 1, center.z - radius, center.x + radius, center.y + radius + 1, center.z + radius);
		int hits = 0;
		for (LivingEntity entity : arena.getEntitiesOfClass(LivingEntity.class, box, e -> isDamageableEnemy(attackerTeam, e))) {
			if (entity.position().add(0, entity.getBbHeight() / 2, 0).distanceTo(center) > radius + 0.5) {
				continue;
			}
			hurtAsTeam(entity, source, damage, attackerTeam);
			if (knockback > 0) {
				Knockback.shove(entity, entity.getX() - center.x, entity.getZ() - center.z, knockback);
			}
			hits++;
		}
		return hits;
	}

	/** Gegner, den Flächenschaden treffen darf: feindliche Einheiten (auch getarnt) einschließlich des Häuptlings. */
	private boolean isDamageableEnemy(TeamColor team, LivingEntity entity) {
		return entity.isAlive() && entity instanceof GoblinUnit unit && owns(unit) && unit.team() != team;
	}

	/** Schaden im Namen eines Clans: Einheiten merken sich den Clan für Kopfgeld. */
	private void hurtAsTeam(LivingEntity entity, DamageSource source, double damage, TeamColor team) {
		if (entity instanceof GoblinUnit unit) {
			unit.hurtByTeam(arena, source, (float) damage, team);
		}
	}

	/** Steht ein Champion-Krieger desselben Clans nahe genug, um diese Einheit mit seiner Aura zu schützen? */
	public boolean championWarriorNear(GoblinUnit unit) {
		double radius = balance.unitLeveling().champion().warriorAuraRadius();
		AABB box = unit.getBoundingBox().inflate(radius, 2, radius);
		for (GoblinUnit other : arena.getEntitiesOfClass(GoblinUnit.class, box,
				o -> o.isAlive() && o.unitType() == UnitType.WARRIOR && o.team() == unit.team() && owns(o))) {
			if (combat.isChampion(other.unitLevel()) && other.distanceTo(unit) <= radius) {
				return true;
			}
		}
		return false;
	}

	private boolean isEnemyOf(TeamColor team, LivingEntity entity) {
		return entity.isAlive() && entity instanceof GoblinUnit unit && owns(unit) && unit.team() != team && !unit.isStealthed();
	}

	private void fireTower(TeamColor team, ArenaLayout.Point muzzle, LivingEntity target, double damage) {
		Vec3 from = new Vec3(muzzle.x(), muzzle.y(), muzzle.z());
		Vec3 to = target.getEyePosition().add(0, -0.3, 0);
		Vec3 step = to.subtract(from);
		double length = step.length();
		int points = (int) Math.max(2, length / 0.7);
		DustParticleOptions dust = new DustParticleOptions(team.rgb(), 0.8f);
		for (int i = 0; i <= points; i++) {
			Vec3 p = from.add(step.scale(i / (double) points));
			arena.sendParticles(i % 3 == 0 ? ParticleTypes.CRIT : dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
		}
		arena.playSound(null, muzzle.x(), muzzle.y(), muzzle.z(), SoundEvents.ARROW_SHOOT, SoundSource.BLOCKS, 1.0f, 0.7f);
		arena.playSound(null, to.x, to.y, to.z, SoundEvents.ARROW_HIT, SoundSource.BLOCKS, 0.8f, 1.0f);
		hurtAsTeam(target, arena.damageSources().magic(), damage, team);
	}

	/** Schaden an Turm oder Festungskern, z. B. durch Einheiten, den Häuptling oder einen Meteor. */
	public void damageStructure(TeamColor target, Structure structure, double amount, Entity attacker, TeamColor attackerTeam) {
		if (phase != MatchPhase.BATTLE || target == attackerTeam || amount <= 0) {
			return;
		}
		TeamState state = teams.get(target);
		ArenaLayout.Box box = structure == Structure.CORE ? ArenaLayout.coreBox(target) : ArenaLayout.towerBox(target);
		if (attackerTeam != null) {
			teams.get(attackerTeam).stats().structureDamage += amount;
		}
		Vec3 hit = attacker != null ? closestPoint(box, attacker.position().add(0, 1, 0)) : vec(box.center());
		arena.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, structure == Structure.CORE
				? Blocks.MUD_BRICKS.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState()), hit.x, hit.y, hit.z, 8, 0.2, 0.2, 0.2, 0.1);
		if (attackerTeam != null) {
			warnDefenders(target, structure);
		}
		if (structure == Structure.TOWER) {
			if (!state.towerAlive()) {
				return;
			}
			if (state.damageTower(amount)) {
				onTowerDestroyed(target, attackerTeam);
			}
			return;
		}
		if (state.coreDestroyed()) {
			return;
		}
		state.damageCore(amount);
		int stage = ArenaBlueprint.coreStage(state.coreHealth() / state.coreMaxHealth());
		if (stage != coreStages.get(target)) {
			coreStages.put(target, stage);
			ArenaBuilder.apply(arena, ArenaBlueprint.coreBlocks(target, stage));
			ArenaLayout.Point center = box.center();
			arena.sendParticles(ParticleTypes.EXPLOSION, center.x(), center.y() + 2, center.z(), 4, 2, 2, 2, 0);
			arena.playSound(null, center.x(), center.y(), center.z(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.5f, 1.0f);
		}
		if (state.coreDestroyed()) {
			endMatch(target.opponent());
		}
	}

	private void warnDefenders(TeamColor target, Structure structure) {
		for (ServerPlayer player : onlineHeroes(target)) {
			Hero hero = heroes.get(player.getUUID());
			if (tick - hero.lastAttackWarning > 200) {
				hero.lastAttackWarning = tick;
				player.sendSystemMessage(Component.translatable("message.goblinforest.under_attack",
						Component.translatable(structure.translationKey())).withStyle(ChatFormatting.RED));
				playTo(player, SoundEvents.BELL_BLOCK, 1.0f, 0.8f);
			}
		}
	}

	private void onTowerDestroyed(TeamColor team, TeamColor attackerTeam) {
		ArenaBuilder.apply(arena, ArenaBlueprint.towerBlocks(team, true));
		ArenaLayout.Point center = ArenaLayout.towerBox(team).center();
		arena.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x(), center.y(), center.z(), 2, 1, 2, 1, 0);
		arena.sendParticles(ParticleTypes.LARGE_SMOKE, center.x(), center.y(), center.z(), 60, 2, 4, 2, 0.05);
		arena.playSound(null, center.x(), center.y(), center.z(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 3.0f, 0.7f);
		if (attackerTeam != null) {
			teams.get(attackerTeam).addBonusReputation(balance.economy().reputationPerTowerKill());
		}
		Component message = Component.translatable("message.goblinforest.tower_destroyed",
				Component.translatable(team.translationKey()).withColor(team.rgb()),
				Component.translatable(team.opponent().translationKey()).withColor(team.opponent().rgb()),
				balance.economy().reputationPerTowerKill()).withStyle(ChatFormatting.GOLD);
		broadcast(message);
		for (ServerPlayer player : onlineHeroes()) {
			title(player, Component.empty(), message, 5, 50, 10);
		}
	}

	private void emitStructureSmoke() {
		for (TeamColor team : TeamColor.values()) {
			int stage = coreStages.get(team);
			if (stage >= 2) {
				ArenaLayout.Point center = ArenaLayout.coreBox(team).center();
				arena.sendParticles(ParticleTypes.LARGE_SMOKE, center.x(), center.y() + 3.5, center.z(), stage * 3, 2, 0.5, 2, 0.02);
				if (stage >= 3) {
					arena.sendParticles(ParticleTypes.FLAME, center.x(), center.y() + 3.5, center.z(), 6, 2, 0.5, 2, 0.01);
				}
			}
			if (!teams.get(team).towerAlive()) {
				ArenaLayout.Point tower = ArenaLayout.towerBox(team).center();
				arena.sendParticles(ParticleTypes.SMOKE, tower.x(), ArenaLayout.GROUND_Y + 5.5, tower.z(), 3, 1, 0.3, 1, 0.01);
			}
		}
	}

	private static Vec3 vec(ArenaLayout.Point point) {
		return new Vec3(point.x(), point.y(), point.z());
	}

	private static Vec3 closestPoint(ArenaLayout.Box box, Vec3 from) {
		return new Vec3(Math.clamp(from.x, box.minX(), box.maxX() + 1.0),
				Math.clamp(from.y, box.minY(), box.maxY() + 1.0),
				Math.clamp(from.z, box.minZ(), box.maxZ() + 1.0));
	}

	// ================================================================ Aktionen der Spieler

	/**
	 * Aktion aus dem Netzwerk (Leiste, Kriegsmenü, Schnelltasten). Gezielte Aktionen tragen den Zielpunkt am Ende,
	 * z. B. {@code cast:fireball@12.50:-3.00} oder {@code rally@-40.00:2.00}.
	 */
	public void handleAction(ServerPlayer player, String action) {
		Hero hero = heroes.get(player.getUUID());
		if (hero == null || !hero.prepared || phase == MatchPhase.SETUP || phase == MatchPhase.ENDED) {
			return;
		}
		Vec3 point = null;
		int at = action.indexOf('@');
		if (at >= 0) {
			point = parsePoint(action.substring(at + 1));
			action = action.substring(0, at);
		}
		String[] parts = action.split(":", 2);
		String verb = parts[0];
		String arg = parts.length > 1 ? parts[1] : "";
		TeamState state = teams.get(hero.team);
		switch (verb) {
			case "recruit" -> {
				UnitType type = UnitType.byId(arg);
				if (type != null) {
					PurchaseResult result = recruit(hero.team, type);
					feedback(player, result, Component.translatable("message.goblinforest.recruited",
							Component.translatable(type.translationKey()), balance.unit(type).cost()));
				}
			}
			case "upgrade" -> {
				UpgradeKey key = UpgradeKey.parse(arg);
				if (key != null) {
					int cost = state.nextUpgradeCost(key);
					PurchaseResult result = state.buyUpgrade(key);
					feedback(player, result, Component.translatable("message.goblinforest.upgraded", upgradeName(key), state.level(key), cost));
					if (result.ok()) {
						announceToTeam(hero.team, player, Component.translatable("message.goblinforest.upgraded", upgradeName(key), state.level(key), cost));
						onUpgradeBought(hero.team, key);
					}
				}
			}
			case "spell_upgrade" -> {
				SpellType spell = SpellType.byId(arg);
				if (spell != null) {
					int cost = state.nextSpellUpgradeCost(spell);
					PurchaseResult result = state.buySpellUpgrade(spell);
					feedback(player, result, Component.translatable("message.goblinforest.spell_upgraded",
							Component.translatable(spell.translationKey()), state.spellLevel(spell), cost));
				}
			}
			case "cast" -> {
				SpellType spell = SpellType.byId(arg);
				if (spell != null && phase == MatchPhase.BATTLE) {
					PurchaseResult result = castSpellFor(hero.team, spell, groundTarget(point != null ? point : hero.focus), player);
					if (!result.ok()) {
						feedback(player, result, Component.empty());
					}
				}
			}
			case "ability" -> {
				AbilityType ability = AbilityType.byId(arg);
				if (ability != null) {
					PurchaseResult result = useAbility(hero.team, ability);
					if (!result.ok()) {
						feedback(player, result, Component.empty());
					}
				}
			}
			case "chieftain" -> commandChieftain(hero.team, switch (arg) {
				case "send" -> Boolean.TRUE;
				case "recall" -> Boolean.FALSE;
				default -> null;
			});
			case "ability_rank" -> {
				AbilityType ability = AbilityType.byId(arg);
				if (ability != null) {
					PurchaseResult result = state.buyAbilityRank(ability);
					feedback(player, result, Component.translatable("message.goblinforest.ability_ranked",
							Component.translatable(ability.translationKey()), state.abilityRank(ability)));
					GoblinUnit unit = chieftain(hero.team);
					if (result.ok() && ability == AbilityType.RAGE && unit != null) {
						unit.refreshChieftain(false);
					}
				}
			}
			case "stance" -> {
				Stance stance = "next".equals(arg) ? state.stance().next() : null;
				for (Stance candidate : Stance.values()) {
					if (candidate.id().equals(arg)) {
						stance = candidate;
					}
				}
				if (stance != null) {
					setStance(hero.team, stance);
				}
			}
			case "rally" -> setRally(player, hero.team, point != null ? point : hero.focus);
			case "focus" -> {
				Vec3 focus = parsePoint(arg);
				if (focus != null) {
					hero.focus = focus;
					moveCommander(player, hero, false);
				}
			}
			case "menu" -> ServerPlayNetworking.send(player, io.github.jan1a234.goblinforest.net.OpenMenuPayload.INSTANCE);
			default -> {
			}
		}
	}

	/** Gekaufte Verbesserung sofort sichtbar machen: Ausdauer wirkt auf lebende Einheiten, Kanone und Goldmine werden gebaut. */
	private void onUpgradeBought(TeamColor team, UpgradeKey key) {
		TeamState state = teams.get(team);
		switch (key.type()) {
			case ENDURANCE -> {
				for (GoblinUnit unit : units) {
					if (unit.team() == team && unit.isAlive()) {
						unit.refreshStats();
					}
				}
			}
			case CANNON -> {
				int level = state.strongholdUpgrade(UpgradeType.CANNON);
				ArenaBuilder.apply(arena, ArenaBlueprint.cannonBlocks(team, level));
				Vec3 at = vec(ArenaLayout.cannonBlock(team));
				arena.sendParticles(ParticleTypes.CLOUD, at.x + 0.5, at.y + 0.5, at.z + 0.5, 15, 0.4, 0.4, 0.4, 0.02);
				arena.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.0f, 0.8f);
				if (level == 1) {
					cannonCooldowns.put(team, 40);
				}
			}
			case GOLDMINE -> {
				int level = state.strongholdUpgrade(UpgradeType.GOLDMINE);
				ArenaBuilder.apply(arena, ArenaBlueprint.goldmineBlocks(team, level));
				Vec3 at = vec(ArenaLayout.goldmine(team));
				arena.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GOLD_ORE.defaultBlockState()),
						at.x, at.y, at.z, 40, 1.0, 0.6, 1.0, 0.1);
				arena.playSound(null, at.x, at.y, at.z, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.2f, 0.7f);
				arena.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 1.4f);
			}
			default -> {
			}
		}
	}

	/** Goldglitzern über der Goldmine, damit man das Einkommen sieht. */
	private void emitGoldmineSparkles() {
		if (tick % 30 != 0) {
			return;
		}
		for (TeamColor team : TeamColor.values()) {
			int level = teams.get(team).strongholdUpgrade(UpgradeType.GOLDMINE);
			if (level <= 0) {
				continue;
			}
			Vec3 at = vec(ArenaLayout.goldmine(team));
			arena.sendParticles(new DustParticleOptions(0xF2C744, 1.2f), at.x, at.y + 1.0, at.z, 2 + level * 2, 0.8, 0.6, 0.8, 0);
			arena.sendParticles(ParticleTypes.WAX_ON, at.x, at.y + 0.5, at.z, level, 0.8, 0.4, 0.8, 0);
		}
	}

	private Component upgradeName(UpgradeKey key) {
		if (key.unit() == null) {
			return Component.translatable(key.type().translationKey());
		}
		return Component.translatable("upgrade.goblinforest.for_unit", Component.translatable(key.type().translationKey()),
				Component.translatable(key.unit().translationKey()));
	}

	private void feedback(ServerPlayer player, PurchaseResult result, Component success) {
		if (result.ok()) {
			player.sendOverlayMessage(Component.empty().append(success).withStyle(ChatFormatting.GREEN));
			playTo(player, SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 1.3f);
		} else {
			player.sendOverlayMessage(Component.translatable(result.translationKey()).withStyle(ChatFormatting.RED));
			playTo(player, SoundEvents.VILLAGER_NO, 0.6f, 1.0f);
		}
	}

	private void announceToTeam(TeamColor team, ServerPlayer except, Component message) {
		for (ServerPlayer player : onlineHeroes(team)) {
			if (player != except) {
				player.sendSystemMessage(Component.empty().append(message).withStyle(ChatFormatting.GRAY));
			}
		}
	}

	void setStance(TeamColor team, Stance stance) {
		teams.get(team).setStance(stance);
		if (stance != Stance.HOLD) {
			rallyPoints.remove(team);
		}
		for (ServerPlayer player : onlineHeroes(team)) {
			player.sendOverlayMessage(Component.translatable("message.goblinforest.stance", Component.translatable(stance.translationKey()))
					.withStyle(stance == Stance.RETREAT ? ChatFormatting.YELLOW : ChatFormatting.AQUA));
			playTo(player, SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f, stance == Stance.ADVANCE ? 1.4f : stance == Stance.HOLD ? 1.0f : 0.7f);
		}
	}

	/** Sammelpunkt setzen: die Armee hält dort ihre Stellung. */
	private void setRally(ServerPlayer player, TeamColor team, Vec3 target) {
		if (phase != MatchPhase.BATTLE || target == null) {
			return;
		}
		Vec3 point = new Vec3(Mth.floor(target.x) + 0.5, ArenaLayout.GROUND_Y + 1, Mth.floor(target.z) + 0.5);
		if (!ArenaLayout.insideArena(point.x, point.z)) {
			return;
		}
		rallyPoints.put(team, point);
		setStance(team, Stance.HOLD);
		arena.playSound(null, point.x, point.y, point.z, ModSounds.WAR_HORN, SoundSource.PLAYERS, 0.7f, 1.25f);
		playTo(player, ModSounds.WAR_HORN, 0.5f, 1.25f);
	}

	private void drawRallyMarkers() {
		rallyPoints.forEach((team, point) -> {
			DustParticleOptions dust = new DustParticleOptions(team.rgb(), 1.5f);
			for (int i = 0; i < 6; i++) {
				arena.sendParticles(dust, point.x, point.y + i * 0.5, point.z, 1, 0.05, 0.05, 0.05, 0);
			}
		});
	}

	/** Liest einen Punkt "x:z" vom Client; null bei ungültiger Eingabe. Liegt immer innerhalb der Arena. */
	private static Vec3 parsePoint(String text) {
		String[] xz = text.split(":");
		if (xz.length != 2) {
			return null;
		}
		try {
			double x = Double.parseDouble(xz[0]);
			double z = Double.parseDouble(xz[1]);
			if (!Double.isFinite(x) || !Double.isFinite(z)) {
				return null;
			}
			return new Vec3(Math.clamp(x, -ArenaLayout.HALF_LENGTH + 1, ArenaLayout.HALF_LENGTH - 1), ArenaLayout.GROUND_Y + 1,
					Math.clamp(z, -ArenaLayout.HALF_WIDTH + 1, ArenaLayout.HALF_WIDTH - 1));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** Oberfläche (erster freier Block über dem Boden) an einer Stelle der Arena. */
	private Vec3 surfaceAt(double x, double z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(x), ArenaLayout.GROUND_Y + 24, Mth.floor(z));
		while (pos.getY() > ArenaLayout.GROUND_Y && arena.getBlockState(pos).isAir()) {
			pos.move(0, -1, 0);
		}
		return new Vec3(x, pos.getY() + 1, z);
	}

	/** Zielpunkt eines Zaubers am Boden: die Oberfläche an der angeklickten Stelle. */
	private Vec3 groundTarget(Vec3 point) {
		return surfaceAt(point.x, point.z);
	}

	/**
	 * Wirkt einen bereits bezahlten Zauber von {@code origin} auf {@code target}. {@code caster} darf null sein
	 * (KI-Clan oder Selbsttest); dann zählt der Schaden nur für den Clan.
	 */
	private void releaseSpell(TeamColor team, SpellType spell, Vec3 origin, Vec3 target, ServerPlayer caster) {
		Balance.Spell config = balance.spell(spell.id());
		double amount = config.amountAtLevel(teams.get(team).spellLevel(spell));
		DamageSource source = caster != null ? arena.damageSources().indirectMagic(caster, caster) : arena.damageSources().magic();
		switch (spell) {
			case FIREBALL -> launchFireball(origin, team, target, config.radius(), amount, source);
			case HEALING -> castHealing(team, target, config.radius(), amount);
			case ROOTS -> castRoots(team, target, config.radius(), (int) Math.round(amount * 20));
			case LIGHTNING -> castLightning(team, target, config.radius(), amount, Math.max(1, config.count()), source);
			case METEOR -> castMeteor(team, target, config.radius(), amount, source);
		}
	}

	private void launchFireball(Vec3 from, TeamColor team, Vec3 target, double radius, double damage, DamageSource source) {
		arena.playSound(null, from.x, from.y, from.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0f, 0.9f);
		int flight = (int) Math.max(4, Math.min(16, from.distanceTo(target) / 2.5));
		for (int i = 1; i <= flight; i++) {
			int step = i;
			schedule(step, () -> {
				Vec3 point = from.lerp(target, step / (double) flight);
				arena.sendParticles(ParticleTypes.FLAME, point.x, point.y, point.z, 8, 0.15, 0.15, 0.15, 0.02);
				arena.sendParticles(ParticleTypes.LARGE_SMOKE, point.x, point.y, point.z, 2, 0.1, 0.1, 0.1, 0.01);
				if (step == flight) {
					explodeFireball(team, target, radius, damage, source);
				}
			});
		}
	}

	private void explodeFireball(TeamColor team, Vec3 center, double radius, double damage, DamageSource source) {
		arena.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0, 0, 0, 0);
		arena.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z, 60, radius * 0.4, 0.5, radius * 0.4, 0.15);
		arena.sendParticles(ParticleTypes.LAVA, center.x, center.y, center.z, 12, radius * 0.3, 0.3, radius * 0.3, 0);
		arena.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.6f, 1.1f);
		areaDamage(team, center, radius, damage, source, 0.5);
	}

	private void castHealing(TeamColor team, Vec3 center, double radius, double amount) {
		arena.sendParticles(ParticleTypes.HAPPY_VILLAGER, center.x, center.y + 0.5, center.z, 50, radius * 0.5, 0.6, radius * 0.5, 0.05);
		arena.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, center.x, center.y + 1, center.z, 40, radius * 0.5, 0.8, radius * 0.5, 0);
		arena.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.RED_MUSHROOM_BLOCK.defaultBlockState()),
				center.x, center.y + 0.2, center.z, 30, radius * 0.4, 0.1, radius * 0.4, 0.1);
		arena.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5f, 0.8f);
		arena.playSound(null, center.x, center.y, center.z, SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 1.0f, 1.2f);
		AABB box = new AABB(center.x - radius, center.y - radius, center.z - radius, center.x + radius, center.y + radius, center.z + radius);
		for (LivingEntity entity : arena.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && teamOf(e) == team)) {
			if (entity.position().distanceTo(center) > radius + 0.5) {
				continue;
			}
			if (!(entity instanceof GoblinUnit)) {
				continue;
			}
			entity.heal((float) amount);
			arena.sendParticles(ParticleTypes.HEART, entity.getX(), entity.getY() + 2, entity.getZ(), 2, 0.3, 0.2, 0.3, 0);
		}
	}

	/** Wurzelfessel: Gegner im Umkreis können sich für {@code ticks} Ticks nicht bewegen. */
	private void castRoots(TeamColor team, Vec3 center, double radius, int ticks) {
		BlockParticleOption roots = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.defaultBlockState());
		for (int i = 0; i < 40; i++) {
			double angle = Math.PI * 2 * i / 40;
			arena.sendParticles(roots, center.x + Math.cos(angle) * radius, center.y + 0.2, center.z + Math.sin(angle) * radius, 3, 0.1, 0.1, 0.1, 0.05);
		}
		arena.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.HANGING_ROOTS.defaultBlockState()),
				center.x, center.y + 0.5, center.z, 60, radius * 0.5, 0.4, radius * 0.5, 0.1);
		arena.playSound(null, center.x, center.y, center.z, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.5f, 0.6f);
		arena.playSound(null, center.x, center.y, center.z, SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.PLAYERS, 0.8f, 0.8f);
		AABB box = new AABB(center.x - radius, center.y - radius - 1, center.z - radius, center.x + radius, center.y + radius + 1, center.z + radius);
		for (LivingEntity entity : arena.getEntitiesOfClass(LivingEntity.class, box, e -> isDamageableEnemy(team, e))) {
			if (entity.position().distanceTo(center) > radius + 0.5) {
				continue;
			}
			if (entity instanceof GoblinUnit unit) {
				unit.root(ticks);
			}
		}
	}

	/** Blitzsturm: {@code bolts} Blitze nacheinander auf zufällige Gegner im Umkreis (bei wenigen Gegnern auch mehrfach). */
	private void castLightning(TeamColor team, Vec3 center, double radius, double damage, int bolts, DamageSource source) {
		arena.playSound(null, center.x, center.y, center.z, SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.2f, 0.7f);
		arena.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y + 6, center.z, 80, radius * 0.6, 1.0, radius * 0.6, 0.1);
		for (int i = 0; i < bolts; i++) {
			schedule(6 + i * 6, () -> {
				AABB box = new AABB(center.x - radius, center.y - radius - 2, center.z - radius, center.x + radius, center.y + radius + 2, center.z + radius);
				List<LivingEntity> targets = new ArrayList<>();
				for (LivingEntity entity : arena.getEntitiesOfClass(LivingEntity.class, box, e -> isDamageableEnemy(team, e))) {
					if (horizontalDistance(center, entity.position()) <= radius) {
						targets.add(entity);
					}
				}
				Vec3 strike;
				LivingEntity victim = null;
				if (targets.isEmpty()) {
					double angle = arena.getRandom().nextDouble() * Math.PI * 2;
					double distance = arena.getRandom().nextDouble() * radius;
					strike = new Vec3(center.x + Math.cos(angle) * distance, ArenaLayout.GROUND_Y + 1, center.z + Math.sin(angle) * distance);
				} else {
					victim = targets.get(arena.getRandom().nextInt(targets.size()));
					strike = victim.position();
				}
				strikeVisual(strike);
				if (victim != null) {
					hurtAsTeam(victim, source, damage, team);
				}
			});
		}
	}

	/** Blitz nur als Effekt (kein Feuer, kein Vanilla-Schaden); der Schaden kommt vom Zauber. */
	private void strikeVisual(Vec3 at) {
		net.minecraft.world.entity.LightningBolt bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(arena, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.setVisualOnly(true);
			bolt.snapTo(at.x, at.y, at.z, 0, 0);
			arena.addFreshEntity(bolt);
		}
		arena.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 1, at.z, 30, 0.4, 1.0, 0.4, 0.3);
		arena.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.5, at.z, 12, 0.2, 1.2, 0.2, 0.05);
	}

	/**
	 * Meteor: kurze Warnung am Boden, dann stürzt ein Feuerbrocken vom Himmel. Trifft alle Gegner im Umkreis
	 * und auch feindliche Gebäude.
	 */
	private void castMeteor(TeamColor team, Vec3 target, double radius, double damage, DamageSource source) {
		Vec3 ground = new Vec3(target.x, ArenaLayout.GROUND_Y + 1, target.z);
		Vec3 sky = ground.add(-ArenaLayout.side(team) * -10, 30, 6);
		int warning = 20;
		int fall = 16;
		DustParticleOptions red = new DustParticleOptions(0xFF3010, 2.0f);
		for (int i = 0; i < warning; i += 4) {
			schedule(i + 1, () -> {
				for (int k = 0; k < 32; k++) {
					double angle = Math.PI * 2 * k / 32;
					arena.sendParticles(red, ground.x + Math.cos(angle) * radius, ground.y + 0.1, ground.z + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
				}
			});
		}
		arena.playSound(null, ground.x, ground.y, ground.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.5f, 0.6f);
		for (int i = 1; i <= fall; i++) {
			int step = i;
			schedule(warning + step, () -> {
				Vec3 p = sky.lerp(ground, step / (double) fall);
				arena.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 25, 0.5, 0.5, 0.5, 0.05);
				arena.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 8, 0.4, 0.4, 0.4, 0.02);
				arena.sendParticles(ParticleTypes.LAVA, p.x, p.y, p.z, 3, 0.3, 0.3, 0.3, 0);
				if (step == 1) {
					arena.playSound(null, p.x, p.y, p.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 3.0f, 0.4f);
				}
				if (step == fall) {
					impactMeteor(team, ground, radius, damage, source);
				}
			});
		}
	}

	private void impactMeteor(TeamColor team, Vec3 ground, double radius, double damage, DamageSource source) {
		arena.sendParticles(ParticleTypes.EXPLOSION_EMITTER, ground.x, ground.y + 0.5, ground.z, 3, radius * 0.4, 0.3, radius * 0.4, 0);
		arena.sendParticles(ParticleTypes.LAVA, ground.x, ground.y + 0.5, ground.z, 40, radius * 0.5, 0.5, radius * 0.5, 0);
		arena.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MAGMA_BLOCK.defaultBlockState()),
				ground.x, ground.y + 0.5, ground.z, 120, radius * 0.5, 0.8, radius * 0.5, 0.3);
		arena.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, ground.x, ground.y + 0.5, ground.z, 30, radius * 0.4, 0.5, radius * 0.4, 0.02);
		arena.playSound(null, ground.x, ground.y, ground.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 4.0f, 0.5f);
		arena.playSound(null, ground.x, ground.y, ground.z, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 2.0f, 0.7f);
		areaDamage(team, ground.add(0, 0.5, 0), radius, damage, source, 1.2);
		TeamColor enemy = team.opponent();
		for (Structure structure : Structure.values()) {
			ArenaLayout.Box box = structure == Structure.CORE ? ArenaLayout.coreBox(enemy) : ArenaLayout.towerBox(enemy);
			if (structureAlive(enemy, structure) && box.distance(ground.x, ground.y, ground.z) <= radius) {
				damageStructure(enemy, structure, damage, null, team);
			}
		}
	}

	/**
	 * Fähigkeit des Häuptlings auslösen, für Spieler, KI und Selbsttest. Wirkt rund um den Häuptling, wo immer er
	 * gerade kämpft; ist er tot, geht es nicht.
	 */
	public PurchaseResult useAbility(TeamColor team, AbilityType ability) {
		if (phase != MatchPhase.BATTLE) {
			return PurchaseResult.NOT_AVAILABLE;
		}
		GoblinUnit chief = chieftain(team);
		if (chief == null) {
			return PurchaseResult.HERO_DEAD;
		}
		TeamState state = teams.get(team);
		PurchaseResult result = state.checkAbility(ability, tick);
		if (!result.ok()) {
			return result;
		}
		state.startAbilityCooldown(ability, tick);
		Balance.Ability config = balance.ability(ability.id());
		int rank = state.abilityRank(ability);
		chief.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
		double radius = config.radiusAt(rank);
		AABB box = chief.getBoundingBox().inflate(radius, 3, radius);
		switch (ability) {
			case BLOODLUST -> {
				int affected = 0;
				for (GoblinUnit unit : arena.getEntitiesOfClass(GoblinUnit.class, box, u -> owns(u) && u.team() == team && u.isAlive())) {
					if (unit.distanceTo(chief) <= radius) {
						unit.applyBloodlust(config.durationTicksAt(rank), config.amountAt(rank));
						arena.sendParticles(ParticleTypes.ANGRY_VILLAGER, unit.getX(), unit.getY() + 2.2, unit.getZ(), 1, 0.1, 0.1, 0.1, 0);
						affected++;
					}
				}
				DustParticleOptions dust = new DustParticleOptions(0xD0201A, 1.6f);
				for (int i = 0; i < 48; i++) {
					double angle = Math.PI * 2 * i / 48;
					arena.sendParticles(dust, chief.getX() + Math.cos(angle) * radius, chief.getY() + 0.3, chief.getZ() + Math.sin(angle) * radius, 1, 0, 0.1, 0, 0);
				}
				arena.playSound(null, chief.getX(), chief.getY(), chief.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.0f, 1.5f);
				for (ServerPlayer player : onlineHeroes(team)) {
					player.sendOverlayMessage(Component.translatable("message.goblinforest.bloodlust", affected).withStyle(ChatFormatting.RED));
				}
			}
			case BATTLE_SLAM -> {
				double damage = config.amountAt(rank) * state.heroDamageMultiplier(tick);
				DamageSource source = arena.damageSources().mobAttack(chief);
				for (LivingEntity entity : arena.getEntitiesOfClass(LivingEntity.class, box, e -> isDamageableEnemy(team, e))) {
					if (entity.distanceTo(chief) > radius) {
						continue;
					}
					hurtAsTeam(entity, source, damage, team);
					Knockback.shove(entity, entity.getX() - chief.getX(), entity.getZ() - chief.getZ(), 1.3);
				}
				arena.sendParticles(ParticleTypes.EXPLOSION, chief.getX(), chief.getY() + 0.2, chief.getZ(), 3, 1, 0.1, 1, 0);
				arena.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
						chief.getX(), chief.getY() + 0.1, chief.getZ(), 80, radius * 0.5, 0.1, radius * 0.5, 0.3);
				arena.playSound(null, chief.getX(), chief.getY(), chief.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.HOSTILE, 1.2f, 0.8f);
				arena.playSound(null, chief.getX(), chief.getY(), chief.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.7f, 1.4f);
			}
			case RAGE -> startRage(team, chief);
		}
		return PurchaseResult.OK;
	}

	// ================================================================ Raserei

	private void startRage(TeamColor team, GoblinUnit chief) {
		TeamState state = teams.get(team);
		chieftains.get(team).raging = true;
		chief.refreshChieftain(false);
		arena.sendParticles(ParticleTypes.ANGRY_VILLAGER, chief.getX(), chief.getY() + 2.4, chief.getZ(), 6, 0.4, 0.3, 0.4, 0);
		arena.sendParticles(ParticleTypes.FLAME, chief.getX(), chief.getY() + 1, chief.getZ(), 40, 0.5, 0.8, 0.5, 0.08);
		arena.playSound(null, chief.getX(), chief.getY(), chief.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.4f, 0.8f);
		arena.playSound(null, chief.getX(), chief.getY(), chief.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 0.5f, 1.6f);
		announceToTeam(team.opponent(), null, Component.translatable("message.goblinforest.enemy_rage").withStyle(ChatFormatting.RED));
		for (ServerPlayer player : onlineHeroes(team)) {
			title(player, Component.empty(), Component.translatable("title.goblinforest.rage").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), 0, 30, 10);
			player.sendOverlayMessage(Component.translatable("message.goblinforest.rage_started", state.rageRemaining(tick) / 20).withStyle(ChatFormatting.DARK_RED));
		}
	}

	private void endRage(TeamColor team) {
		chieftains.get(team).raging = false;
		teams.get(team).endRage();
		GoblinUnit chief = chieftain(team);
		if (chief != null) {
			chief.refreshChieftain(false);
		}
	}

	private void tickRage(TeamColor team, Chieftain chieftain) {
		if (!chieftain.raging) {
			return;
		}
		if (!teams.get(team).rageActive(tick)) {
			endRage(team);
			for (ServerPlayer player : onlineHeroes(team)) {
				player.sendOverlayMessage(Component.translatable("message.goblinforest.rage_ended").withStyle(ChatFormatting.GRAY));
			}
			return;
		}
		GoblinUnit chief = chieftain(team);
		if (chief != null && tick % 4 == 0) {
			arena.sendParticles(new DustParticleOptions(0xB01010, 1.4f), chief.getX(), chief.getY() + 1.0, chief.getZ(), 3, 0.35, 0.6, 0.35, 0);
			arena.sendParticles(ParticleTypes.SMALL_FLAME, chief.getX(), chief.getY() + 0.2, chief.getZ(), 1, 0.3, 0.1, 0.3, 0.01);
		}
	}

	private void addRage(TeamColor team, double amount) {
		TeamState state = teams.get(team);
		boolean wasFull = state.rageCharge() >= state.rageMax();
		state.addRage(amount, tick);
		if (!wasFull && state.rageCharge() >= state.rageMax()) {
			for (ServerPlayer player : onlineHeroes(team)) {
				player.sendOverlayMessage(Component.translatable("message.goblinforest.rage_ready",
						Component.keybind("key.goblinforest.rage")).withStyle(ChatFormatting.GOLD));
				playTo(player, SoundEvents.PIGLIN_BRUTE_ANGRY, 0.8f, 1.2f);
			}
		}
	}

	// ================================================================ Ende

	private void checkForfeit() {
		if (practice && ai == null) {
			return;
		}
		for (TeamColor team : TeamColor.values()) {
			if (team == aiTeam) {
				continue;
			}
			boolean anyOnline = !onlineHeroes(team).isEmpty();
			int ticks = anyOnline ? 0 : offlineTicks.get(team) + 20;
			offlineTicks.put(team, ticks);
			if (ticks == 20 * 30) {
				broadcast(Component.translatable("message.goblinforest.forfeit_warning",
						Component.translatable(team.translationKey()).withColor(team.rgb()), (FORFEIT_TICKS - ticks) / 20).withStyle(ChatFormatting.YELLOW));
			}
			if (ticks >= FORFEIT_TICKS) {
				endMatch(team.opponent());
				return;
			}
		}
	}

	private void endMatch(TeamColor winningTeam) {
		if (phase == MatchPhase.ENDED) {
			return;
		}
		phase = MatchPhase.ENDED;
		winner = winningTeam;
		phaseTicks = balance.match().endDelaySeconds() * 20;
		Series series = options.series();
		if (series != null) {
			series.record(winningTeam);
			nextRound = !series.decided();
			if (nextRound) {
				phaseTicks = Math.max(phaseTicks, balance.match().roundBreakSeconds() * 20);
			}
		}
		TeamColor loser = winningTeam.opponent();
		if (teams.get(loser).coreDestroyed()) {
			coreStages.put(loser, 4);
			ArenaBuilder.apply(arena, ArenaBlueprint.coreBlocks(loser, 4));
		}
		for (ServerPlayer player : onlineHeroes()) {
			Hero hero = heroes.get(player.getUUID());
			boolean won = hero.team == winningTeam;
			Component winnerName = Component.translatable(winningTeam.translationKey()).withColor(winningTeam.rgb());
			if (series == null) {
				title(player, Component.translatable(won ? "title.goblinforest.victory" : "title.goblinforest.defeat")
								.withStyle(won ? ChatFormatting.GOLD : ChatFormatting.DARK_RED, ChatFormatting.BOLD),
						Component.translatable("title.goblinforest.winner", winnerName), 10, 100, 20);
			} else {
				Component score = Component.translatable("title.goblinforest.series_score", series.wins(TeamColor.RED), series.wins(TeamColor.GREEN));
				String key = nextRound ? (won ? "title.goblinforest.round_won" : "title.goblinforest.round_lost")
						: (won ? "title.goblinforest.series_won" : "title.goblinforest.series_lost");
				title(player, Component.translatable(key).withStyle(won ? ChatFormatting.GOLD : ChatFormatting.DARK_RED, ChatFormatting.BOLD),
						score, 10, 100, 20);
				player.sendSystemMessage(Component.translatable(nextRound ? "message.goblinforest.round_result" : "message.goblinforest.series_result",
						winnerName, series.wins(TeamColor.RED), series.wins(TeamColor.GREEN), series.bestOf()).withStyle(ChatFormatting.GOLD));
			}
			playTo(player, won ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.WITHER_SPAWN, 1.0f, won ? 1.0f : 0.6f);
		}
		sendStatistics();
	}

	private void sendStatistics() {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("stats.goblinforest.header", formatTime(battleTicks)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		for (TeamColor team : TeamColor.values()) {
			TeamState state = teams.get(team);
			TeamState.Statistics s = state.stats();
			lines.add(Component.translatable("stats.goblinforest.team",
					Component.translatable(team.translationKey()).withColor(team.rgb()),
					team == winner ? Component.translatable("stats.goblinforest.winner_mark").withStyle(ChatFormatting.GOLD) : Component.empty()));
			lines.add(Component.translatable("stats.goblinforest.units", s.unitsRecruited, s.unitKills, s.unitsLost,
					"★".repeat(Math.max(0, s.highestUnitLevel - 1)) + " " + s.highestUnitLevel).withStyle(ChatFormatting.GRAY));
			lines.add(Component.translatable("stats.goblinforest.hero", state.heroLevel(), s.heroKills, s.heroDeaths, s.spellsCast).withStyle(ChatFormatting.GRAY));
			lines.add(Component.translatable("stats.goblinforest.economy", (int) s.goldEarned, (int) s.goldSpent, state.reputation(),
					(int) s.structureDamage).withStyle(ChatFormatting.GRAY));
		}
		for (ServerPlayer player : onlineHeroes()) {
			for (Component line : lines) {
				player.sendSystemMessage(line);
			}
		}
	}

	private static String formatTime(long ticks) {
		long seconds = ticks / 20;
		return String.format("%d:%02d", seconds / 60, seconds % 60);
	}

	/**
	 * Schließt eine Runde einer Serie ab: Einheiten und Effekte weg, aber die Spieler bleiben in der Arena
	 * (ihre Sicherung bleibt bestehen), bis die nächste Runde sie wieder aufstellt.
	 */
	private void finishRound() {
		if (finished) {
			return;
		}
		finished = true;
		discardUnits();
		clearArenaEntities();
		removeScoreboardTeams();
		scheduled.clear();
	}

	/** Entfernt alle Einheiten und Häuptlinge. */
	private void discardUnits() {
		for (GoblinUnit unit : units) {
			unit.discard();
		}
		units.clear();
		for (Chieftain chieftain : chieftains.values()) {
			if (chieftain.unit != null) {
				chieftain.unit.discard();
				chieftain.unit = null;
			}
		}
	}

	/** Bricht das Match ab (oder schließt es nach dem Ende): alle Spieler zurück, Arena aufräumen. */
	public void finish(Component reason) {
		if (finished) {
			return;
		}
		finished = true;
		if (reason != null) {
			broadcast(reason);
		}
		for (ServerPlayer player : onlineHeroes()) {
			Hero hero = heroes.get(player.getUUID());
			if (hero.prepared || player.level() == arena) {
				restoreHero(player);
			}
		}
		discardUnits();
		clearArenaEntities();
		removeScoreboardTeams();
		forceChunks(false);
		scheduled.clear();
	}

	// ================================================================ Anzeige

	private void broadcast(Component message) {
		for (ServerPlayer player : onlineHeroes()) {
			player.sendSystemMessage(message);
		}
	}

	private static void title(ServerPlayer player, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
		player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
		player.connection.send(new ClientboundSetTitleTextPacket(title));
	}

	/** Spielt einen Klang nur für diesen Spieler (der Gegner hört Countdown, Kopfgeld usw. nicht mit). */
	private void playTo(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
		player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.MASTER,
				player.getX(), player.getY(), player.getZ(), volume, pitch, player.getRandom().nextLong()));
	}

	private void syncAll() {
		for (ServerPlayer player : onlineHeroes()) {
			Hero hero = heroes.get(player.getUUID());
			if (hero.prepared || phase == MatchPhase.SETUP) {
				ServerPlayNetworking.send(player, snapshot(hero));
			}
		}
	}

	private MatchStatePayload snapshot(Hero hero) {
		TeamColor team = hero.team;
		TeamColor enemy = team.opponent();
		TeamState state = teams.get(team);
		TeamState enemyState = teams.get(enemy);
		int[] unitCounts = new int[UnitType.values().length];
		int enemyUnits = 0;
		for (GoblinUnit unit : units) {
			if (!unit.isAlive()) {
				continue;
			}
			if (unit.team() == team) {
				unitCounts[unit.unitType().ordinal()]++;
			} else {
				enemyUnits++;
			}
		}
		List<MatchStatePayload.ShopEntry> shop = new ArrayList<>();
		int living = livingUnits(team);
		for (UnitType type : UnitType.soldiers()) {
			Balance.UnitStats stats = balance.unit(type);
			shop.add(new MatchStatePayload.ShopEntry("recruit:" + type.id(), 0, 0, stats.cost(), stats.unlockReputation(),
					state.checkRecruit(type, living).ordinal()));
		}
		for (UpgradeKey key : UpgradeKey.all(type -> balance.unit(type).ranged())) {
			int cost = state.nextUpgradeCost(key);
			shop.add(new MatchStatePayload.ShopEntry("upgrade:" + key.serialize(), state.level(key), balance.upgrade(key.type().id()).maxLevel(),
					Math.max(0, cost), Math.max(0, state.nextUpgradeReputation(key)), state.checkUpgrade(key).ordinal()));
		}
		for (SpellType spell : SpellType.values()) {
			Balance.Spell config = balance.spell(spell.id());
			shop.add(new MatchStatePayload.ShopEntry("spell_upgrade:" + spell.id(), state.spellLevel(spell), config.maxLevel(),
					Math.max(0, state.nextSpellUpgradeCost(spell)), Math.max(config.unlockReputation(), state.nextSpellUpgradeReputation(spell)),
					state.checkSpellUpgrade(spell).ordinal()));
			shop.add(new MatchStatePayload.ShopEntry("cast:" + spell.id(), state.spellLevel(spell), config.maxLevel(), config.cost(),
					config.unlockReputation(), state.checkCast(spell, tick).ordinal()));
		}
		for (AbilityType ability : AbilityType.values()) {
			shop.add(new MatchStatePayload.ShopEntry("ability_rank:" + ability.id(), state.abilityRank(ability),
					balance.ability(ability.id()).maxRank(), 1, 0, state.checkAbilityRank(ability).ordinal()));
			shop.add(new MatchStatePayload.ShopEntry("ability:" + ability.id(), state.abilityRank(ability),
					balance.ability(ability.id()).maxRank(), 0, 0, state.checkAbility(ability, tick).ordinal()));
		}
		List<MatchStatePayload.Cooldown> cooldowns = new ArrayList<>();
		for (SpellType spell : SpellType.values()) {
			cooldowns.add(new MatchStatePayload.Cooldown(spell.id(), (int) state.cooldownRemaining(spell.id(), tick), (int) state.cooldownLength(spell.id())));
		}
		for (AbilityType ability : AbilityType.values()) {
			cooldowns.add(new MatchStatePayload.Cooldown(ability.id(), (int) state.cooldownRemaining(ability.id(), tick), (int) state.cooldownLength(ability.id())));
		}
		Chieftain chieftain = chieftains.get(team);
		GoblinUnit chief = chieftain(team);
		int chieftainState = chief == null ? MatchStatePayload.CHIEFTAIN_DEAD
				: chieftain.deployed ? MatchStatePayload.CHIEFTAIN_FIELD : MatchStatePayload.CHIEFTAIN_HOME;
		if (chief == null && chieftain.respawnTicks <= 0) {
			// Vor Rundenbeginn gibt es noch keinen Häuptling: er wartet in der Festung.
			chieftainState = MatchStatePayload.CHIEFTAIN_HOME;
		}
		int phaseSeconds = switch (phase) {
			case COUNTDOWN, ENDED -> (phaseTicks + 19) / 20;
			case SETUP -> builder == null ? 0 : (int) Math.round(builder.progress() * 100);
			default -> 0;
		};
		return new MatchStatePayload(
				phase.ordinal(), team.ordinal(), phaseSeconds, (int) (battleTicks / 20),
				state.gold(), state.reputation(), (float) state.reputationProgress(),
				state.population(), state.populationLimit(), state.stance().ordinal(),
				state.heroLevel(), (float) state.heroProgression().progressToNext(state.heroXp()),
				chieftainState, chieftainRespawnSeconds(team),
				chief == null ? 0f : chief.getHealth(), (float) chieftainMaxHealth(team),
				chief == null ? 0f : (float) chief.getX(), chief == null ? 0f : (float) chief.getZ(), chieftain.sentOnce,
				new float[] {(float) teams.get(TeamColor.RED).coreHealth(), (float) teams.get(TeamColor.GREEN).coreHealth()},
				new float[] {(float) teams.get(TeamColor.RED).coreMaxHealth(), (float) teams.get(TeamColor.GREEN).coreMaxHealth()},
				new float[] {(float) teams.get(TeamColor.RED).towerHealth(), (float) teams.get(TeamColor.GREEN).towerHealth()},
				new float[] {(float) teams.get(TeamColor.RED).towerMaxHealth(), (float) teams.get(TeamColor.GREEN).towerMaxHealth()},
				unitCounts, enemyUnits, enemyState.heroLevel(), enemyState.reputation(), rallyPoints.containsKey(team),
				shop, cooldowns, spellRadii(),
				state.abilityPoints(), (float) (state.rageCharge() / state.rageMax()), (int) ((state.rageRemaining(tick) + 19) / 20),
				phase == MatchPhase.BATTLE ? (int) secondsUntilSuddenDeath() : -1, (float) state.incomePerSecond(),
				new int[] {roundWins(TeamColor.RED), roundWins(TeamColor.GREEN)}, bestOf());
	}

	private float[] spellRadii() {
		float[] radii = new float[SpellType.values().length];
		for (SpellType spell : SpellType.values()) {
			radii[spell.ordinal()] = (float) balance.spell(spell.id()).radius();
		}
		return radii;
	}

	/** Zeilen für {@code /gf status}. */
	public List<Component> statusLines() {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("command.goblinforest.status.phase", Component.translatable("phase.goblinforest." + phase.name().toLowerCase()),
				formatTime(battleTicks)));
		if (options.series() != null) {
			Series series = options.series();
			lines.add(Component.translatable("command.goblinforest.status.series", series.round(), series.bestOf(),
					series.wins(TeamColor.RED), series.wins(TeamColor.GREEN)));
		}
		for (TeamColor team : TeamColor.values()) {
			TeamState state = teams.get(team);
			List<String> names = new ArrayList<>();
			for (Hero hero : heroes.values()) {
				if (hero.team == team) {
					names.add(hero.name);
				}
			}
			if (team == aiTeam) {
				names.add(Component.translatable("command.goblinforest.status.ai", Component.translatable(options.ai().translationKey())).getString());
			}
			lines.add(Component.translatable("command.goblinforest.status.team",
					Component.translatable(team.translationKey()).withColor(team.rgb()),
					String.join(", ", names),
					(int) state.coreHealth(), (int) state.coreMaxHealth(),
					livingUnits(team), state.gold(), state.reputation(), state.heroLevel()));
		}
		return lines;
	}

	/** Für Tests und den Selbsttest: Gold gutschreiben. */
	public void grantGold(TeamColor team, int amount) {
		teams.get(team).addGold(amount);
	}

	/** Für den Selbsttest: Einheiten kaufen, als hätte ein Spieler sie bestellt. */
	public PurchaseResult recruitForTest(TeamColor team, UnitType type) {
		return recruit(team, type);
	}

	/** Verbesserung für einen Clan kaufen, als hätte ein Spieler sie bestellt (inklusive Bauwerken); für KI und Selbsttest. */
	public PurchaseResult buyUpgradeFor(TeamColor team, UpgradeKey key) {
		PurchaseResult result = teams.get(team).buyUpgrade(key);
		if (result.ok()) {
			onUpgradeBought(team, key);
		}
		return result;
	}

	/** Zauber für einen Clan auf einen Punkt wirken (bezahlt wie ein echter Zauber); für KI und Selbsttest. */
	public PurchaseResult castSpellFor(TeamColor team, SpellType spell, Vec3 target) {
		return castSpellFor(team, spell, groundTarget(target), null);
	}

	/**
	 * Wirkt ein Wunder auf einen Punkt am Boden. Der Feldherr zaubert aus der Draufsicht, deshalb kommt der Feuerball
	 * schräg vom Himmel aus Richtung der eigenen Festung, die übrigen Zauber erscheinen direkt am Ziel.
	 */
	private PurchaseResult castSpellFor(TeamColor team, SpellType spell, Vec3 target, ServerPlayer caster) {
		TeamState state = teams.get(team);
		PurchaseResult result = state.checkCast(spell, tick);
		if (result.ok()) {
			state.payCast(spell, tick);
			Vec3 origin = spell == SpellType.FIREBALL ? target.add(ArenaLayout.side(team) * 10, 16, 0) : target.add(0, 3, 0);
			releaseSpell(team, spell, origin, target, caster);
		}
		return result;
	}

	/** Lebende Einheiten eines Clans. */
	public List<GoblinUnit> unitsOf(TeamColor team) {
		List<GoblinUnit> list = new ArrayList<>();
		for (GoblinUnit unit : units) {
			if (unit.isAlive() && unit.team() == team) {
				list.add(unit);
			}
		}
		return list;
	}

	public long battleTicks() {
		return battleTicks;
	}

	public long currentTick() {
		return tick;
	}

	public int unitCount() {
		return units.size();
	}

	/** Lebende Einheiten eines Typs (für den Selbsttest). */
	public int unitCount(TeamColor team, UnitType type) {
		int count = 0;
		for (GoblinUnit unit : units) {
			if (unit.isAlive() && unit.team() == team && unit.unitType() == type) {
				count++;
			}
		}
		return count;
	}
}
