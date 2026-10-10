package io.github.jan1a234.goblinforest.game;

import io.github.jan1a234.goblinforest.GoblinForest;
import io.github.jan1a234.goblinforest.net.ActionPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Verwaltet Lobby und das (eine) laufende Match des Servers und verdrahtet alle Server-Ereignisse.
 * Pro Server gibt es höchstens ein Match, weil es genau eine Arena gibt.
 */
public final class MatchManager {
	public static final ResourceKey<Level> ARENA = ResourceKey.create(Registries.DIMENSION, GoblinForest.id("arena"));

	private static MinecraftServer server;
	private static Match match;
	/** Angemeldete Spieler mit Wunsch-Clan (null = egal). */
	private static final Map<UUID, TeamColor> LOBBY = new LinkedHashMap<>();

	private MatchManager() {
	}

	public static Match match() {
		return match;
	}

	public static Map<UUID, TeamColor> lobby() {
		return LOBBY;
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
			if (match != null) {
				match.finish(Component.translatable("message.goblinforest.server_stopping").withStyle(ChatFormatting.RED));
				match = null;
			}
			LOBBY.clear();
			server = null;
		});
		ServerTickEvents.END_SERVER_TICK.register(MatchManager::tick);

		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (match != null && entity instanceof ServerPlayer player && match.isMember(player.getUUID())) {
				return match.allowHeroDamage(player, source);
			}
			return true;
		});
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (match != null && entity instanceof ServerPlayer player && match.isMember(player.getUUID())) {
				return match.onHeroDeath(player, source);
			}
			return true;
		});

		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			// Türen, Truhen und Co. in der Arena sind Kulisse.
			return !isInArena(level) || player.isCreative() ? InteractionResult.PASS : InteractionResult.FAIL;
		});
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> !isInArena(level) || player.isCreative());

		ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> onJoin(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> LOBBY.remove(handler.player.getUUID()));

		ServerPlayNetworking.registerGlobalReceiver(ActionPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (match != null && match.isMember(player.getUUID()) && payload.action().length() <= 64) {
				match.handleAction(player, payload.action());
			}
		});
	}

	private static boolean isInArena(Level level) {
		return level.dimension() == ARENA;
	}

	private static void tick(MinecraftServer s) {
		if (match == null) {
			return;
		}
		try {
			match.tick();
		} catch (RuntimeException e) {
			GoblinForest.LOGGER.error("Fehler im Match, breche ab", e);
			Match failed = match;
			match = null;
			failed.finish(Component.translatable("message.goblinforest.match_error").withStyle(ChatFormatting.RED));
			return;
		}
		if (match.finished()) {
			Match ended = match;
			match = null;
			if (ended.continuesSeries()) {
				match = new Match(s, ended.arena(), ended.assignment(), ended.names(), ended.options());
				GoblinForest.LOGGER.info("Goblin Forest: Runde {} der Serie beginnt", ended.options().series().round());
			}
		}
	}

	private static void onJoin(ServerPlayer player) {
		if (match != null && match.isMember(player.getUUID())) {
			match.onPlayerJoin(player);
			return;
		}
		boolean inArena = player.level().dimension() == ARENA;
		if (PlayerBackup.exists(player.level().getServer(), player.getUUID())) {
			// Match ist vorbei oder der Server ist abgestürzt: alles zurückgeben.
			cleanUpPlayer(player);
			PlayerBackup.restore(player);
			player.sendSystemMessage(Component.translatable("message.goblinforest.restored").withStyle(ChatFormatting.GRAY));
		} else if (inArena && !player.isCreative() && !player.isSpectator()) {
			cleanUpPlayer(player);
			PlayerBackup.sendHome(player);
		}
	}

	private static void cleanUpPlayer(ServerPlayer player) {
		player.removeAllEffects();
		player.level().getServer().getScoreboard().removePlayerFromTeam(player.getScoreboardName());
	}

	// ================================================================ Befehle

	/** Ergebnis eines Befehls: Text und Erfolg. */
	public record Result(Component message, boolean success) {
		static Result ok(Component message) {
			return new Result(message, true);
		}

		static Result fail(String key, Object... args) {
			return new Result(Component.translatable(key, args), false);
		}
	}

	public static Result join(ServerPlayer player, TeamColor preference) {
		if (match != null) {
			return Result.fail("command.goblinforest.match_running");
		}
		LOBBY.put(player.getUUID(), preference);
		Component team = preference == null ? Component.translatable("team.goblinforest.any")
				: Component.translatable(preference.translationKey()).withColor(preference.rgb());
		Component announcement = Component.translatable("command.goblinforest.joined", player.getDisplayName(), team, LOBBY.size());
		for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
			if (other != player) {
				other.sendSystemMessage(Component.empty().append(announcement).withStyle(ChatFormatting.GRAY));
			}
		}
		return Result.ok(announcement);
	}

	public static Result leave(ServerPlayer player) {
		if (LOBBY.remove(player.getUUID()) == null) {
			return Result.fail("command.goblinforest.not_in_lobby");
		}
		return Result.ok(Component.translatable("command.goblinforest.left"));
	}

	/**
	 * Startet ein Match mit allen angemeldeten Spielern. {@code practice} erlaubt einen leeren Clan
	 * (Übungsmodus zum Ausprobieren alleine, ohne Aufgabe-Regel).
	 */
	public static Result start(MinecraftServer s, ServerPlayer starter, boolean practice) {
		return start(s, starter, practice, null, 1, false);
	}

	/**
	 * Startet ein Match mit allen angemeldeten Spielern.
	 *
	 * @param ai     Schwierigkeit eines KI-Gegners oder null; mit KI spielen alle Angemeldeten in einem Clan
	 * @param bestOf      Anzahl der Runden einer Serie (1, 3 oder 5)
	 * @param suddenDeath Sudden Death einschalten (sonst kein Zeitlimit)
	 */
	public static Result start(MinecraftServer s, ServerPlayer starter, boolean practice, AiDifficulty ai, int bestOf, boolean suddenDeath) {
		if (match != null) {
			return Result.fail("command.goblinforest.match_running");
		}
		if (starter != null && !LOBBY.containsKey(starter.getUUID())) {
			LOBBY.put(starter.getUUID(), null);
		}
		ServerLevel arena = s.getLevel(ARENA);
		if (arena == null) {
			return Result.fail("command.goblinforest.no_arena");
		}
		Map<UUID, TeamColor> assignment = new LinkedHashMap<>();
		Map<UUID, String> names = new HashMap<>();
		List<UUID> flexible = new ArrayList<>();
		int red = 0;
		int green = 0;
		for (Map.Entry<UUID, TeamColor> entry : LOBBY.entrySet()) {
			ServerPlayer player = s.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				continue;
			}
			names.put(entry.getKey(), player.getScoreboardName());
			if (entry.getValue() == null) {
				flexible.add(entry.getKey());
			} else {
				assignment.put(entry.getKey(), entry.getValue());
				if (entry.getValue() == TeamColor.RED) {
					red++;
				} else {
					green++;
				}
			}
		}
		if (ai != null) {
			// Gegen die KI spielen alle Angemeldeten zusammen im Clan des ersten Spielers mit Wunsch (sonst Rot).
			TeamColor humans = assignment.isEmpty() ? TeamColor.RED : assignment.values().iterator().next();
			assignment.replaceAll((uuid, team) -> humans);
			for (UUID uuid : flexible) {
				assignment.put(uuid, humans);
			}
			flexible.clear();
			red = humans == TeamColor.RED ? assignment.size() : 0;
			green = humans == TeamColor.GREEN ? assignment.size() : 0;
		}
		for (UUID uuid : flexible) {
			TeamColor team = red <= green ? TeamColor.RED : TeamColor.GREEN;
			assignment.put(uuid, team);
			if (team == TeamColor.RED) {
				red++;
			} else {
				green++;
			}
		}
		if (assignment.isEmpty()) {
			return Result.fail("command.goblinforest.nobody");
		}
		if (!practice && ai == null && (red == 0 || green == 0)) {
			return Result.fail("command.goblinforest.need_both_teams");
		}
		LOBBY.clear();
		Series series = bestOf > 1 ? new Series(bestOf) : null;
		match = new Match(s, arena, assignment, names, new MatchOptions(practice || ai == null && (red == 0 || green == 0), ai, series, suddenDeath));
		for (Map.Entry<UUID, TeamColor> entry : assignment.entrySet()) {
			ServerPlayer player = s.getPlayerList().getPlayer(entry.getKey());
			if (player != null) {
				player.sendSystemMessage(Component.translatable("command.goblinforest.started",
						Component.translatable(entry.getValue().translationKey()).withColor(entry.getValue().rgb())).withStyle(ChatFormatting.GOLD));
			}
		}
		if (ai != null) {
			TeamColor aiTeam = match.aiTeam();
			return Result.ok(Component.translatable("command.goblinforest.starting_ai", assignment.size(),
					Component.translatable(aiTeam.translationKey()).withColor(aiTeam.rgb()), Component.translatable(ai.translationKey()),
					bestOf));
		}
		if (bestOf > 1) {
			return Result.ok(Component.translatable("command.goblinforest.starting_series", assignment.size(), bestOf));
		}
		return Result.ok(Component.translatable("command.goblinforest.starting", assignment.size()));
	}

	/**
	 * Startet ein Übungsmatch ganz ohne Spieler (nur für automatische Tests): Einheiten werden per
	 * {@link Match#recruitForTest} gekauft und kämpfen alleine. Der Gametest-Server von Minecraft lädt keine
	 * Dimensionen aus Datenpaketen, deshalb kann der Test eine andere Welt als Arena vorgeben.
	 */
	public static Match startWithoutPlayers(MinecraftServer s, ServerLevel arena) {
		return startWithoutPlayers(s, arena, MatchOptions.single(true));
	}

	/** Wie {@link #startWithoutPlayers(MinecraftServer, ServerLevel)}, mit KI-Clan und/oder Best-of-Serie. */
	public static Match startWithoutPlayers(MinecraftServer s, ServerLevel arena, MatchOptions options) {
		if (match != null) {
			match.finish(null);
		}
		server = s;
		match = new Match(s, arena, Map.of(), Map.of(), options);
		return match;
	}

	public static Result stop(Component by) {
		if (match == null) {
			return Result.fail("command.goblinforest.no_match");
		}
		Match running = match;
		match = null;
		running.finish(Component.translatable("message.goblinforest.stopped", by).withStyle(ChatFormatting.YELLOW));
		return Result.ok(Component.translatable("command.goblinforest.stopped"));
	}

	/** Notfall: Match abbrechen, Lobby leeren, alle Spieler aus der Arena zurückholen. */
	public static Result reset(MinecraftServer s) {
		if (match != null) {
			Match running = match;
			match = null;
			running.finish(Component.translatable("message.goblinforest.reset").withStyle(ChatFormatting.YELLOW));
		}
		LOBBY.clear();
		int rescued = 0;
		for (ServerPlayer player : new ArrayList<>(s.getPlayerList().getPlayers())) {
			boolean backup = PlayerBackup.exists(s, player.getUUID());
			if (backup || player.level().dimension() == ARENA && !player.isCreative()) {
				cleanUpPlayer(player);
				if (backup) {
					PlayerBackup.restore(player);
				} else {
					PlayerBackup.sendHome(player);
				}
				rescued++;
			}
		}
		return Result.ok(Component.translatable("command.goblinforest.reset", rescued));
	}

	public static boolean canStop(ServerPlayer player) {
		return match != null && player != null && match.isMember(player.getUUID());
	}

	public static List<Component> status(MinecraftServer s) {
		List<Component> lines = new ArrayList<>();
		if (match != null) {
			lines.addAll(match.statusLines());
			return lines;
		}
		if (LOBBY.isEmpty()) {
			lines.add(Component.translatable("command.goblinforest.status.idle"));
			return lines;
		}
		lines.add(Component.translatable("command.goblinforest.status.lobby", LOBBY.size()));
		for (Map.Entry<UUID, TeamColor> entry : LOBBY.entrySet()) {
			ServerPlayer player = s.getPlayerList().getPlayer(entry.getKey());
			Component team = entry.getValue() == null ? Component.translatable("team.goblinforest.any")
					: Component.translatable(entry.getValue().translationKey()).withColor(entry.getValue().rgb());
			lines.add(Component.literal(" - ").append(player == null ? Component.literal("?") : player.getDisplayName())
					.append(" (").append(team).append(")"));
		}
		return lines;
	}

	/** Hilfsfunktion für Spieler-Objekte aus Ereignissen, die auch auf dem Client feuern. */
	static boolean isHero(Player player) {
		return match != null && match.isMember(player.getUUID());
	}
}
