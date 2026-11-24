/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.net;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.S2CPlayChannelEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import org.mtr.aurelia.road.CoreInfo;
import org.mtr.aurelia.road.json.JsonFormatException;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.proto.EditorLimits;
import org.mtr.aurelia.road.proto.EditorServer;
import org.mtr.aurelia.road.proto.Messages;
import org.mtr.aurelia.road.proto.Protocol;
import org.mtr.aurelia.road.road.RoadsModule;
import org.mtr.aurelia.road.road.nodes.MtrNodes;
import org.mtr.aurelia.road.fabric.StratosMod;
import org.mtr.aurelia.road.fabric.apply.ApplyService;
import org.mtr.aurelia.road.fabric.apply.SideEffects;
import org.mtr.aurelia.road.fabric.palette.GamePalettes;

/**
 * The server's end of {@code aurelia:roads} (editor-protocol): frames core's {@link EditorServer} on Fabric's networking
 * API, the same on a dedicated server and in singleplayer.
 * <ul>
 *   <li>{@code Hello} goes out only to clients that registered the channel ({@code canSend}), so a client without
 *       Stratos is never sent anything (§9): at Fabric's join event in singleplayer, where the channels are known
 *       already, or on the tick after the client registers them on a dedicated server.</li>
 *   <li>Incoming packets are handled on the server thread (Fabric's packet handlers run there).</li>
 *   <li>Plans are worked out on two daemon threads, "Stratos planner".</li>
 *   <li><b>Who may edit</b> (F-6): {@link StratosPermissions}, by operator level, node by node.</li>
 *   <li><b>The rate and volume limits</b> (F-6) come from {@code config/aurelia/roads/server.json}, read when the server starts
 *       and written with the defaults if it isn't there. A file that doesn't read is logged and the defaults used.</li>
 *   <li>{@code Hello} lists the {@code track} capability when the server can build track ({@link SideEffects}, T-2).</li>
 * </ul>
 */
public final class EditorNetworking {
    /** Connections whose client registered the roads channel, to greet on the next server tick. */
    private static final Set<ServerPlayNetworkHandler> REGISTERED = ConcurrentHashMap.newKeySet();
    private static EditorServer server;
    private static ExecutorService planners;

    private EditorNetworking() {
    }

