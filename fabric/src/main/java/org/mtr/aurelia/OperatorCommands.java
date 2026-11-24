package org.mtr.aurelia;

import com.aureliatransit.architecture.registry.ModItems;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Two commands the panel's tools use instead of vanilla {@code /tp} and {@code /give}, so the single-player host can use
 * them with cheats off. Both are for {@link Operators} only and act on the player who runs them.
 * <ul>
 * <li>{@code /atr_jump <pos>}: moves you to a point in your own dimension (Jump, Speed check, Train inspector).</li>
 * <li>{@code /atr_wrench}: gives you the ATR wrench.</li>
 * </ul>
 */
public final class OperatorCommands {

	private OperatorCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("atr_jump").requires(source -> Operators.isOperatorSource(source))
				.then(CommandManager.argument("pos", Vec3ArgumentType.vec3()).executes(context -> {
					final ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
					final Vec3d pos = Vec3ArgumentType.getVec3(context, "pos");
					player.teleport(player.getServerWorld(), pos.x, pos.y, pos.z, player.getYaw(), player.getPitch());
					return 1;
				})));
		dispatcher.register(CommandManager.literal("atr_wrench").requires(source -> Operators.isOperatorSource(source))
				.executes(context -> {
					context.getSource().getPlayerOrThrow().getInventory().offerOrDrop(new ItemStack(ModItems.WRENCH));
					return 1;
				}));
	}
}
