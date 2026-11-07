package org.mtr.mod.passenger;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Operator-only demo for the passenger network: {@code /atr_passengers demo [count]} turns passengers on, fills the
 * nearest MTR platform with waiting people, and sends the counts to every client; {@code /atr_passengers clear} removes
 * them and turns passengers off again. It uses the same {@link PassengerNetwork} and {@link PassengerService} the game
 * uses, not a separate fake renderer.
 */
final class PassengerCommand {

	private PassengerCommand() {
	}

	static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("atr_passengers").requires(source -> source.hasPermissionLevel(2))
				.then(CommandManager.literal("demo")
						.executes(context -> demo(context, 20))
						.then(CommandManager.argument("count", IntegerArgumentType.integer(1, 60))
								.executes(context -> demo(context, IntegerArgumentType.getInteger(context, "count")))))
				.then(CommandManager.literal("clear").executes(PassengerCommand::clear)));
	}

	private static int demo(CommandContext<ServerCommandSource> context, int count) {
		final ServerPlayerEntity player = context.getSource().getPlayer();
		if (player == null) {
			error(context, "Run this as a player standing near an MTR platform.");
			return 0;
		}
		PassengerService.demo(player.getServer(), player.getServerWorld(), player.getBlockPos(), count, player);
		return count;
	}

	private static int clear(CommandContext<ServerCommandSource> context) {
		final ServerPlayerEntity player = context.getSource().getPlayer();
		if (player == null) {
			error(context, "Run this as a player.");
			return 0;
		}
		PassengerService.clearDemo(player.getServer(), player.getServerWorld(), player);
		return 1;
	}

	private static void error(CommandContext<ServerCommandSource> context, String message) {
		context.getSource().sendError(Text.literal(message).formatted(Formatting.RED));
	}
}
