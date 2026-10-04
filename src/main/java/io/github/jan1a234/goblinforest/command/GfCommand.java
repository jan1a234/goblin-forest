package io.github.jan1a234.goblinforest.command;

import com.mojang.brigadier.CommandDispatcher;
import io.github.jan1a234.goblinforest.GoblinForest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Der Befehl {@code /gf}. Die Match-Unterbefehle join, start, stop und reset kommen mit dem Kern-Gameplay dazu.
 */
public final class GfCommand {
	private GfCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("gf")
				.executes(context -> {
					context.getSource().sendSuccess(
							() -> Component.translatable("command.goblinforest.info", GoblinForest.version()),
							false);
					return 1;
				}));
	}
}
