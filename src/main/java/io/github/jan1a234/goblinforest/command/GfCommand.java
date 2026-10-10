package io.github.jan1a234.goblinforest.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.jan1a234.goblinforest.GoblinForest;
import io.github.jan1a234.goblinforest.game.AiDifficulty;
import io.github.jan1a234.goblinforest.game.Match;
import io.github.jan1a234.goblinforest.game.MatchManager;
import io.github.jan1a234.goblinforest.game.TeamColor;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Der Befehl {@code /gf}: Lobby, Matchstart und -abbruch, Status und Hilfe.
 */
public final class GfCommand {
	private GfCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("gf")
				.executes(context -> {
					context.getSource().sendSuccess(() -> Component.translatable("command.goblinforest.info", GoblinForest.version())
							.withStyle(ChatFormatting.GOLD), false);
					context.getSource().sendSuccess(() -> Component.translatable("command.goblinforest.help.hint").withStyle(ChatFormatting.GRAY), false);
					return 1;
				})
				.then(Commands.literal("help").executes(GfCommand::help))
				.then(Commands.literal("join")
						.executes(context -> join(context, null))
						.then(Commands.argument("team", StringArgumentType.word())
								.suggests((context, builder) -> SharedSuggestionProvider.suggest(new String[] {"rot", "gruen", "red", "green"}, builder))
								.executes(context -> join(context, StringArgumentType.getString(context, "team")))))
				.then(Commands.literal("leave").executes(context -> {
					ServerPlayer player = context.getSource().getPlayerOrException();
					return send(context, MatchManager.leave(player));
				}))
				.then(Commands.literal("start")
						.executes(context -> start(context, ""))
						.then(Commands.argument("options", StringArgumentType.greedyString())
								.suggests((context, builder) -> SharedSuggestionProvider.suggest(new String[] {"practice", "ki", "ki leicht",
										"ki normal", "ki schwer", "bo3", "bo5", "ki bo3", "ki schwer bo3", "sd", "bo3 sd"}, builder))
								.executes(context -> start(context, StringArgumentType.getString(context, "options")))))
				.then(Commands.literal("stop").executes(context -> {
					CommandSourceStack source = context.getSource();
					if (!isOperator(source) && !MatchManager.canStop(source.getPlayer())) {
						source.sendFailure(Component.translatable("command.goblinforest.stop_denied"));
						return 0;
					}
					return send(context, MatchManager.stop(source.getDisplayName()));
				}))
				.then(Commands.literal("reset")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(context -> send(context, MatchManager.reset(context.getSource().getServer()))))
				.then(Commands.literal("status").executes(context -> {
					for (Component line : MatchManager.status(context.getSource().getServer())) {
						context.getSource().sendSuccess(() -> line, false);
					}
					return 1;
				}))
				.then(Commands.literal("gold")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("amount", IntegerArgumentType.integer(1, 100000))
								.executes(context -> {
									Match match = MatchManager.match();
									ServerPlayer player = context.getSource().getPlayerOrException();
									TeamColor team = match == null ? null : match.teamOf(player.getUUID());
									if (team == null) {
										context.getSource().sendFailure(Component.translatable("command.goblinforest.not_in_match"));
										return 0;
									}
									int amount = IntegerArgumentType.getInteger(context, "amount");
									match.grantGold(team, amount);
									context.getSource().sendSuccess(() -> Component.translatable("command.goblinforest.gold", amount), true);
									return 1;
								}))));
	}

	private static boolean isOperator(CommandSourceStack source) {
		return Commands.LEVEL_GAMEMASTERS.check(source.permissions());
	}

	private static int join(CommandContext<CommandSourceStack> context, String team) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		TeamColor preference = null;
		if (team != null) {
			switch (team.toLowerCase(java.util.Locale.ROOT)) {
				case "rot", "red", "r" -> preference = TeamColor.RED;
				case "gruen", "grün", "green", "g" -> preference = TeamColor.GREEN;
				default -> {
					context.getSource().sendFailure(Component.translatable("command.goblinforest.unknown_team", team));
					return 0;
				}
			}
		}
		return send(context, MatchManager.join(player, preference));
	}

	/**
	 * {@code /gf start [practice] [ki [leicht|normal|schwer]] [bo3|bo5] [sd]}: Optionen in beliebiger Reihenfolge,
	 * auch auf Englisch (ai, easy, hard). {@code sd} schaltet den Sudden Death ein; ohne läuft das Match ohne Zeitlimit.
	 */
	private static int start(CommandContext<CommandSourceStack> context, String options) {
		boolean practice = false;
		AiDifficulty ai = null;
		int bestOf = 1;
		boolean suddenDeath = false;
		for (String word : options.trim().split("\\s+")) {
			String option = word.toLowerCase(java.util.Locale.ROOT);
			if (option.isEmpty()) {
				continue;
			}
			AiDifficulty difficulty = AiDifficulty.parse(option);
			if (difficulty != null) {
				ai = difficulty;
			} else if (option.equals("ki") || option.equals("ai") || option.equals("bot")) {
				ai = ai == null ? AiDifficulty.NORMAL : ai;
			} else if (option.equals("practice") || option.equals("übung") || option.equals("uebung")) {
				practice = true;
			} else if (option.equals("sd") || option.equals("suddendeath") || option.equals("sudden")) {
				suddenDeath = true;
			} else if (option.matches("bo[135]")) {
				bestOf = option.charAt(2) - '0';
			} else {
				context.getSource().sendFailure(Component.translatable("command.goblinforest.unknown_option", word));
				return 0;
			}
		}
		return send(context, MatchManager.start(context.getSource().getServer(), context.getSource().getPlayer(), practice, ai, bestOf, suddenDeath));
	}

	private static int help(CommandContext<CommandSourceStack> context) {
		String[] keys = {"join", "leave", "start", "practice", "stop", "ai", "series", "suddendeath", "status", "reset", "controls", "keys", "keys2", "units"};
		context.getSource().sendSuccess(() -> Component.translatable("command.goblinforest.help.header").withStyle(ChatFormatting.GOLD), false);
		for (String key : keys) {
			context.getSource().sendSuccess(() -> Component.translatable("command.goblinforest.help." + key), false);
		}
		return 1;
	}

	private static int send(CommandContext<CommandSourceStack> context, MatchManager.Result result) {
		if (result.success()) {
			context.getSource().sendSuccess(result::message, false);
			return 1;
		}
		context.getSource().sendFailure(result.message());
		return 0;
	}
}
