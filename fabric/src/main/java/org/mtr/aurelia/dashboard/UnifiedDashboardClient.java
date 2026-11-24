package org.mtr.aurelia.dashboard;

import org.mtr.aurelia.client.ClientOperators;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
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
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.mtr.aurelia.client.AureliaClient;
import org.mtr.aurelia.client.ui.AureliaChrome;
import org.mtr.aurelia.client.ui.AureliaPalette;
import org.mtr.core.data.TransportMode;
import org.mtr.mod.item.ItemDashboard;
import org.mtr.mod.packet.PacketOpenDashboardScreen;
import org.mtr.mod.screen.DashboardScreen;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Client coordinator for the unified Transit Dashboard:
 * <ul>
 *   <li>Attaches the four-mode header bar ({@code Train | Boat | Cable Car | Plane}) to {@link DashboardScreen}.</li>
 *   <li>Switches modes in place while preserving map camera center & zoom and disposing {@code WorldMap} tile textures.</li>
 *   <li>Routes legacy item packets ({@code dashboard_2..4}) to their dedicated starting mode and primary {@code dashboard}
 *       (plus ATR menu / Aurelia panel entries) to the last selected mode.</li>
 * </ul>
 */
public final class UnifiedDashboardClient {

	private static final int TAB_ACTIVE_BG = 0xFF1C2A40;
	private static final int TAB_HOVER_BG = 0xF0282E38;
	private static final int TAB_IDLE_BG = 0xE816181C;
	private static final int TAB_DISABLED_BG = 0xCC121417;
	private static final int TAB_ACTIVE_BAR = 0xFF4A90E2;
	private static final int MAX_PARENT_DEPTH = 16;

	private static final DashboardOpenState OPEN_STATE = new DashboardOpenState();

	private UnifiedDashboardClient() {
	}

