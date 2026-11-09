package com.aureliatransit.architecture.client;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.network.ModPackets;
import com.aureliatransit.architecture.station.AirportFeatures;
import com.aureliatransit.architecture.station.AirportPresets;
import com.aureliatransit.architecture.station.StationDescriptionParser;
import com.aureliatransit.architecture.station.world.StationWrenchItem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

import java.util.EnumMap;
import java.util.Map;

/**
 * The airport wrench screen: the airport choices (Regional, Small, Two runways, Airstrip) write their words into the text box, which
 * can be edited freely ("airport, 2 runways, 6 gates, no terminal"); then Plan in front of me, Build, Cancel or Undo. Each button
 * sends one packet (hand, action, words) like the station wrench screen and closes the screen, so the outline and the chat summary
 * are visible. The server re-checks the operator level and bounds the words, and plans an airport whatever they say.
 */
public class AirportWrenchScreen extends Screen {

	private static final String KEY = "screen." + AureliaTransitArchitecture.MOD_ID + ".airport_wrench.";
	private static final int WIDTH = 260;
	private static final int ROW = 22;

	private final Hand hand;
	private String words;
	private TextFieldWidget field;
	/** Set once an action went to the server, which saves the words with it: closing then sends nothing more. */
	private boolean sent;
	/** The feature switches, so their labels can follow the words. */
	private final Map<AirportFeatures.Feature, ButtonWidget> toggles = new EnumMap<>(AirportFeatures.Feature.class);

	public AirportWrenchScreen(Hand hand, String words) {
		super(Text.translatable(KEY + "title"));
		this.hand = hand;
		this.words = words;
	}

	/** Old airport-wrench id: the same screen as the one wrench, opened in Airport mode. */
	public static void open(Hand hand) {
		StationWrenchScreen.openAirport(hand);
	}

	@Override
	protected void init() {
		final int left = (width - WIDTH) / 2;
		int y = Math.max(24, height / 2 - 110);
		final int half = (WIDTH - 4) / 2;
		for (int i = 0; i < AirportPresets.ALL.size(); i++) {
			final AirportPresets.Preset preset = AirportPresets.ALL.get(i);
			addDrawableChild(ButtonWidget.builder(Text.translatable(KEY + "preset." + preset.key()), b -> field.setText(preset.description()))
					.dimensions(left + (i % 2) * (half + 4), y + (i / 2) * ROW, half, 20).build());
		}
		y += ((AirportPresets.ALL.size() + 1) / 2) * ROW + 8;
		field = new TextFieldWidget(textRenderer, left, y, WIDTH, 18, Text.translatable(KEY + "description"));
		field.setMaxLength(StationDescriptionParser.MAX_INPUT);
		field.setText(words);
		field.setPlaceholder(Text.translatable(KEY + "example"));
		addDrawableChild(field);
		y += 26;
		// one switch per visible feature; they write the same plain words the server parses, so an old save opens with all on
		final int featureWidth = (WIDTH - 8) / 3;
		int i = 0;
		for (final AirportFeatures.Feature feature : AirportFeatures.Feature.values()) {
			final ButtonWidget button = addDrawableChild(ButtonWidget.builder(toggleLabel(feature), b -> {
				words = AirportFeatures.set(words, feature, !AirportFeatures.enabled(words, feature));
				field.setText(words);
				refreshToggles();
			}).dimensions(left + (i % 3) * (featureWidth + 4), y + (i / 3) * 20, featureWidth, 18).build());
			toggles.put(feature, button);
			i++;
		}
		y += ((AirportFeatures.Feature.values().length + 2) / 3) * 20 + 6;
		final int action = (WIDTH - 8) / 3;
		action(StationWrenchItem.Action.PLAN, "plan", left, y, WIDTH);
		y += ROW;
		action(StationWrenchItem.Action.CONFIRM, "build", left, y, action);
		action(StationWrenchItem.Action.CANCEL, "cancel", left + action + 4, y, action);
		action(StationWrenchItem.Action.UNDO, "undo", left + 2 * (action + 4), y, action);
		refreshToggles();
	}

	/** "@Runway markings: On" for the switch's current state in the words. */
	private Text toggleLabel(AirportFeatures.Feature feature) {
		final boolean on = AirportFeatures.enabled(field == null ? words : field.getText(), feature);
		return Text.translatable(AirportFeatures.langKey(feature)).append(": ")
				.append(Text.translatable(KEY + "feature." + (on ? "on" : "off")));
	}

	private void refreshToggles() {
		for (final Map.Entry<AirportFeatures.Feature, ButtonWidget> entry : toggles.entrySet()) {
			entry.getValue().setMessage(toggleLabel(entry.getKey()));
		}
	}

	private void action(StationWrenchItem.Action action, String key, int x, int y, int w) {
		addDrawableChild(ButtonWidget.builder(Text.translatable(KEY + key), b -> send(action)).dimensions(x, y, w, 20).build());
	}

	private void send(StationWrenchItem.Action action) {
		words = field.getText();
		ClientPlayNetworking.send(ModPackets.STATION_WRENCH, ModPackets.writeStationWrench(hand, action, words, "",
				com.aureliatransit.architecture.station.world.WrenchMode.AIRPORT));
		sent = true;
		close();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer, Text.translatable(KEY + "hint").formatted(Formatting.GRAY), width / 2, 20, 0xAAAAAA);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public void close() {
		// closing without an action saves the words on the wrench, so its next click on the ground plans them
		if (!sent && field != null && !field.getText().equals(StationWrenchItem.description(client.player.getStackInHand(hand)))) {
			ClientPlayNetworking.send(ModPackets.STATION_WRENCH, ModPackets.writeStationWrench(hand, StationWrenchItem.Action.SAVE, field.getText(), "",
					com.aureliatransit.architecture.station.world.WrenchMode.AIRPORT));
		}
		super.close();
	}
}
