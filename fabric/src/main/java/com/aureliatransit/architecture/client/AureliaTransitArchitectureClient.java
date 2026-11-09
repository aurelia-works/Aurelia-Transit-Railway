package com.aureliatransit.architecture.client;

import com.aureliatransit.architecture.client.fare.CardHistoryScreen;
import com.aureliatransit.architecture.fare.TransitCardItem;
import com.aureliatransit.architecture.station.world.StationCodexItem;
import com.aureliatransit.architecture.station.world.AirportWrenchItem;
import com.aureliatransit.architecture.station.world.StationWrenchItem;
import com.aureliatransit.architecture.block.TextSignBlock;
import com.aureliatransit.architecture.client.airport.AirportClient;
import com.aureliatransit.architecture.client.interactive.InteractiveClient;
import com.aureliatransit.architecture.client.live.LiveClient;
import com.aureliatransit.architecture.client.timetable.PocketTimetableClient;
import com.aureliatransit.architecture.client.wayfinding.WayfindingClient;
import com.aureliatransit.architecture.client.wayfinding.WayfindingLogicClient;
import com.aureliatransit.architecture.block.entity.TextSignBlockEntity;
import com.aureliatransit.architecture.registry.ModBlocks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TexturedButtonWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.mtr.aurelia.client.screen.AureliaPanelScreen;
import org.mtr.mod.data.IGui;
import org.mtr.mod.screen.DashboardScreen;

public final class AureliaTransitArchitectureClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		DevScreenshots.init();
		FreePlacementRender.init();
		LiveClient.init();
		AirportClient.init();
		InteractiveClient.init();
		WayfindingLogicClient.init();
		WayfindingClient.init();
		PocketTimetableClient.init();
		StationStudioClient.init();
		StationPaceClient.register();
		StationDevPanel.register();
		WrenchClient.init();
		WrenchSettings.load(FabricLoader.getInstance().getConfigDir());
		initMenuEntries();
		for (final ModBlocks.Entry entry : ModBlocks.entries()) {
			switch (entry.renderKind()) {
				case CUTOUT -> BlockRenderLayerMap.INSTANCE.putBlock(entry.block(), RenderLayer.getCutout());
				case TRANSLUCENT -> BlockRenderLayerMap.INSTANCE.putBlock(entry.block(), RenderLayer.getTranslucent());
				case SOLID -> {
				}
			}
		}

		StationWrenchItem.openScreen = StationWrenchScreen::open;
		AirportWrenchItem.openAirportScreen = AirportWrenchScreen::open;
		StationCodexItem.openScreen = CodexScreen::open;
		TransitCardItem.openScreen = stack -> MinecraftClient.getInstance().setScreen(new CardHistoryScreen(stack));
		TextSignBlock.openEditor = pos -> {
			final MinecraftClient client = MinecraftClient.getInstance();
			if (client.world != null && client.world.getBlockEntity(pos) instanceof TextSignBlockEntity sign) {
				client.setScreen(new TextSignEditScreen(pos, sign));
			}
		};
	}

	/**
	 * Adds ATR's own entry points without touching MTR's classes: an ATR menu button on MTR's dashboard next to the panel
	 * button, and the two wrench switches on the Aurelia panel so every visible look has a dev switch.
	 */
	private static void initMenuEntries() {
		final Identifier texture = new Identifier("aurelia", "textures/gui/dashboard_button.png");
		final int y = FabricLoader.getInstance().isModLoaded("jme") ? 6 + 20 + 4 : 6;
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof DashboardScreen) {
				final TexturedButtonWidget button = new TexturedButtonWidget(IGui.PANEL_WIDTH + 30, y, 20, 20, 0, 0, 20, texture,
						20, 40, pressed -> client.setScreen(new AtrMenuScreen(screen, AtrMenuScreen.Tab.BUILD)),
						Text.translatable("gui.aurelia.menu.title"));
				button.setTooltip(Tooltip.of(Text.translatable("gui.aurelia.menu.title")));
				Screens.getButtons(screen).add(button);
				ScreenMouseEvents.allowMouseClick(screen).register((clickedScreen, mouseX, mouseY, mouseButton) -> !button.mouseClicked(mouseX, mouseY, mouseButton));
			} else if (screen instanceof AureliaPanelScreen) {
				addPanelSwitches(screen, scaledHeight);
			}
		});
	}

	/** The wrench switches and a way into the one ATR menu, at the dev panel's free bottom-left corner. */
	private static void addPanelSwitches(Screen screen, int height) {
		Screens.getButtons(screen).add(ButtonWidget.builder(Text.translatable("gui.aurelia.menu.open"), b ->
				MinecraftClient.getInstance().setScreen(new AtrMenuScreen(screen, AtrMenuScreen.Tab.BUILD)))
				.dimensions(4, height - 96, 132, 20).build());
		final ButtonWidget actionBar = ButtonWidget.builder(actionBarLabel(), b -> {
			WrenchSettings.toggleActionBar();
			b.setMessage(actionBarLabel());
		}).dimensions(4, height - 72, 132, 20).build();
		actionBar.setTooltip(Tooltip.of(Text.translatable("gui.aurelia.menu.settings.action_bar.tooltip")));
		Screens.getButtons(screen).add(actionBar);
		Screens.getButtons(screen).add(ButtonWidget.builder(menuLookLabel(), b -> {
			WrenchSettings.toggleMenuLook();
			b.setMessage(menuLookLabel());
		}).dimensions(4, height - 48, 132, 20).build());
	}

	private static Text actionBarLabel() {
		return Text.translatable("gui.aurelia.menu.settings.action_bar", ScreenTexts.onOrOff(WrenchSettings.actionBar()));
	}

	private static Text menuLookLabel() {
		return Text.translatable("gui.aurelia.menu.settings.menu_look", ScreenTexts.onOrOff(WrenchSettings.menuLook()));
	}
}
