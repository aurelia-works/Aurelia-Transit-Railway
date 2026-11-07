package com.aureliatransit.architecture.client.live;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.block.wayfinding.HelpPointBlock;
import com.aureliatransit.architecture.live.LiveDebug;
import com.aureliatransit.architecture.live.PidsBlock;
import com.aureliatransit.architecture.live.PidsBlockEntity;
import com.aureliatransit.architecture.live.SpeakerBlock;
import com.aureliatransit.architecture.live.SpeakerBlockEntity;
import com.aureliatransit.architecture.live.announce.AnnouncementCategory;
import com.aureliatransit.architecture.live.announce.AnnouncementStyle;
import com.aureliatransit.architecture.live.announce.AnnouncementText;
import com.aureliatransit.architecture.live.announce.VoicePack;
import com.aureliatransit.architecture.registry.LiveBlocks;
import com.aureliatransit.architecture.transit.StationData;
import org.mtr.aurelia.announce.Announcement;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Client init of the live systems: installs the MTR-backed station data provider, the display renderer and config
 * screens, the speaker registry, the announcement engine and voice packs, and the {@code /aurelia_live} debug command.
 * Nothing here runs on a dedicated server.
 */
public final class LiveClient {

	private static MtrStationDataProvider provider;
	private static final SpeakerRegistry SPEAKERS = new SpeakerRegistry();
	private static final VoicePackManager VOICE_PACKS = new VoicePackManager();
	private static final AnnouncementEngine ENGINE = new AnnouncementEngine(SPEAKERS, VOICE_PACKS);
	/** On-train announcements, separate from the platform speaker queue. */
	private static final AnnouncementPlayer ONBOARD = new AnnouncementPlayer();

	private LiveClient() {
	}

