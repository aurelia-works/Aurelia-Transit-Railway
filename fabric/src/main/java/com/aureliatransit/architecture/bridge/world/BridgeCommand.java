package com.aureliatransit.architecture.bridge.world;

import org.mtr.aurelia.Operators;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

/**
 * {@code /atr_bridge undo}: puts back what was there before the player's last wrench bridge. The wrench's Bridge mode runs
 * it from its undo button. Operators and creative mode only, like the other ATA building commands.
 */
final class BridgeCommand {

	private BridgeCommand() {
	}

	static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("atr_bridge").requires(source -> Operators.isOperatorOrCreativeSource(source))
				.then(CommandManager.literal("undo").executes(BridgeCommand::undo)));
	}

	private static int undo(CommandContext<ServerCommandSource> context) {
		final ServerCommandSource source = context.getSource();
		final ServerPlayerEntity player = source.getPlayer();
		if (player == null) {
			error(source, "Run this as a player.");
			return 0;
		}
		if (BridgeSessions.hasJob(player.getUuid())) {
			error(source, "A bridge task is still running; wait for it to finish before undoing.");
			return 0;
		}
		final RegistryKey<World> dimension = BridgeSessions.undoDimension(player.getUuid());
		if (dimension != null && !dimension.equals(player.getServerWorld().getRegistryKey())) {
			error(source, "The last bridge was built in " + dimension.getValue() + "; go there to undo it.");
			return 0;
		}
		if (!BridgeSessions.undo(player.getServerWorld(), player.getUuid())) {
			error(source, "Nothing to undo, or the last bridge changed nothing.");
			return 0;
		}
		source.sendFeedback(() -> Text.literal("Undoing the last bridge...").formatted(Formatting.GRAY), false);
		return 1;
	}

	private static void error(ServerCommandSource source, String message) {
		source.sendError(Text.literal(message).formatted(Formatting.RED));
	}
}
