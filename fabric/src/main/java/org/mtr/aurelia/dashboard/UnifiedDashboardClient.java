package org.mtr.aurelia.dashboard;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import org.mtr.aurelia.client.AureliaClient;
import org.mtr.aurelia.client.screen.RoadDashboardScreen;
import org.mtr.aurelia.client.ui.AureliaChrome;
import org.mtr.aurelia.client.ui.AureliaPalette;
import org.mtr.core.data.TransportMode;
import org.mtr.mod.packet.PacketOpenDashboardScreen;
import org.mtr.mod.screen.DashboardScreen;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Client coordinator for the unified Transit Dashboard:
 * <ul>
 *   <li>Attaches the five-mode header bar ({@code Train | Bus & Road | Boat | Cable Car | Plane}) to {@link DashboardScreen}
 *       and {@link RoadDashboardScreen} at identical pixel bounds.</li>
 *   <li>Switches modes in place while preserving map camera center & zoom and disposing {@code WorldMap} tile textures.</li>
 *   <li>Routes legacy item packets ({@code dashboard_2..4}) to their dedicated starting mode and primary {@code dashboard}
 *       (plus ATR menu / Aurelia panel entries) to the last selected mode.</li>
 * </ul>
 */
public final class UnifiedDashboardClient {

	private static final int TAB_ACTIVE_BG = 0xFF1C2A40;
	private static final int TAB_HOVER_BG = 0xF0282E38;
	private static final int TAB_IDLE_BG = 0xE816181C;
	private static final int TAB_ACTIVE_BAR = 0xFF4A90E2;

	@Nullable
	private static DashboardMode pendingMenuMode;
	private static boolean forceReplaceScreen;