    /** Hooks the channel and the server's lifecycle. Called once, from the mod initialiser. */
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(StratosPacket.TYPE, (packet, player, sender) -> {
            if (server != null) {
                try {
                    server.receive(player.getUuid(), packet.bytes());
                } catch (RuntimeException e) {
                    StratosMod.LOGGER.error("[stratos] failed handling a message from {}", player.getGameProfile().getName(), e);
                }
            }
        });
        // In singleplayer the client's channels are already known while the connection is being made, before Fabric has
        // finished setting it up, so the join event says Hello. On a dedicated server the client registers its channels
        // later; those connections are remembered here and greeted on the next tick, once the connection is ready.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, minecraft) -> join(minecraft, handler));
        S2CPlayChannelEvents.REGISTER.register((handler, sender, minecraft, channels) -> {
            if (channels.contains(StratosPacket.ID)) {
                REGISTERED.add(handler);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> {
            REGISTERED.remove(handler);
            UUID player = handler.player.getUuid();
            minecraft.execute(() -> {
                if (server != null) {
                    server.leave(player);
                }
            });
        });
        // A player's node aids need the roads of the dimension they are in (F-5: YourRoads).
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            if (server != null) {
                server.dimensionChanged(player.getUuid());
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(EditorNetworking::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(minecraft -> stop());
        ServerTickEvents.END_SERVER_TICK.register(minecraft -> {
            for (Iterator<ServerPlayNetworkHandler> handlers = REGISTERED.iterator(); handlers.hasNext(); ) {
                ServerPlayNetworkHandler handler = handlers.next();
                handlers.remove();
                join(minecraft, handler);
            }
            if (server != null) {
                server.tick();
            }
        });
        ApplyService.addListener(finished -> {
            if (server != null) {
                server.finished(finished.result());
            }
        });
        // F-2+: the repair after a crash is told to players who can edit.
        ApplyService.addRepairListener(report -> {
            if (server != null) {
                server.repaired(EditorServer.repairNotice(report.rolledBack(), report.completed(), report.needAttention()));
            }
        });
    }

    private static void start(MinecraftServer minecraft) {
        String modVersion = StratosMod.version();
        int dataVersion = SharedConstants.getGameVersion().getSaveVersion().getId();
        planners = Executors.newFixedThreadPool(2, task -> {
            Thread thread = new Thread(task, "Stratos planner");
            thread.setDaemon(true);
            return thread;
        });
        // T-2: the server offers track only when it can build it.
        List<String> capabilities = SideEffects.writer(minecraft).available() ? List.of(Protocol.CAPABILITY_TRACK) : List.of();
        EditorLimits configured = readLimits(FabricLoader.getInstance().getConfigDir().resolve(EditorLimits.FILE));
        server = new EditorServer(new EditorServer.ServerInfo(modVersion, CoreInfo.version(), dataVersion, CoreInfo.FORMAT_VERSION, configured,
                capabilities),
                List.of(RoadsModule.INSTANCE),
                (player, node) -> has(minecraft, player, node),
                player -> editWorld(minecraft, player),
                planners, System::nanoTime, UUID::randomUUID,
                StratosMod.LOGGER::warn);
        // Baking to vanilla and redrawing markings (world tasks) come with the road blocks they look for.
        // UI-1c: the world's mark for Stratos blocks, kept in its aurelia_roads_meta record.
        server.stratosBlocks(ApplyService.stratosBlocks(minecraft), present -> ApplyService.stratosBlocks(minecraft, present));
    }

    /**
     * A Stratos block was placed (UI-1c), from {@code LayeredBlock.onPlace}: by a tool, an undo, a player or a command.
     * Server thread; it runs for every block, so it does next to nothing once the world's mark is on.
     */
    public static void stratosBlockPlaced(MinecraftServer minecraft) {
        if (server != null) {
            server.stratosBlockPlaced();
        } else {
            ApplyService.stratosBlocks(minecraft, true);
        }
    }

    /** Whether the world has Stratos blocks, by its mark (UI-1c), as the server would tell a player now. */
    public static boolean stratosBlocks() {
        return server == null || server.stratosBlocks();
    }

    private static void stop() {
        REGISTERED.clear();
        if (server != null) {
            server.stop();
            server = null;
        }
        if (planners != null) {
            planners.shutdownNow();
            planners = null;
        }
    }

    /** Says {@code Hello} to a connected player whose client can receive the roads channel, once per connection. */
    private static void join(MinecraftServer minecraft, ServerPlayNetworkHandler handler) {
        ServerPlayerEntity player = handler.player;
        if (server == null || server.hasSession(player.getUuid()) || !handler.isConnectionOpen()
                || minecraft.getPlayerManager().getPlayer(player.getUuid()) != player || !ServerPlayNetworking.canSend(handler, StratosPacket.TYPE)) {
            return;
        }
        PacketSender sender = ServerPlayNetworking.getSender(handler);
        String name = player.getGameProfile().getName();
        server.join(player.getUuid(), message -> {
            try {
                sender.sendPacket(new StratosPacket(Messages.encode(message)));
            } catch (RuntimeException e) {
                StratosMod.LOGGER.error("[stratos] couldn't send {} to {}", message.getClass().getSimpleName(), name, e);
            }
        });
    }

    /** Whether the player holds {@code node} ({@link StratosPermissions}); a player who has left holds none. */
    private static boolean has(MinecraftServer minecraft, UUID id, String node) {
        ServerPlayerEntity player = minecraft.getPlayerManager().getPlayer(id);
        return player != null && StratosPermissions.has(player, node);
    }

    /**
     * The server's rate and volume limits in {@code file}: written with the defaults if it isn't there, and the defaults
     * if it doesn't read, with the reason in the log.
     */
    private static EditorLimits readLimits(Path file) {
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, EditorLimits.DEFAULTS.toJson());
                return EditorLimits.DEFAULTS;
            }
            return EditorLimits.fromJson(Files.readString(file));
        } catch (IOException | JsonFormatException e) {
            StratosMod.LOGGER.error("[stratos] couldn't read {}, so the default limits apply: {}", file, e.getMessage());
            return EditorLimits.DEFAULTS;
        }
    }

    private static EditorServer.EditWorld editWorld(MinecraftServer minecraft, UUID id) {
        ServerPlayerEntity player = minecraft.getPlayerManager().getPlayer(id);
        ServerWorld level = player != null ? player.getServerWorld() : minecraft.getOverworld();
        return new EditorServer.EditWorld(ApplyService.operations(level), limits(level));
    }

    /**
     * Where {@code level} lets blocks be placed: its build height and its world border; (R-6) which of the other mods'
     * blocks the planner may use this game has; and (R-9) the palettes roads may use: the server's own on the server, the
     * server's as it sent them on a client ({@link GamePalettes}). Client and server use this.
     */
    public static WorldLimits limits(World level) {
        WorldBorder border = level.getWorldBorder();
        WorldLimits limits = new WorldLimits(level.getBottomY(), level.getTopY(), border.getBoundWest(), border.getBoundEast(), border.getBoundNorth(),
                border.getBoundSouth(), modBlocks());
        return limits.withPalettes(level instanceof ServerWorld serverLevel ? GamePalettes.server(serverLevel.getServer()) : GamePalettes.client());
    }

    /**
     * The other mods' blocks the planner may use that this game has, by runtime id (R-6: MTR's node block, for
     * pre-oriented nodes), looked up in the block registry.
     */
    public static Set<String> modBlocks() {
        return Registries.BLOCK.containsId(new Identifier(MtrNodes.BLOCK)) ? Set.of(MtrNodes.BLOCK) : Set.of();
    }
}