	public static void register() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> OPEN_STATE.clear());

		ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
			if (stack.getItem() instanceof ItemDashboard) {
				final String itemId = Registries.ITEM.getId(stack.getItem()).toString();
				final List<String> keys = DashboardPackets.tooltipTranslationKeys(itemId);
				final boolean primary = DashboardPackets.isPrimaryDashboardItemId(itemId);
				for (int i = 0; i < keys.size(); i++) {
					final Formatting color = (i == 0 && primary) ? Formatting.GOLD : Formatting.GRAY;
					lines.add(Text.translatable(keys.get(i)).formatted(color));
				}
			}
		});

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof DashboardScreen && screen instanceof DashboardCameraAccess access) {
				final DashboardMode activeMode = modeOfDashboardScreen(screen);
				final DashboardLayout layout = access.atr$tabBar().rebuild(activeMode, scaledWidth, scaledHeight, AureliaClient.isMagicLoaded());
				final List<ModeTabButton> tabs = createModeButtons(client, screen, activeMode, layout);
				access.atr$setModeTabs(tabs);
				syncModeTabs(screen, access);
				Screens.getButtons(screen).addAll(tabs);

				ScreenEvents.beforeRender(screen).register((renderedScreen, drawContext, mouseX, mouseY, tickDelta) ->
						syncModeTabs(renderedScreen, access));

				// MTR's WidgetMap consumes clicks across the map area unless handled before mouseClicked reaches it.
				ScreenMouseEvents.allowMouseClick(screen).register((clickedScreen, mouseX, mouseY, mouseButton) -> {
					if (!syncModeTabs(clickedScreen, access)) {
						return true;
					}
					for (final Object raw : access.atr$modeTabs()) {
						if (raw instanceof ModeTabButton tab && tab.mouseClicked(mouseX, mouseY, mouseButton)) {
							return false;
						}
					}
					return true;
				});
			}
		});
	}

	private static boolean syncModeTabs(Screen screen, DashboardCameraAccess access) {
		final boolean enabled = access.atr$tabBar().updateEnabled(access.atr$isEditing(), AureliaClient.isMagicMenuOpen(screen));
		for (final Object raw : access.atr$modeTabs()) {
			if (raw instanceof ModeTabButton tab) {
				tab.syncState(enabled, access.atr$isEditing());
			}
		}
		return enabled;
	}

	public static boolean canClickModeTabs(@Nullable Screen screen) {
		if (screen == null) {
			return false;
		}
		final boolean editing = screen instanceof DashboardCameraAccess access && access.atr$isEditing();
		final boolean magicOpen = AureliaClient.isMagicMenuOpen(screen);
		return DashboardTabBar.canSwitchMode(editing, magicOpen);
	}

	public static boolean canOpenDashboard(@Nullable MinecraftClient client) {
		if (client == null || client.player == null) {
			return false;
		}
		return DashboardPackets.isAllowed(
				ClientOperators.isOperator(client),
				DashboardPackets.hasDashboardItem(client.player)
		);
	}

	public static List<ModeTabButton> createModeButtons(
			MinecraftClient client,
			Screen currentScreen,
			DashboardMode activeMode,
			DashboardLayout layout
	) {
		final List<ModeTabButton> buttons = new ArrayList<>(layout.modeButtons().size());
		for (final DashboardLayout.ModeSlot slot : layout.modeButtons()) {
			buttons.add(new ModeTabButton(slot.bounds(), slot.mode(), activeMode, currentScreen, () -> switchMode(client, currentScreen, slot.mode())));
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

	public static DashboardMode modeOfDashboardScreen(@Nullable Screen screen) {
		if (screen instanceof DashboardCameraAccess access) {
			return DashboardMode.fromTransportMode(access.atr$transportMode());
		}
		return DashboardMode.TRAIN;
	}

	@Nullable
	public static Screen findDashboardInChain(@Nullable Screen start) {
		Screen cursor = start;
		for (int depth = 0; cursor != null && depth < MAX_PARENT_DEPTH; depth++) {
			if (cursor instanceof DashboardCameraAccess) {
				return cursor;
			}
			if (cursor instanceof DashboardParentAccess parentAccess) {
				cursor = parentAccess.atr$parentScreen();
			} else {
				break;
			}
		}
		return null;
	}

	private static void disposeDashboardsInChain(@Nullable Screen start) {
		Screen cursor = start;
		for (int depth = 0; cursor != null && depth < MAX_PARENT_DEPTH; depth++) {
			if (cursor instanceof DashboardCameraAccess access) {
				access.atr$disposeMap();
			}
			if (cursor instanceof DashboardParentAccess parentAccess) {
				cursor = parentAccess.atr$parentScreen();
			} else {
				break;
			}
		}
	}

	/** Switches from the current dashboard screen to {@code target}, preserving map camera position and zoom. */
	public static void switchMode(MinecraftClient client, @Nullable Screen currentScreen, DashboardMode target) {
		if (client == null || target == null) {
			return;
		}
		if (currentScreen instanceof DashboardCameraAccess directAccess && directAccess.atr$isEditing()) {
			return;
		}
		final Screen existingDashboard = findDashboardInChain(currentScreen);
		if (existingDashboard != null && modeOfDashboardScreen(existingDashboard) == target) {
			if (client.currentScreen != existingDashboard) {
				client.setScreen(existingDashboard);
			}
			return;
		}
		final DashboardMapState mapState = captureMapState(client, currentScreen);
		disposeDashboardsInChain(currentScreen);
		DashboardSettings.setLastMode(target);
		openModeWithState(client, target, mapState);
	}

	/**
	 * Opens the unified Transit Dashboard from a UI shortcut (such as {@code AureliaPanelScreen}).
	 * If a dashboard screen is already open in the {@code parent} chain, returns directly to it without a server round-trip
	 * or {@code WorldMap} texture churn; otherwise requests fresh {@code LIST_DATA} when permitted.
	 */
	public static void requestOpen(@Nullable DashboardMode preferredMode) {
		final MinecraftClient client = MinecraftClient.getInstance();
		if (!canOpenDashboard(client)) {
			return;
		}
		final Screen existingDashboard = findDashboardInChain(client.currentScreen);
		if (existingDashboard != null) {
			if (preferredMode == null || modeOfDashboardScreen(existingDashboard) == preferredMode) {
				client.setScreen(existingDashboard);
			} else {
				switchMode(client, existingDashboard, preferredMode);
			}
			return;
		}

		final DashboardMode target = preferredMode != null ? preferredMode : DashboardSettings.lastMode();
		if (ClientPlayNetworking.canSend(DashboardPackets.OPEN_DASHBOARD)) {
			OPEN_STATE.arm(target, client.currentScreen, System.currentTimeMillis());
			final PacketByteBuf buf = PacketByteBufs.create();
			buf.writeString(TransportMode.TRAIN.toString());
			ClientPlayNetworking.send(DashboardPackets.OPEN_DASHBOARD, buf);
		} else {
			DashboardSettings.setLastMode(target);
			final DashboardMapState mapState = captureMapState(client, client.currentScreen);
			disposeDashboardsInChain(client.currentScreen);
			openModeWithState(client, target, mapState);
		}
	}

	/**
	 * Intercepts {@code ClientPacketHelper.openDashboardScreen}. Returns {@code true} when handled here (for
	 * {@code ScreenType.DEFAULT}), or {@code false} to let MTR open the specific station/platform/depot/siding editor.
	 */
	public static boolean openFromPacket(TransportMode packetMode, PacketOpenDashboardScreen.ScreenType screenType, long id) {
		final MinecraftClient client = MinecraftClient.getInstance();
		final Screen current = client != null ? client.currentScreen : null;
		final DashboardOpenState.PacketResolution resolution = OPEN_STATE.resolvePacket(
				packetMode,
				screenType == PacketOpenDashboardScreen.ScreenType.DEFAULT,
				DashboardSettings.lastMode(),
				current,
				System.currentTimeMillis()
		);

		if (!resolution.handledByUnified() || resolution.targetMode() == null) {
			return false;
		}

		final DashboardMode target = resolution.targetMode();
		if (resolution.updateLastMode()) {
			DashboardSettings.setLastMode(target);
		}
		if (client == null) {
			return true;
		}

		if (!resolution.forceReplaceScreen()) {
			final Screen existing = findDashboardInChain(current);
			if (existing != null && existing == current && modeOfDashboardScreen(existing) == target) {
				return true;
			}
		}

		final DashboardMapState mapState = captureMapState(client, current);
		disposeDashboardsInChain(current);
		openModeWithState(client, target, mapState);
		return true;
	}

	private static void openModeWithState(MinecraftClient client, DashboardMode target, DashboardMapState mapState) {
		final TransportMode mode = target.transportMode != null ? target.transportMode : TransportMode.TRAIN;
		final DashboardScreen screen = new DashboardScreen(mode);
		client.setScreen(screen);
		if (screen instanceof DashboardCameraAccess access) {
			access.atr$applyMapState(mapState);
		}
	}

	public static DashboardMapState captureMapState(MinecraftClient client, @Nullable Screen screen) {
		final Screen dashboard = findDashboardInChain(screen);
		if (dashboard instanceof DashboardCameraAccess access) {
			return access.atr$captureMapState();
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
		@Nullable
		private final Screen ownerScreen;
		private boolean showingEditingLockTooltip;

		public ModeTabButton(
				DashboardLayout.Rect rect,
				DashboardMode mode,
				DashboardMode activeMode,
				@Nullable Screen ownerScreen,
				Runnable onSelect
		) {
			super(rect.x(), rect.y(), rect.width(), rect.height(), Text.translatable(mode.labelKey()), b -> onSelect.run(), DEFAULT_NARRATION_SUPPLIER);
			this.mode = mode;
			this.activeMode = activeMode;
			this.ownerScreen = ownerScreen;
			setTooltip(Tooltip.of(Text.translatable(mode.tooltipKey())));
		}

		public DashboardMode mode() {
			return mode;
		}

		void syncState(boolean enabled, boolean editingLocked) {
			this.active = enabled;
			if (editingLocked != showingEditingLockTooltip) {
				showingEditingLockTooltip = editingLocked;
				setTooltip(Tooltip.of(Text.translatable(editingLocked ? "gui.aurelia.dashboard.editing_locked" : mode.tooltipKey())));
			}
		}

		@Override
		public void onPress() {
			if (!this.active || (ownerScreen != null && !canClickModeTabs(ownerScreen))) {
				return;
			}
			super.onPress();
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if (!this.active || (ownerScreen != null && !canClickModeTabs(ownerScreen))) {
				return false;
			}
			return super.mouseClicked(mouseX, mouseY, button);
		}

		@Override
		protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
			if (!visible || width <= 0 || height <= 0) {
				return;
			}
			if (ownerScreen != null) {
				final boolean editing = ownerScreen instanceof DashboardCameraAccess access && access.atr$isEditing();
				syncState(DashboardTabBar.canSwitchMode(editing, AureliaClient.isMagicMenuOpen(ownerScreen)), editing);
			}
			final boolean open = mode == activeMode;
			final boolean interactive = this.active;
			final boolean hoverOrFocus = interactive && (isHovered() || isFocused());
			final int bg = open ? TAB_ACTIVE_BG : (!interactive ? TAB_DISABLED_BG : (hoverOrFocus ? TAB_HOVER_BG : TAB_IDLE_BG));
			final int border = open || hoverOrFocus ? TAB_ACTIVE_BAR : AureliaPalette.BORDER;
			context.fill(getX(), getY(), getX() + width, getY() + height, bg);
			AureliaChrome.frame(context, getX(), getY(), width, height, border);
			if (open) {
				context.fill(getX() + 1, getY() + height - 2, getX() + width - 1, getY() + height, TAB_ACTIVE_BAR);
			} else if (interactive && isHovered()) {
				context.fill(getX(), getY(), getX() + 2, getY() + height, AureliaPalette.ACCENT);
			}
			final TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
			final String shown = textRenderer.trimToWidth(getMessage(), Math.max(4, width - 4)).getString();
			final int textX = getX() + (width - textRenderer.getWidth(shown)) / 2;
			final int textY = getY() + (height - 8) / 2;
			final int fg = open
					? (interactive ? AureliaPalette.FG : AureliaPalette.FG_LIGHT)
					: (!interactive ? 0xFF6E737B : (isHovered() ? 0xFFE6ECF5 : AureliaPalette.FG_LIGHT));
			context.drawTextWithShadow(textRenderer, shown, textX, textY, fg);
		}
	}
}