	private UnifiedDashboardClient() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof DashboardScreen) {
				final DashboardMode activeMode = modeOfDashboardScreen(screen);
				final DashboardLayout layout = DashboardLayout.compute(scaledWidth, scaledHeight, AureliaClient.isMagicLoaded());
				final List<ModeTabButton> tabs = createModeButtons(client, screen, activeMode, layout);
				Screens.getButtons(screen).addAll(tabs);
				// MTR's WidgetMap consumes clicks across the map area unless handled before mouseClicked reaches it.
				ScreenMouseEvents.allowMouseClick(screen).register((clickedScreen, mouseX, mouseY, mouseButton) -> {
					if (AureliaClient.isMagicMenuOpen(clickedScreen)) {
						return true;
					}
					for (final ModeTabButton tab : tabs) {
						if (tab.mouseClicked(mouseX, mouseY, mouseButton)) {
							return false;
						}
					}
					return true;
				});
			}
		});
	}

	public static List<ModeTabButton> createModeButtons(
			MinecraftClient client,
			Screen currentScreen,
			DashboardMode activeMode,
			DashboardLayout layout
	) {
		final List<ModeTabButton> buttons = new ArrayList<>(layout.modeButtons().size());
		for (final DashboardLayout.ModeSlot slot : layout.modeButtons()) {
			buttons.add(new ModeTabButton(slot.bounds(), slot.mode(), activeMode, () -> switchMode(client, currentScreen, slot.mode())));
		}
		return buttons;
	}

	public static void attachModeButtons(
			MinecraftClient client,
			Screen currentScreen,
			DashboardMode activeMode,
			DashboardLayout layout,
			Consumer<ButtonWidget> addWidget
	) {
		for (final ModeTabButton button : createModeButtons(client, currentScreen, activeMode, layout)) {
			addWidget.accept(button);
		}
	}

	public static DashboardMode modeOfDashboardScreen(Screen screen) {
		if (screen instanceof DashboardCameraAccess access) {
			return DashboardMode.fromTransportMode(access.atr$transportMode());
		}
		return DashboardMode.TRAIN;
	}

	/** Switches from the current dashboard screen to {@code target}, preserving map camera position and zoom. */
	public static void switchMode(MinecraftClient client, @Nullable Screen currentScreen, DashboardMode target) {
		if (client == null || target == null) {
			return;
		}
		final DashboardMapState mapState = captureMapState(client, currentScreen);
		if (currentScreen instanceof DashboardCameraAccess access) {
			if (target.isMtrMode() && access.atr$transportMode() == target.transportMode) {
				return;
			}
			access.atr$disposeMap();
		} else if (currentScreen instanceof RoadDashboardScreen && target == DashboardMode.ROAD) {
			return;
		}
		DashboardSettings.setLastMode(target);
		openModeWithState(client, target, mapState);
	}

	/**
	 * Opens the unified Transit Dashboard from a UI shortcut (such as {@code AtrMenuScreen} or {@code AureliaPanelScreen}).
	 * Requests fresh {@code LIST_DATA} from the server when connected, falling back to immediate client open otherwise.
	 */
	public static void requestOpen(@Nullable DashboardMode preferredMode) {
		final MinecraftClient client = MinecraftClient.getInstance();
		final DashboardMode target = preferredMode != null ? preferredMode : DashboardSettings.lastMode();
		if (ClientPlayNetworking.canSend(DashboardPackets.OPEN_DASHBOARD)) {
			pendingMenuMode = target;
			forceReplaceScreen = true;
			final PacketByteBuf buf = PacketByteBufs.create();
			final TransportMode wireMode = target.transportMode != null ? target.transportMode : TransportMode.TRAIN;
			buf.writeString(wireMode.toString());
			ClientPlayNetworking.send(DashboardPackets.OPEN_DASHBOARD, buf);
		} else {
			DashboardSettings.setLastMode(target);
			openModeWithState(client, target, defaultMapState(client));
		}
	}

	/**
	 * Intercepts {@code ClientPacketHelper.openDashboardScreen}. Returns {@code true} when handled here (for
	 * {@code ScreenType.DEFAULT}), or {@code false} to let MTR open the specific station/platform/depot/siding editor.
	 */
	public static boolean openFromPacket(TransportMode packetMode, PacketOpenDashboardScreen.ScreenType screenType, long id) {
		final DashboardMode explicit = pendingMenuMode;
		final boolean force = forceReplaceScreen;
		pendingMenuMode = null;
		forceReplaceScreen = false;

		if (screenType != PacketOpenDashboardScreen.ScreenType.DEFAULT) {
			if (packetMode != TransportMode.TRAIN) {
				DashboardSettings.setLastMode(DashboardMode.fromTransportMode(packetMode));
			}
			return false;
		}

		final DashboardMode target = explicit != null
				? explicit
				: DashboardMode.resolveOpenMode(packetMode, true, DashboardSettings.lastMode());
		DashboardSettings.setLastMode(target);

		final MinecraftClient client = MinecraftClient.getInstance();
		final Screen current = client.currentScreen;
		if (!force) {
			if (target == DashboardMode.ROAD && current instanceof RoadDashboardScreen) {
				return true;
			}
			if (target.isMtrMode() && current instanceof DashboardCameraAccess access && access.atr$transportMode() == target.transportMode) {
				return true;
			}
		}

		final DashboardMapState mapState = captureMapState(client, current);
		if (current instanceof DashboardCameraAccess access) {
			access.atr$disposeMap();
		}
		openModeWithState(client, target, mapState);
		return true;
	}

	private static void openModeWithState(MinecraftClient client, DashboardMode target, DashboardMapState mapState) {
		if (target == DashboardMode.ROAD) {
			client.setScreen(new RoadDashboardScreen(mapState));
		} else {
			final TransportMode mode = target.transportMode != null ? target.transportMode : TransportMode.TRAIN;
			final DashboardScreen screen = new DashboardScreen(mode);
			client.setScreen(screen);
			if (screen instanceof DashboardCameraAccess access) {
				access.atr$applyMapState(mapState);
			}
		}
	}

	public static DashboardMapState captureMapState(MinecraftClient client, @Nullable Screen screen) {
		if (screen instanceof DashboardCameraAccess access) {
			return access.atr$captureMapState();
		}
		if (screen instanceof RoadDashboardScreen road) {
			return road.captureMapState();
		}
		return defaultMapState(client);
	}

	public static DashboardMapState defaultMapState(MinecraftClient client) {
		if (client != null && client.player != null) {
			return new DashboardMapState(client.player.getX(), client.player.getZ(), DashboardMapState.DEFAULT_SCALE, false);
		}
		return new DashboardMapState(0, 0, DashboardMapState.DEFAULT_SCALE, false);
	}

	/**
	 * Flat AureliaChrome-styled mode tab button. Overrides {@code renderButton} directly (like {@code AureliaPanelScreen.SideTab})
	 * so the active mode shows a distinct lit fill and accent underline while remaining keyboard-focusable.
	 */
	public static final class ModeTabButton extends ButtonWidget {

		private final DashboardMode mode;
		private final DashboardMode activeMode;

		public ModeTabButton(DashboardLayout.Rect rect, DashboardMode mode, DashboardMode activeMode, Runnable onSelect) {
			super(rect.x(), rect.y(), rect.width(), rect.height(), Text.translatable(mode.labelKey()), b -> onSelect.run(), DEFAULT_NARRATION_SUPPLIER);
			this.mode = mode;
			this.activeMode = activeMode;
			setTooltip(Tooltip.of(Text.translatable(mode.tooltipKey())));
		}

		public DashboardMode mode() {
			return mode;
		}

		@Override
		protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
			if (!visible || width <= 0 || height <= 0) {
				return;
			}
			final boolean open = mode == activeMode;
			final int bg = open ? TAB_ACTIVE_BG : (isHovered() || isFocused() ? TAB_HOVER_BG : TAB_IDLE_BG);
			final int border = open || isHovered() || isFocused() ? TAB_ACTIVE_BAR : AureliaPalette.BORDER;
			context.fill(getX(), getY(), getX() + width, getY() + height, bg);
			AureliaChrome.frame(context, getX(), getY(), width, height, border);
			if (open) {
				context.fill(getX() + 1, getY() + height - 2, getX() + width - 1, getY() + height, TAB_ACTIVE_BAR);
			} else if (isHovered()) {
				context.fill(getX(), getY(), getX() + 2, getY() + height, AureliaPalette.ACCENT);
			}
			final TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
			final String shown = textRenderer.trimToWidth(getMessage(), Math.max(4, width - 4)).getString();
			final int textX = getX() + (width - textRenderer.getWidth(shown)) / 2;
			final int textY = getY() + (height - 8) / 2;
			final int fg = open ? AureliaPalette.FG : (isHovered() ? 0xFFE6ECF5 : AureliaPalette.FG_LIGHT);
			context.drawTextWithShadow(textRenderer, shown, textX, textY, fg);
		}
	}
}
