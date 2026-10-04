package io.github.jan1a234.goblinforest.game;

import io.github.jan1a234.goblinforest.GoblinForest;
import io.github.jan1a234.goblinforest.arena.ArenaBlueprint;
import io.github.jan1a234.goblinforest.arena.ArenaBuilder;
import io.github.jan1a234.goblinforest.arena.ArenaLayout;
import io.github.jan1a234.goblinforest.config.Balance;
import io.github.jan1a234.goblinforest.hero.AbilityType;
import io.github.jan1a234.goblinforest.hero.HeroProgression;
import io.github.jan1a234.goblinforest.net.MatchStatePayload;
import io.github.jan1a234.goblinforest.registry.ModEntities;
import io.github.jan1a234.goblinforest.spell.SpellType;
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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
	private static final Identifier HEALTH_MODIFIER = GoblinForest.id("hero_health");
	private static final Identifier DAMAGE_MODIFIER = GoblinForest.id("hero_damage");
	private static final Identifier ATTACK_SPEED_MODIFIER = GoblinForest.id("hero_attack_speed");
	private static final Identifier KNOCKBACK_MODIFIER = GoblinForest.id("hero_knockback");
	private static final Identifier FREEZE_MODIFIER = GoblinForest.id("freeze");
	private static final Identifier FREEZE_JUMP_MODIFIER = GoblinForest.id("freeze_jump");
	private static final Identifier ROOT_MODIFIER = GoblinForest.id("rooted");
	private static final Identifier ROOT_JUMP_MODIFIER = GoblinForest.id("rooted_jump");
	private static final Identifier RAGE_DAMAGE_MODIFIER = GoblinForest.id("rage_damage");
	private static final Identifier RAGE_SPEED_MODIFIER = GoblinForest.id("rage_speed");
	/** Ab so vielen Ticks ohne Häuptling online verliert ein Clan kampflos. */
	private static final int FORFEIT_TICKS = 20 * 120;
	private static final int STRUCTURE_HIT_COOLDOWN = 12;

	/** Häuptling eines Clans (ein Spieler). */
	static final class Hero {
		final UUID uuid;
		final String name;
		final TeamColor team;
		boolean prepared;
		boolean dead;
		int respawnTicks;
		long lastStructureHit = -100;
		long lastKitUse = -100;
		long lastAttackWarning = -1000;
		long rootedUntil = -1;
		boolean raging;

		Hero(UUID uuid, String name, TeamColor team) {
			this.uuid = uuid;
			this.name = name;
			this.team = team;
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
	private final EnumMap<TeamColor, TeamState> teams = new EnumMap<>(TeamColor.class);
	private final EnumMap<TeamColor, List<ArenaLayout.Point>> waypoints = new EnumMap<>(TeamColor.class);
	private final EnumMap<TeamColor, Vec3> rallyPoints = new EnumMap<>(TeamColor.class);
	private final EnumMap<TeamColor, Integer> coreStages = new EnumMap<>(TeamColor.class);
	private final EnumMap<TeamColor, Integer> towerCooldowns = new EnumMap<>(TeamColor.class);
	private final EnumMap<TeamColor, Integer> cannonCooldowns = new EnumMap<>(TeamColor.class);
	/** Best-of-Serie: gewonnene Runden je Clan und Anzahl der Runden (1 = einzelnes Match). */
	private final EnumMap<TeamColor, Integer> roundWins = new EnumMap<>(TeamColor.class);
	private int bestOf = 1;
	private final EnumMap<TeamColor, Integer> lastReputation = new EnumMap<>(TeamColor.class);
	private final EnumMap<TeamColor, Integer> offlineTicks = new EnumMap<>(TeamColor.class);
	private final EnumMap<TeamColor, PlayerTeam> scoreboardTeams = new EnumMap<>(TeamColor.class);
	private final Map<UUID, Hero> heroes = new LinkedHashMap<>();
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
		this.server = server;
		this.arena = arena;
		this.balance = GoblinForest.balance();
		this.combat = new UnitCombat(balance);
		this.bounty = new Bounty(balance.economy());
		this.practice = practice;
		for (TeamColor team : TeamColor.values()) {
			teams.put(team, new TeamState(team, balance));
			waypoints.put(team, ArenaLayout.laneWaypoints(team));
			coreStages.put(team, 0);
			towerCooldowns.put(team, 0);
			cannonCooldowns.put(team, 0);
			lastReputation.put(team, 0);
			offlineTicks.put(team, 0);
		}
		players.forEach((uuid, team) -> heroes.put(uuid, new Hero(uuid, names.getOrDefault(uuid, "?"), team)));
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

	/** Kann ein Häuptling gerade angegriffen werden (lebt, ist in der Arena, kein Zuschauer)? */
	public boolean isTargetableHero(ServerPlayer player) {
		Hero hero = heroes.get(player.getUUID());
		return hero != null && !hero.dead && player.isAlive() && player.level() == arena
				&& !player.isSpectator() && !player.isCreative();
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

	private List<ServerPlayer> onlineHeroes(TeamColor team) {
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
				setFrozen(player, false);
				title(player, Component.translatable("title.goblinforest.fight").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
						Component.translatable("title.goblinforest.fight_sub"), 0, 30, 15);
				playTo(player, SoundEvents.RAID_HORN.value(), 1.0f, 1.0f);
			}
			return;
		}
		phaseTicks--;
		for (ServerPlayer player : onlineHeroes()) {
			Hero hero = heroes.get(player.getUUID());
			if (hero != null && hero.prepared) {
				ArenaLayout.Point spawn = ArenaLayout.heroSpawn(hero.team);
				if (player.position().distanceToSqr(spawn.x(), spawn.y(), spawn.z()) > 2.5) {
					teleportToSpawn(player, hero.team);
				}
			}
		}
	}

	private void tickBattle() {
		battleTicks++;
		for (TeamState state : teams.values()) {
			state.tickIncome();
		}
		tickTowers();
		tickCannons();
		tickHeroes();
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

	/** Sekunden bis Sudden Death (negativ, sobald er läuft). */
	public int roundWins(TeamColor team) {
		return roundWins.getOrDefault(team, 0);
	}

	public int bestOf() {
		return bestOf;
	}

	/** Sekunden bis zum Sudden Death; 0, sobald er läuft; -1, wenn er abgeschaltet ist. */
	public long secondsUntilSuddenDeath() {
		if (balance.match().suddenDeathMinutes() <= 0) {
			return -1;
		}
		return Math.max(0, (balance.match().suddenDeathTicks() - battleTicks + 19) / 20);
	}

	private void tickSuddenDeath() {
		if (balance.match().suddenDeathMinutes() <= 0) {
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
			finish(null);
			return;
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

	// ================================================================ Häuptlinge

	private void prepareHero(ServerPlayer player, Hero hero) {
		if (hero == null) {
			return;
		}
		if (!PlayerBackup.exists(server, player.getUUID())) {
			PlayerBackup.save(player);
		}
		player.closeContainer();
		player.removeAllEffects();
		player.setGameMode(GameType.ADVENTURE);
		teleportToSpawn(player, hero.team);
		applyHeroStats(player, hero.team, true);
		HeroKit.apply(player, hero.team);
		giveHeroEffects(player);
		joinScoreboardTeam(player, hero.team);
		feed(player);
		updateXpBar(player, hero.team);
		hero.prepared = true;
		hero.dead = false;
		setFrozen(player, phase == MatchPhase.COUNTDOWN || phase == MatchPhase.SETUP);
		player.sendSystemMessage(Component.translatable("message.goblinforest.welcome",
				Component.translatable(hero.team.translationKey()).withColor(hero.team.rgb())).withStyle(ChatFormatting.GOLD));
		player.sendSystemMessage(Component.translatable("message.goblinforest.controls").withStyle(ChatFormatting.GRAY));
	}

	private void giveHeroEffects(ServerPlayer player) {
		player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, -1, 0, true, false, false));
	}

	private void feed(ServerPlayer player) {
		// 17 Hungerpunkte: Sprinten geht, natürliche Regeneration nicht. Geheilt wird in der eigenen Festung.
		player.getFoodData().setFoodLevel(17);
		player.getFoodData().setSaturation(0);
	}

	private void teleportToSpawn(ServerPlayer player, TeamColor team) {
		ArenaLayout.Point spawn = ArenaLayout.heroSpawn(team);
		player.teleport(new TeleportTransition(arena, new Vec3(spawn.x(), spawn.y(), spawn.z()), Vec3.ZERO,
				ArenaLayout.facingYaw(team), 0, TeleportTransition.DO_NOTHING));
	}

	private void applyHeroStats(ServerPlayer player, TeamColor team, boolean fullHeal) {
		HeroProgression progression = teams.get(team).heroProgression();
		int level = teams.get(team).heroLevel();
		double oldMax = player.getMaxHealth();
		setModifier(player, Attributes.MAX_HEALTH, HEALTH_MODIFIER, progression.maxHealth(level) - 20.0, AttributeModifier.Operation.ADD_VALUE);
		setModifier(player, Attributes.ATTACK_DAMAGE, DAMAGE_MODIFIER, progression.damage(level) - 1.0, AttributeModifier.Operation.ADD_VALUE);
		setModifier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_MODIFIER, -2.4, AttributeModifier.Operation.ADD_VALUE);
		setModifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_MODIFIER, 0.5, AttributeModifier.Operation.ADD_VALUE);
		if (fullHeal) {
			player.setHealth(player.getMaxHealth());
		} else {
			player.heal((float) Math.max(0, player.getMaxHealth() - oldMax));
		}
	}

	private static void setModifier(LivingEntity entity, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
			Identifier modifierId, double value, AttributeModifier.Operation operation) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance != null) {
			instance.addOrUpdateTransientModifier(new AttributeModifier(modifierId, value, operation));
		}
	}

	private static void removeModifier(LivingEntity entity, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
			Identifier modifierId) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance != null) {
			instance.removeModifier(modifierId);
		}
	}

	private void setFrozen(ServerPlayer player, boolean frozen) {
		if (frozen) {
			setModifier(player, Attributes.MOVEMENT_SPEED, FREEZE_MODIFIER, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			setModifier(player, Attributes.JUMP_STRENGTH, FREEZE_JUMP_MODIFIER, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		} else {
			removeModifier(player, Attributes.MOVEMENT_SPEED, FREEZE_MODIFIER);
			removeModifier(player, Attributes.JUMP_STRENGTH, FREEZE_JUMP_MODIFIER);
		}
	}

	private void updateXpBar(ServerPlayer player, TeamColor team) {
		TeamState state = teams.get(team);
		player.experienceLevel = state.heroLevel();
		player.experienceProgress = (float) state.heroProgression().progressToNext(state.heroXp());
		// Eine sich ändernde Gesamtzahl löst das Senden an den Client aus.
		player.totalExperience = (int) Math.round(state.heroXp() * 10) + state.heroLevel();
	}

	private void restoreHero(ServerPlayer player) {
		removeModifier(player, Attributes.MAX_HEALTH, HEALTH_MODIFIER);
		removeModifier(player, Attributes.ATTACK_DAMAGE, DAMAGE_MODIFIER);
		removeModifier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_MODIFIER);
		removeModifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_MODIFIER);
		removeModifier(player, Attributes.MOVEMENT_SPEED, ROOT_MODIFIER);
		removeModifier(player, Attributes.JUMP_STRENGTH, ROOT_JUMP_MODIFIER);
		removeModifier(player, Attributes.ATTACK_DAMAGE, RAGE_DAMAGE_MODIFIER);
		removeModifier(player, Attributes.MOVEMENT_SPEED, RAGE_SPEED_MODIFIER);
		setFrozen(player, false);
		server.getScoreboard().removePlayerFromTeam(player.getScoreboardName());
		player.removeAllEffects();
		player.closeContainer();
		PlayerBackup.restore(player);
		ServerPlayNetworking.send(player, MatchStatePayload.none());
	}

	private void tickHeroes() {
		for (ServerPlayer player : onlineHeroes()) {
			Hero hero = heroes.get(player.getUUID());
			if (!hero.prepared) {
				continue;
			}
			if (hero.dead) {
				if (hero.respawnTicks % 20 == 0 && hero.respawnTicks > 0) {
					player.sendOverlayMessage(Component.translatable("message.goblinforest.respawn_in", hero.respawnTicks / 20).withStyle(ChatFormatting.GRAY));
				}
				if (--hero.respawnTicks <= 0) {
					respawnHero(player, hero);
				}
				continue;
			}
			if (tick % 10 == 0) {
				HeroKit.enforce(player, hero.team);
			}
			tickRage(player, hero);
			if (hero.rootedUntil >= 0 && tick >= hero.rootedUntil) {
				clearRoot(player, hero);
			} else if (hero.rootedUntil >= 0 && tick % 8 == 0) {
				arena.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.defaultBlockState()),
						player.getX(), player.getY() + 0.2, player.getZ(), 6, 0.3, 0.1, 0.3, 0.05);
			}
			if (tick % 20 == 0) {
				feed(player);
				ArenaLayout.Box zone = ArenaLayout.healZone(hero.team);
				BlockPos pos = player.blockPosition();
				if (zone.contains(pos.getX(), pos.getY(), pos.getZ()) && player.getHealth() < player.getMaxHealth()) {
					player.heal(player.getMaxHealth() * 0.03f);
					arena.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 2.2, player.getZ(), 1, 0.2, 0.1, 0.2, 0);
				}
				if (!ArenaLayout.insideArena(player.getX(), player.getZ()) || player.getY() < ArenaLayout.GROUND_Y - 8
						|| player.level() != arena) {
					teleportToSpawn(player, hero.team);
				}
			}
		}
	}

	private void respawnHero(ServerPlayer player, Hero hero) {
		hero.dead = false;
		player.setGameMode(GameType.ADVENTURE);
		teleportToSpawn(player, hero.team);
		applyHeroStats(player, hero.team, true);
		HeroKit.enforce(player, hero.team);
		giveHeroEffects(player);
		feed(player);
		title(player, Component.translatable("title.goblinforest.respawned").withStyle(ChatFormatting.GREEN), Component.empty(), 5, 30, 10);
		arena.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(), 30, 0.4, 0.8, 0.4, 0.3);
	}

	private void addHeroXp(TeamColor team, double amount) {
		TeamState state = teams.get(team);
		boolean levelUp = state.addHeroXp(amount);
		for (ServerPlayer player : onlineHeroes(team)) {
			if (levelUp) {
				applyHeroStats(player, team, false);
				title(player, Component.translatable("title.goblinforest.hero_level", state.heroLevel()).withStyle(ChatFormatting.GOLD),
						Component.translatable("title.goblinforest.hero_level_sub",
								(int) state.heroProgression().maxHealth(state.heroLevel()),
								(int) state.heroProgression().damage(state.heroLevel())), 5, 40, 10);
				playTo(player, SoundEvents.PLAYER_LEVELUP, 1.0f, 0.8f);
				arena.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(), 40, 0.4, 1.0, 0.4, 0.4);
			}
			updateXpBar(player, team);
		}
	}

	/** Häuptling stirbt: statt echtem Tod Zuschauermodus bis zur Wiederbelebung. Gibt false zurück (Tod abbrechen). */
	public boolean onHeroDeath(ServerPlayer player, DamageSource source) {
		Hero hero = heroes.get(player.getUUID());
		if (hero == null) {
			return true;
		}
		player.setHealth(player.getMaxHealth());
		player.clearFire();
		if (phase != MatchPhase.BATTLE || hero.dead) {
			return false;
		}
		TeamColor enemy = hero.team.opponent();
		teams.get(hero.team).stats().heroDeaths++;
		teams.get(enemy).stats().heroKills++;
		teams.get(enemy).addGold(bounty.heroKillBounty());
		teams.get(enemy).addClanXp(bounty.heroKillClanXp());
		Entity killer = source.getEntity();
		boolean byHero = killer instanceof ServerPlayer;
		addHeroXp(enemy, byHero ? bounty.heroKillClanXp() : bounty.heroKillClanXp() * teams.get(enemy).heroProgression().armyXpShare());
		if (killer instanceof GoblinUnit unit && owns(unit)) {
			unit.addXp(combat.leveling().xpForKill() * 3);
			unit.onKilledEnemy(arena, this);
		}
		if (hero.raging) {
			endRage(player, hero);
		}
		clearRoot(player, hero);
		arena.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1, player.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
		arena.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.4f, 1.6f);
		hero.dead = true;
		int level = teams.get(hero.team).heroLevel();
		hero.respawnTicks = teams.get(hero.team).heroProgression().respawnSeconds(level) * 20;
		player.removeAllEffects();
		giveHeroEffects(player);
		player.setGameMode(GameType.SPECTATOR);
		title(player, Component.translatable("title.goblinforest.died").withStyle(ChatFormatting.DARK_RED),
				Component.translatable("title.goblinforest.died_sub", hero.respawnTicks / 20), 5, 50, 10);
		broadcast(Component.translatable("message.goblinforest.hero_killed",
				Component.literal(hero.name).withColor(hero.team.rgb()),
				bounty.heroKillBounty(),
				Component.translatable(enemy.translationKey()).withColor(enemy.rgb())));
		return false;
	}

	/** Darf ein Häuptling gerade Schaden nehmen? Kein Schaden außerhalb des Kampfes und durch Verbündete. */
	public boolean allowHeroDamage(ServerPlayer player, DamageSource source) {
		Hero hero = heroes.get(player.getUUID());
		if (hero == null) {
			return true;
		}
		if (phase != MatchPhase.BATTLE || hero.dead) {
			return false;
		}
		TeamColor attacker = teamOf(source.getEntity());
		return attacker != hero.team;
	}

	/** Nach erlittenem Schaden: lädt die Raserei des Opfers; ein angreifender Häuptling bekommt Ladung bzw. Lebensraub. */
	public void onHeroDamaged(ServerPlayer player, DamageSource source, float amount) {
		Hero hero = heroes.get(player.getUUID());
		if (hero == null || hero.dead || phase != MatchPhase.BATTLE || amount <= 0) {
			return;
		}
		if (!teams.get(hero.team).rageActive(tick)) {
			onHeroTookDamage(player, hero, amount);
		}
		if (source.getEntity() instanceof ServerPlayer attacker && attacker != player) {
			onHeroDealtDamage(attacker, amount);
		}
	}

	private void clearRoot(ServerPlayer player, Hero hero) {
		hero.rootedUntil = -1;
		removeModifier(player, Attributes.MOVEMENT_SPEED, ROOT_MODIFIER);
		removeModifier(player, Attributes.JUMP_STRENGTH, ROOT_JUMP_MODIFIER);
	}

	public void onPlayerJoin(ServerPlayer player) {
		Hero hero = heroes.get(player.getUUID());
		if (hero == null) {
			return;
		}
		if (phase == MatchPhase.SETUP) {
			return;
		}
		if (phase == MatchPhase.ENDED) {
			restoreHero(player);
			return;
		}
		boolean wasDead = hero.dead;
		prepareHero(player, hero);
		if (wasDead) {
			hero.dead = true;
			player.setGameMode(GameType.SPECTATOR);
		}
	}

	// ================================================================ Einheiten

	private PurchaseResult recruit(TeamColor team, UnitType type) {
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
			boolean byHero = killer instanceof ServerPlayer;
			addHeroXp(enemy, byHero ? clanXp : clanXp * state.heroProgression().armyXpShare());
			if (killer instanceof GoblinUnit killerUnit && owns(killerUnit)) {
				killerUnit.addXp(combat.leveling().xpForKill());
				killerUnit.onKilledEnemy(arena, this);
			}
			if (killer instanceof ServerPlayer player) {
				player.sendOverlayMessage(Component.translatable("message.goblinforest.bounty", gold).withStyle(ChatFormatting.GOLD));
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
		for (UnitType type : UnitType.values()) {
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

	/** Gegner, den Flächenschaden treffen darf: feindliche Einheiten (auch getarnt) und angreifbare feindliche Häuptlinge. */
	private boolean isDamageableEnemy(TeamColor team, LivingEntity entity) {
		if (!entity.isAlive()) {
			return false;
		}
		if (entity instanceof GoblinUnit unit) {
			return owns(unit) && unit.team() != team;
		}
		if (entity instanceof ServerPlayer player) {
			return teamOf(player.getUUID()) == team.opponent() && isTargetableHero(player);
		}
		return false;
	}

	/** Schaden im Namen eines Clans: Einheiten merken sich den Clan für Kopfgeld, Häuptlinge ohne Unverwundbarkeitspause. */
	private void hurtAsTeam(LivingEntity entity, DamageSource source, double damage, TeamColor team) {
		if (entity instanceof GoblinUnit unit) {
			unit.hurtByTeam(arena, source, (float) damage, team);
		} else {
			entity.invulnerableTime = 0;
			entity.hurtServer(arena, source, (float) damage);
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
		if (!entity.isAlive()) {
			return false;
		}
		if (entity instanceof GoblinUnit unit) {
			return owns(unit) && unit.team() != team && !unit.isStealthed();
		}
		if (entity instanceof ServerPlayer player) {
			return teamOf(player.getUUID()) == team.opponent() && isTargetableHero(player);
		}
		return false;
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
		if (target instanceof GoblinUnit unit) {
			unit.hurtByTeam(arena, arena.damageSources().magic(), (float) damage, team);
		} else {
			target.invulnerableTime = 0;
			target.hurtServer(arena, arena.damageSources().magic(), (float) damage);
		}
	}

	/** Schaden an Turm oder Festungskern, z. B. durch Einheiten oder den Häuptling. */
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

	/** Aktion aus dem Netzwerk (Kriegsmenü, Schnelltasten) oder aus der Hotbar. */
	public void handleAction(ServerPlayer player, String action) {
		Hero hero = heroes.get(player.getUUID());
		if (hero == null || !hero.prepared || phase == MatchPhase.SETUP || phase == MatchPhase.ENDED) {
			return;
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
				if (spell != null) {
					castSpell(player, hero, spell);
				}
			}
			case "ability" -> {
				AbilityType ability = AbilityType.byId(arg);
				if (ability != null) {
					useAbility(player, hero, ability);
				}
			}
			case "ability_rank" -> {
				AbilityType ability = AbilityType.byId(arg);
				if (ability != null) {
					PurchaseResult result = state.buyAbilityRank(ability);
					feedback(player, result, Component.translatable("message.goblinforest.ability_ranked",
							Component.translatable(ability.translationKey()), state.abilityRank(ability)));
					if (result.ok() && ability == AbilityType.RAGE && hero.raging) {
						applyRageModifiers(player, hero.team);
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
			case "rally" -> setRally(player, hero);
			case "hit_structure" -> heroStructureHit(player, hero);
			case "menu" -> ServerPlayNetworking.send(player, io.github.jan1a234.goblinforest.net.OpenMenuPayload.INSTANCE);
			default -> {
			}
		}
	}

	/** Rechtsklick mit einem Ausrüstungsgegenstand. Gibt true zurück, wenn er zur Ausrüstung gehört. */
	public boolean handleKitUse(ServerPlayer player, String kitId) {
		Hero hero = heroes.get(player.getUUID());
		if (hero == null) {
			return false;
		}
		if (tick - hero.lastKitUse < 4) {
			return true;
		}
		hero.lastKitUse = tick;
		HeroKit.Slot slot = HeroKit.Slot.byId(kitId);
		if (slot != null && !slot.action().isEmpty()) {
			handleAction(player, slot.action());
		}
		return true;
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

	private void setStance(TeamColor team, Stance stance) {
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

	private void setRally(ServerPlayer player, Hero hero) {
		if (phase != MatchPhase.BATTLE || hero.dead) {
			return;
		}
		Vec3 target = aimPoint(player, 40);
		BlockPos ground = BlockPos.containing(target.x, target.y, target.z);
		Vec3 point = new Vec3(ground.getX() + 0.5, ArenaLayout.GROUND_Y + 1, ground.getZ() + 0.5);
		if (!ArenaLayout.insideArena(point.x, point.z)) {
			return;
		}
		rallyPoints.put(hero.team, point);
		setStance(hero.team, Stance.HOLD);
		arena.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 0.5f, 1.4f);
	}

	private void drawRallyMarkers() {
		rallyPoints.forEach((team, point) -> {
			DustParticleOptions dust = new DustParticleOptions(team.rgb(), 1.5f);
			for (int i = 0; i < 6; i++) {
				arena.sendParticles(dust, point.x, point.y + i * 0.5, point.z, 1, 0.05, 0.05, 0.05, 0);
			}
		});
	}

	private void heroStructureHit(ServerPlayer player, Hero hero) {
		if (phase != MatchPhase.BATTLE || hero.dead || tick - hero.lastStructureHit < STRUCTURE_HIT_COOLDOWN) {
			return;
		}
		net.minecraft.world.phys.HitResult hit = player.pick(5.0, 1.0f, false);
		if (!(hit instanceof net.minecraft.world.phys.BlockHitResult blockHit) || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
			return;
		}
		BlockPos pos = blockHit.getBlockPos();
		TeamColor enemy = hero.team.opponent();
		Structure structure = null;
		if (expanded(ArenaLayout.towerBox(enemy)).contains(pos.getX(), pos.getY(), pos.getZ()) && teams.get(enemy).towerAlive()) {
			structure = Structure.TOWER;
		} else if (expanded(ArenaLayout.coreBox(enemy)).contains(pos.getX(), pos.getY(), pos.getZ())) {
			structure = Structure.CORE;
		}
		if (structure == null) {
			return;
		}
		hero.lastStructureHit = tick;
		TeamState state = teams.get(hero.team);
		double damage = state.heroProgression().damage(state.heroLevel()) * state.heroDamageMultiplier(tick);
		Vec3 location = hit.getLocation();
		arena.sendParticles(ParticleTypes.CRIT, location.x, location.y, location.z, 8, 0.2, 0.2, 0.2, 0.2);
		arena.playSound(null, location.x, location.y, location.z, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.PLAYERS, 0.5f, 1.2f);
		damageStructure(enemy, structure, damage, player, hero.team);
	}

	private static ArenaLayout.Box expanded(ArenaLayout.Box box) {
		return new ArenaLayout.Box(box.minX() - 1, box.minY(), box.minZ() - 1, box.maxX() + 1, box.maxY() + 4, box.maxZ() + 1);
	}

	/** Punkt, auf den der Spieler zielt: erster Block oder erstes Lebewesen auf der Blicklinie. */
	private Vec3 aimPoint(ServerPlayer player, double range) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		for (double d = 1.0; d <= range; d += 0.5) {
			Vec3 point = eye.add(look.scale(d));
			BlockPos pos = BlockPos.containing(point.x, point.y, point.z);
			if (!arena.getBlockState(pos).isAir()) {
				return eye.add(look.scale(d - 0.5));
			}
			AABB probe = new AABB(point.x - 0.5, point.y - 0.5, point.z - 0.5, point.x + 0.5, point.y + 0.5, point.z + 0.5);
			if (!arena.getEntitiesOfClass(LivingEntity.class, probe, e -> e != player && e.isAlive() && !e.isSpectator()).isEmpty()) {
				return point;
			}
		}
		return eye.add(look.scale(range));
	}

	private void castSpell(ServerPlayer player, Hero hero, SpellType spell) {
		if (phase != MatchPhase.BATTLE) {
			return;
		}
		if (hero.dead) {
			feedback(player, PurchaseResult.HERO_DEAD, Component.empty());
			return;
		}
		TeamState state = teams.get(hero.team);
		PurchaseResult result = state.checkCast(spell, tick);
		if (!result.ok()) {
			feedback(player, result, Component.empty());
			return;
		}
		Balance.Spell config = balance.spell(spell.id());
		state.payCast(spell, tick);
		player.getCooldowns().addCooldown(HeroKit.create(HeroKit.Slot.forAction(spell.id())), config.cooldownSeconds() * 20);
		Vec3 target = aimPoint(player, config.range());
		player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
		Vec3 origin = player.getEyePosition().add(player.getLookAngle().scale(0.8));
		releaseSpell(hero.team, spell, origin, target, player);
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
			if (entity instanceof ServerPlayer player && !isTargetableHero(player)) {
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
			} else if (entity instanceof ServerPlayer player) {
				rootHero(player, ticks);
			}
		}
	}

	private void rootHero(ServerPlayer player, int ticks) {
		Hero hero = heroes.get(player.getUUID());
		if (hero == null) {
			return;
		}
		hero.rootedUntil = Math.max(hero.rootedUntil, tick + ticks);
		setModifier(player, Attributes.MOVEMENT_SPEED, ROOT_MODIFIER, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		setModifier(player, Attributes.JUMP_STRENGTH, ROOT_JUMP_MODIFIER, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		player.sendOverlayMessage(Component.translatable("message.goblinforest.rooted").withStyle(ChatFormatting.DARK_GREEN));
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
		arena.sendParticles(ParticleTypes.FLASH, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
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

	private void useAbility(ServerPlayer player, Hero hero, AbilityType ability) {
		if (phase != MatchPhase.BATTLE) {
			return;
		}
		if (hero.dead) {
			feedback(player, PurchaseResult.HERO_DEAD, Component.empty());
			return;
		}
		TeamState state = teams.get(hero.team);
		PurchaseResult result = state.checkAbility(ability, tick);
		if (!result.ok()) {
			feedback(player, result, Component.empty());
			return;
		}
		state.startAbilityCooldown(ability, tick);
		Balance.Ability config = balance.ability(ability.id());
		int rank = state.abilityRank(ability);
		if (!ability.charged()) {
			player.getCooldowns().addCooldown(HeroKit.create(HeroKit.Slot.forAction(ability.id())), config.cooldownTicksAt(rank));
		}
		player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
		double radius = config.radiusAt(rank);
		AABB box = player.getBoundingBox().inflate(radius, 3, radius);
		switch (ability) {
			case BLOODLUST -> {
				int affected = 0;
				for (GoblinUnit unit : arena.getEntitiesOfClass(GoblinUnit.class, box, u -> owns(u) && u.team() == hero.team && u.isAlive())) {
					if (unit.distanceTo(player) <= radius) {
						unit.applyBloodlust(config.durationTicksAt(rank), config.amountAt(rank));
						arena.sendParticles(ParticleTypes.ANGRY_VILLAGER, unit.getX(), unit.getY() + 2.2, unit.getZ(), 1, 0.1, 0.1, 0.1, 0);
						affected++;
					}
				}
				DustParticleOptions dust = new DustParticleOptions(0xD0201A, 1.6f);
				for (int i = 0; i < 48; i++) {
					double angle = Math.PI * 2 * i / 48;
					arena.sendParticles(dust, player.getX() + Math.cos(angle) * radius, player.getY() + 0.3, player.getZ() + Math.sin(angle) * radius, 1, 0, 0.1, 0, 0);
				}
				arena.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.8f, 1.5f);
				player.sendOverlayMessage(Component.translatable("message.goblinforest.bloodlust", affected).withStyle(ChatFormatting.RED));
			}
			case BATTLE_SLAM -> {
				double damage = config.amountAt(rank) * state.heroDamageMultiplier(tick);
				for (LivingEntity entity : arena.getEntitiesOfClass(LivingEntity.class, box, e -> isDamageableEnemy(hero.team, e))) {
					if (entity.distanceTo(player) > radius) {
						continue;
					}
					hurtAsTeam(entity, arena.damageSources().playerAttack(player), damage, hero.team);
					Knockback.shove(entity, entity.getX() - player.getX(), entity.getZ() - player.getZ(), 1.3);
				}
				arena.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 0.2, player.getZ(), 3, 1, 0.1, 1, 0);
				arena.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
						player.getX(), player.getY() + 0.1, player.getZ(), 80, radius * 0.5, 0.1, radius * 0.5, 0.3);
				arena.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.0f, 0.8f);
				arena.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6f, 1.4f);
			}
			case RAGE -> startRage(player, hero);
		}
	}

	// ================================================================ Raserei

	private void startRage(ServerPlayer player, Hero hero) {
		TeamState state = teams.get(hero.team);
		hero.raging = true;
		applyRageModifiers(player, hero.team);
		title(player, Component.empty(), Component.translatable("title.goblinforest.rage").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), 0, 30, 10);
		arena.sendParticles(ParticleTypes.ANGRY_VILLAGER, player.getX(), player.getY() + 2.2, player.getZ(), 6, 0.4, 0.3, 0.4, 0);
		arena.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1, player.getZ(), 40, 0.5, 0.8, 0.5, 0.08);
		arena.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.2f, 0.8f);
		arena.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 0.4f, 1.6f);
		announceToTeam(hero.team.opponent(), null, Component.translatable("message.goblinforest.enemy_rage").withStyle(ChatFormatting.RED));
		player.sendOverlayMessage(Component.translatable("message.goblinforest.rage_started",
				state.rageRemaining(tick) / 20).withStyle(ChatFormatting.DARK_RED));
	}

	private void applyRageModifiers(ServerPlayer player, TeamColor team) {
		TeamState state = teams.get(team);
		setModifier(player, Attributes.ATTACK_DAMAGE, RAGE_DAMAGE_MODIFIER, state.heroDamageMultiplier(tick) - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		setModifier(player, Attributes.MOVEMENT_SPEED, RAGE_SPEED_MODIFIER, balance.rage().speedBonus(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	}

	private void endRage(ServerPlayer player, Hero hero) {
		hero.raging = false;
		teams.get(hero.team).endRage();
		removeModifier(player, Attributes.ATTACK_DAMAGE, RAGE_DAMAGE_MODIFIER);
		removeModifier(player, Attributes.MOVEMENT_SPEED, RAGE_SPEED_MODIFIER);
	}

	private void tickRage(ServerPlayer player, Hero hero) {
		if (!hero.raging) {
			return;
		}
		TeamState state = teams.get(hero.team);
		if (!state.rageActive(tick)) {
			endRage(player, hero);
			player.sendOverlayMessage(Component.translatable("message.goblinforest.rage_ended").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (tick % 4 == 0) {
			arena.sendParticles(new DustParticleOptions(0xB01010, 1.4f), player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.35, 0.6, 0.35, 0);
			arena.sendParticles(ParticleTypes.SMALL_FLAME, player.getX(), player.getY() + 0.2, player.getZ(), 1, 0.3, 0.1, 0.3, 0.01);
		}
	}

	/** Der Häuptling hat Schaden ausgeteilt: Raserei laden, während der Raserei Lebensraub. */
	public void onHeroDealtDamage(ServerPlayer player, double amount) {
		Hero hero = heroes.get(player.getUUID());
		if (hero == null || hero.dead || phase != MatchPhase.BATTLE || amount <= 0) {
			return;
		}
		TeamState state = teams.get(hero.team);
		if (state.rageActive(tick)) {
			float heal = (float) (amount * state.rageLifesteal());
			if (heal > 0 && player.getHealth() < player.getMaxHealth()) {
				player.heal(heal);
				arena.sendParticles(new DustParticleOptions(0x9C0A0A, 1.0f), player.getX(), player.getY() + 1.2, player.getZ(), 4, 0.3, 0.4, 0.3, 0);
			}
		} else {
			addRage(player, hero, amount * balance.rage().chargePerDamageDealt());
		}
	}

	/** Der Häuptling hat Schaden eingesteckt: lädt die Raserei. */
	private void onHeroTookDamage(ServerPlayer player, Hero hero, double amount) {
		addRage(player, hero, amount * balance.rage().chargePerDamageTaken());
	}

	private void addRage(ServerPlayer player, Hero hero, double amount) {
		TeamState state = teams.get(hero.team);
		boolean wasFull = state.rageCharge() >= state.rageMax();
		state.addRage(amount, tick);
		if (!wasFull && state.rageCharge() >= state.rageMax()) {
			player.sendOverlayMessage(Component.translatable("message.goblinforest.rage_ready",
					Component.keybind("key.goblinforest.rage")).withStyle(ChatFormatting.GOLD));
			playTo(player, SoundEvents.PIGLIN_BRUTE_ANGRY, 0.8f, 1.2f);
		}
	}

	// ================================================================ Ende

	private void checkForfeit() {
		if (practice) {
			return;
		}
		for (TeamColor team : TeamColor.values()) {
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
		TeamColor loser = winningTeam.opponent();
		if (teams.get(loser).coreDestroyed()) {
			coreStages.put(loser, 4);
			ArenaBuilder.apply(arena, ArenaBlueprint.coreBlocks(loser, 4));
		}
		for (ServerPlayer player : onlineHeroes()) {
			Hero hero = heroes.get(player.getUUID());
			boolean won = hero.team == winningTeam;
			if (hero.dead) {
				respawnHero(player, hero);
			}
			title(player, Component.translatable(won ? "title.goblinforest.victory" : "title.goblinforest.defeat")
							.withStyle(won ? ChatFormatting.GOLD : ChatFormatting.DARK_RED, ChatFormatting.BOLD),
					Component.translatable("title.goblinforest.winner", Component.translatable(winningTeam.translationKey()).withColor(winningTeam.rgb())),
					10, 100, 20);
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
		for (GoblinUnit unit : units) {
			unit.discard();
		}
		units.clear();
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

	private void playTo(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.MASTER, volume, pitch);
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
		for (UnitType type : UnitType.values()) {
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
		int phaseSeconds = switch (phase) {
			case COUNTDOWN, ENDED -> (phaseTicks + 19) / 20;
			case SETUP -> builder == null ? 0 : (int) Math.round(builder.progress() * 100);
			default -> 0;
		};
		return new MatchStatePayload(
				phase.ordinal(), team.ordinal(), phaseSeconds, (int) (battleTicks / 20),
				state.gold(), state.reputation(), (float) state.reputationProgress(),
				state.population(), state.populationLimit(), state.stance().ordinal(),
				state.heroLevel(), (float) state.heroProgression().progressToNext(state.heroXp()), hero.dead ? (hero.respawnTicks + 19) / 20 : 0,
				new float[] {(float) teams.get(TeamColor.RED).coreHealth(), (float) teams.get(TeamColor.GREEN).coreHealth()},
				new float[] {(float) teams.get(TeamColor.RED).coreMaxHealth(), (float) teams.get(TeamColor.GREEN).coreMaxHealth()},
				new float[] {(float) teams.get(TeamColor.RED).towerHealth(), (float) teams.get(TeamColor.GREEN).towerHealth()},
				new float[] {(float) teams.get(TeamColor.RED).towerMaxHealth(), (float) teams.get(TeamColor.GREEN).towerMaxHealth()},
				unitCounts, enemyUnits, enemyState.heroLevel(), enemyState.reputation(), rallyPoints.containsKey(team),
				shop, cooldowns,
				state.abilityPoints(), (float) (state.rageCharge() / state.rageMax()), (int) ((state.rageRemaining(tick) + 19) / 20),
				phase == MatchPhase.BATTLE ? (int) secondsUntilSuddenDeath() : -1, (float) state.incomePerSecond(),
				new int[] {roundWins(TeamColor.RED), roundWins(TeamColor.GREEN)}, bestOf());
	}

	/** Zeilen für {@code /gf status}. */
	public List<Component> statusLines() {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("command.goblinforest.status.phase", Component.translatable("phase.goblinforest." + phase.name().toLowerCase()),
				formatTime(battleTicks)));
		for (TeamColor team : TeamColor.values()) {
			TeamState state = teams.get(team);
			List<String> names = new ArrayList<>();
			for (Hero hero : heroes.values()) {
				if (hero.team == team) {
					names.add(hero.name);
				}
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

	public int unitCount() {
		return units.size();
	}
}