	public static void init() {
		if (Boolean.getBoolean("ata.demoBoards")) {
			// dev runs only: a made-up station on every display (DemoStationData)
			StationData.install(new DemoStationData());
		} else if (FabricLoader.getInstance().isModLoaded("mtr")) {
			provider = new MtrStationDataProvider();
			StationData.install(provider);
		} else {
			AureliaTransitArchitecture.LOGGER.warn("Minecraft Transit Railway is not loaded: live displays and announcements will show no data");
		}

		BlockEntityRendererFactories.register(LiveBlocks.PIDS_ENTITY, PidsRenderer::new);
		BlockEntityRendererFactories.register(com.aureliatransit.architecture.registry.ModBlockEntities.TRAIN_EDGE, TrainEdgeRenderer::new);

		PidsBlock.openEditor = pos -> {
			final MinecraftClient client = MinecraftClient.getInstance();
			if (client.world != null && client.world.getBlockEntity(pos) instanceof PidsBlockEntity display) {
				client.setScreen(new PidsScreen(pos, display));
			}
		};
		SpeakerBlock.openEditor = pos -> {
			final MinecraftClient client = MinecraftClient.getInstance();
			if (client.world != null && client.world.getBlockEntity(pos) instanceof SpeakerBlockEntity speaker) {
				client.setScreen(new SpeakerScreen(pos, speaker));
			}
		};

		HelpPointBlock.speak = pos -> ENGINE.playHelpPoint(MinecraftClient.getInstance(), pos);
		VOICE_PACKS.register();
		ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof SpeakerBlockEntity speaker) {
				SPEAKERS.add(speaker);
			}
		});
		ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((entity, world) -> {
			if (entity instanceof SpeakerBlockEntity speaker) {
				SPEAKERS.remove(speaker);
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(ENGINE::onTick);
		ClientTickEvents.END_CLIENT_TICK.register(client -> ONBOARD.tick(client, System.currentTimeMillis()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			SPEAKERS.clear();
			ENGINE.reset();
			ONBOARD.stop();
			if (provider != null) {
				provider.clear();
			}
		});
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommandManager.literal("aurelia_live")
						.then(ClientCommandManager.literal("stats").executes(context -> {
							context.getSource().sendFeedback(Text.literal("Live systems (debug counters " + (LiveDebug.enabled() ? "on" : "off - use /aurelia_live debug true") + ")"));
							for (final Map.Entry<LiveDebug.Counter, Long> entry : LiveDebug.snapshot().entrySet()) {
								context.getSource().sendFeedback(Text.literal("  " + entry.getKey().name().toLowerCase() + " = " + entry.getValue()));
							}
							context.getSource().sendFeedback(Text.literal("  engine: " + ENGINE.describe()));
							context.getSource().sendFeedback(Text.literal("  provider: " + (provider == null ? "none" : provider.describeCaches())));
							return 1;
						}))
						.then(ClientCommandManager.literal("routes").executes(context -> {
							// what MTR has synced about route timing: the source of calling-point times (A14)
							if (provider == null) {
								context.getSource().sendFeedback(Text.literal("No MTR provider"));
								return 0;
							}
							provider.describeRouteTimes().forEach(line -> context.getSource().sendFeedback(Text.literal(line)));
							return 1;
						}))
						.then(ClientCommandManager.literal("edge").executes(context -> {
							// what a train-keyed edge under the crosshair sees (1.4)
							final var hit = net.minecraft.client.MinecraftClient.getInstance().crosshairTarget;
							if (!(hit instanceof net.minecraft.util.hit.BlockHitResult block)) {
								context.getSource().sendFeedback(Text.literal("Look at a block"));
								return 0;
							}
							context.getSource().sendFeedback(Text.literal(TrainEdgeRenderer.describe(block.getBlockPos(), System.currentTimeMillis())));
							return 1;
						}))
						.then(ClientCommandManager.literal("debug").then(ClientCommandManager.argument("enabled", BoolArgumentType.bool()).executes(context -> {
							final boolean enabled = BoolArgumentType.getBool(context, "enabled");
							LiveDebug.setEnabled(enabled);
							if (enabled) {
								LiveDebug.reset();
							}
							context.getSource().sendFeedback(Text.literal("Live debug counters " + (enabled ? "enabled" : "disabled")));
							return 1;
						})))
						.then(ClientCommandManager.literal("voices").executes(context -> {
							for (final VoicePack pack : VOICE_PACKS.stack().packs()) {
								context.getSource().sendFeedback(Text.literal(pack.id() + " (priority " + pack.priority() + ", " + pack.size() + " fragments)"));
							}
							return 1;
						}))
						.then(ClientCommandManager.literal("test").then(ClientCommandManager.argument("category", StringArgumentType.word())
								.suggests((context, builder) -> {
									for (final AnnouncementCategory category : AnnouncementCategory.values()) {
										builder.suggest(category.id());
									}
									return builder.buildFuture();
								})
								.executes(context -> playTest(context, AnnouncementStyle.STANDARD))
								.then(ClientCommandManager.argument("wording", StringArgumentType.word())
										.suggests((context, builder) -> {
											for (final AnnouncementStyle style : AnnouncementStyle.values()) {
												builder.suggest(style.id());
											}
											return builder.buildFuture();
										})
										.executes(context -> playTest(context, AnnouncementStyle.byId(StringArgumentType.getString(context, "wording")))))))));
	}

	/**
	 * Plays one on-train announcement through the loaded voice packs. Returns false when no fragment resolves and the
	 * system voice is unavailable, so the caller can fall back to the legacy AureliaEX clips.
	 */
	public static boolean playOnboard(MinecraftClient client, List<Announcement.VoiceFragment> fragments, String subtitle) {
		if (fragments.isEmpty() || client.player == null) {
			return false;
		}
		final List<AnnouncementText.Fragment> text = new ArrayList<>(fragments.size());
		for (final Announcement.VoiceFragment fragment : fragments) {
			text.add(new AnnouncementText.Fragment(fragment.key(), fragment.text()));
		}
		final VoicePack.Stack stack = VOICE_PACKS.stack();
		final List<String> missing = new ArrayList<>();
		if (stack.resolvePartial(text, missing).isEmpty() && !SystemVoice.available(client)) {
			return false;
		}
		final AnnouncementText.Announcement announcement = new AnnouncementText.Announcement(AnnouncementCategory.APPROACHING, subtitle, text);
		ONBOARD.start(client, announcement, stack, client.player.getX(), client.player.getY(), client.player.getZ(), 1F, System.currentTimeMillis());
		return true;
	}

	public static boolean onboardBusy() {
		return ONBOARD.busy(System.currentTimeMillis());
	}

	public static void stopOnboard() {
		ONBOARD.stop();
	}

	private static int playTest(com.mojang.brigadier.context.CommandContext<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> context, AnnouncementStyle style) {
		final AnnouncementCategory category = AnnouncementCategory.byId(StringArgumentType.getString(context, "category"));
		if (category == null) {
			context.getSource().sendFeedback(Text.literal("Unknown category"));
			return 0;
		}
		ENGINE.playTest(MinecraftClient.getInstance(), category, style);
		return 1;
	}

	/**
	 * The installed MTR-backed provider, or null when MTR is absent (for diagnostics only).
	 */
	static MtrStationDataProvider provider() {
		return provider;
	}
}
