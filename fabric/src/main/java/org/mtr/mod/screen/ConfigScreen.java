package org.mtr.mod.screen;

import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.*;
import org.mtr.mod.Init;
import org.mtr.mod.Keys;
import org.mtr.mod.Patreon;
import org.mtr.mod.client.DynamicTextureCache;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.config.Client;
import org.mtr.mod.config.Config;
import org.mtr.mod.data.IGui;
import org.mtr.mod.generated.lang.TranslationProvider;

import javax.annotation.Nullable;

public class ConfigScreen extends MTRScreenBase implements IGui {

	private final Client client = Config.getClient();

	private final ButtonWidgetExtension buttonShowAnnouncementMessages;
	private final ButtonWidgetExtension buttonUseTTSAnnouncements;
	private final ButtonWidgetExtension buttonHideTranslucentParts;
	private final ButtonWidgetExtension buttonHideRainInsideVehicles;
	private final ButtonWidgetExtension buttonLanguageOptions;
	private final WidgetShorterSlider sliderDynamicTextureResolution;
	private final WidgetShorterSlider sliderTrainOscillationMultiplier;
	private final ButtonWidgetExtension buttonDefaultRail3D;
	private final ButtonWidgetExtension buttonUseMTRFont;
	private final ButtonWidgetExtension buttonDisableShadowsForShaders;
	private final ButtonWidgetExtension buttonFasterDoorChecks;
	private final ButtonWidgetExtension buttonCheapShadows;
	private final ButtonWidgetExtension buttonRailLod;
	private final WidgetShorterSlider sliderRailSimpleChunks;
	private final WidgetShorterSlider sliderRailFarChunks;
	private final ButtonWidgetExtension buttonMergeTrainParts;
	private final ButtonWidgetExtension buttonTrainLod;
	private final WidgetShorterSlider sliderTrainInteriorChunks;
	private final WidgetShorterSlider sliderTrainBoxChunks;
	private final ButtonWidgetExtension buttonFasterModelLoading;
	private final ButtonWidgetExtension buttonBackgroundModelLoading;
	private final ButtonWidgetExtension buttonFreeUnusedModels;
	private final ButtonWidgetExtension buttonSupportPatreon;

	private static final Identifier HEADER_LOGO = new Identifier("mtr:textures/gui/atr_logo.png");
	private static final int HEADER_LOGO_SIZE = 40;
	private static final int BUTTON_WIDTH = 60;
	private static final int BUTTON_HEIGHT = TEXT_HEIGHT * 2;

	/** The rows no longer fit one screen, so the whole page scrolls (header, rows and Patreon list). */
	private int scrollOffset;
	private int rowCount;

	public ConfigScreen(@Nullable Screen previousScreen) {
		super(previousScreen);

		buttonShowAnnouncementMessages = new ButtonWidgetExtension(0, 0, 0, BUTTON_HEIGHT, TextHelper.literal(""), button -> {
			client.toggleChatAnnouncements();
			setButtonText(button, client.getChatAnnouncements());
		});
		buttonUseTTSAnnouncements = new ButtonWidgetExtension(0, 0, 0, BUTTON_HEIGHT, TextHelper.literal(""), button -> {
			client.toggleTextToSpeechAnnouncements();
			setButtonText(button, client.getTextToSpeechAnnouncements());
		});
		buttonHideTranslucentParts = new ButtonWidgetExtension(0, 0, 0, BUTTON_HEIGHT, TextHelper.literal(""), button -> {
			client.toggleHideTranslucentParts();
			setButtonText(button, client.getHideTranslucentParts());
		});
		buttonHideRainInsideVehicles = new ButtonWidgetExtension(0, 0, 0, BUTTON_HEIGHT, TextHelper.literal(""), button -> {
			client.toggleHideRainInsideVehicles();
			setButtonText(button, client.getHideRainInsideVehicles());
		});
		buttonLanguageOptions = new ButtonWidgetExtension(0, 0, 0, BUTTON_HEIGHT, TextHelper.literal(""), button -> {
			client.cycleLanguageDisplay();
			button.setMessage(client.getLanguageDisplay().translationKey.getText());
		});
		sliderDynamicTextureResolution = new WidgetShorterSlider(0, 0, Client.DYNAMIC_RESOLUTION_COUNT - 1, String::valueOf, null);
		sliderTrainOscillationMultiplier = new WidgetShorterSlider(0, 0, Client.TRAIN_OSCILLATION_COUNT, i -> (i * 10) + "%", null);
		buttonDefaultRail3D = new ButtonWidgetExtension(0, 0, 0, BUTTON_HEIGHT, TextHelper.literal(""), button -> {
			client.toggleDefaultRail3D();
			setButtonText(button, client.getDefaultRail3D());
		});
		buttonUseMTRFont = new ButtonWidgetExtension(0, 0, 0, BUTTON_HEIGHT, TextHelper.literal(""), button -> {
			client.toggleUseMTRFont();
			setButtonText(button, client.getUseMTRFont());
		});
		buttonDisableShadowsForShaders = new ButtonWidgetExtension(0, 0, 0, BUTTON_HEIGHT, TextHelper.literal(""), button -> {
			client.toggleDisableShadowsForShaders();
			setButtonText(button, client.getDisableShadowsForShaders());
		});
		buttonFasterDoorChecks = new ButtonWidgetExtension(0, 0, 0, BUTTON_HEIGHT, TextHelper.literal(""), button -> {
			client.toggleFasterDoorChecks();
			setButtonText(button, client.getFasterDoorChecks());
		});
		buttonCheapShadows = new ButtonWidgetExtension(0, 0, 0, BUTTON_HEIGHT, TextHelper.literal(""), button -> {
			client.toggleCheapShadows();
			setButtonText(button, client.getCheapShadows());
		});
		buttonRailLod = new ButtonWidgetExtension(0, 0, 0, BUTTON_HEIGHT, TextHelper.literal(""), button -> {
			client.toggleRailLod();
			setButtonText(button, client.getRailLod());
		});
		sliderRailSimpleChunks = new WidgetShorterSlider(0, 0, Client.RAIL_SIMPLE_CHUNKS_MAX, i -> i == 0 ? "-" : String.valueOf(i), null);
		sliderRailFarChunks = new WidgetShorterSlider(0, 0, Client.RAIL_FAR_CHUNKS_MAX, i -> i == 0 ? "-" : String.valueOf(i), null);
		buttonMergeTrainParts = new ButtonWidgetExtension(0, 0, 0, BUTTON_HEIGHT, TextHelper.literal(""), button -> {
			client.toggleMergeTrainParts();
			setButtonText(button, client.getMergeTrainParts());
		});
		buttonTrainLod = new ButtonWidgetExtension(0, 0, 0, BUTTON_HEIGHT, TextHelper.literal(""), button -> {
			client.toggleTrainLod();
			setButtonText(button, client.getTrainLod());
		});
		sliderTrainInteriorChunks = new WidgetShorterSlider(0, 0, Client.TRAIN_INTERIOR_CHUNKS_MAX, i -> i == 0 ? "-" : String.valueOf(i), null);
		sliderTrainBoxChunks = new WidgetShorterSlider(0, 0, Client.TRAIN_BOX_CHUNKS_MAX, i -> i == 0 ? "-" : String.valueOf(i), null);
		buttonFasterModelLoading = new ButtonWidgetExtension(0, 0, 0, BUTTON_HEIGHT, TextHelper.literal(""), button -> {
			client.toggleFasterModelLoading();
			setButtonText(button, client.getFasterModelLoading());
		});
		buttonBackgroundModelLoading = new ButtonWidgetExtension(0, 0, 0, BUTTON_HEIGHT, TextHelper.literal(""), button -> {
			client.toggleBackgroundModelLoading();
			setButtonText(button, client.getBackgroundModelLoading());
		});
		buttonFreeUnusedModels = new ButtonWidgetExtension(0, 0, 0, BUTTON_HEIGHT, TextHelper.literal(""), button -> {
			client.toggleFreeUnusedModels();
			setButtonText(button, client.getFreeUnusedModels());
		});
		buttonSupportPatreon = new ButtonWidgetExtension(0, 0, 0, BUTTON_HEIGHT, TextHelper.literal(""), button -> Util.getOperatingSystem().open("https://www.patreon.com/minecraft_transit_railway"));
	}

	@Override
	protected void init2() {
		super.init2();

		positionWidgets();
		setButtonText(new ButtonWidget(buttonShowAnnouncementMessages), client.getChatAnnouncements());
		setButtonText(new ButtonWidget(buttonUseTTSAnnouncements), client.getTextToSpeechAnnouncements());
		setButtonText(new ButtonWidget(buttonHideTranslucentParts), client.getHideTranslucentParts());
		setButtonText(new ButtonWidget(buttonHideRainInsideVehicles), client.getHideRainInsideVehicles());
		setButtonText(new ButtonWidget(buttonDefaultRail3D), client.getDefaultRail3D());
		setButtonText(new ButtonWidget(buttonUseMTRFont), client.getUseMTRFont());
		setButtonText(new ButtonWidget(buttonDisableShadowsForShaders), client.getDisableShadowsForShaders());
		setButtonText(new ButtonWidget(buttonFasterDoorChecks), client.getFasterDoorChecks());
		setButtonText(new ButtonWidget(buttonCheapShadows), client.getCheapShadows());
		setButtonText(new ButtonWidget(buttonRailLod), client.getRailLod());
		sliderRailSimpleChunks.setHeight(BUTTON_HEIGHT);
		sliderRailSimpleChunks.setValue(client.getRailSimpleChunks());
		sliderRailFarChunks.setHeight(BUTTON_HEIGHT);
		sliderRailFarChunks.setValue(client.getRailFarChunks());
		setButtonText(new ButtonWidget(buttonMergeTrainParts), client.getMergeTrainParts());
		setButtonText(new ButtonWidget(buttonTrainLod), client.getTrainLod());
		sliderTrainInteriorChunks.setHeight(BUTTON_HEIGHT);
		sliderTrainInteriorChunks.setValue(client.getTrainInteriorChunks());
		sliderTrainBoxChunks.setHeight(BUTTON_HEIGHT);
		sliderTrainBoxChunks.setValue(client.getTrainBoxChunks());
		setButtonText(new ButtonWidget(buttonFasterModelLoading), client.getFasterModelLoading());
		setButtonText(new ButtonWidget(buttonBackgroundModelLoading), client.getBackgroundModelLoading());
		setButtonText(new ButtonWidget(buttonFreeUnusedModels), client.getFreeUnusedModels());
		buttonLanguageOptions.setMessage2(client.getLanguageDisplay().translationKey.getText());
		sliderDynamicTextureResolution.setHeight(BUTTON_HEIGHT);
		sliderDynamicTextureResolution.setValue(client.getDynamicTextureResolution());
		sliderTrainOscillationMultiplier.setHeight(BUTTON_HEIGHT);
		sliderTrainOscillationMultiplier.setValue((int) (client.getVehicleOscillationMultiplier() * 10));
		buttonDefaultRail3D.active = OptimizedRenderer.hasOptimizedRendering();
		buttonSupportPatreon.setMessage2(TranslationProvider.GUI_MTR_SUPPORT.getText());

		addChild(new ClickableWidget(buttonShowAnnouncementMessages));
		addChild(new ClickableWidget(buttonUseTTSAnnouncements));
		addChild(new ClickableWidget(buttonHideTranslucentParts));
		addChild(new ClickableWidget(buttonHideRainInsideVehicles));
		addChild(new ClickableWidget(buttonLanguageOptions));
		addChild(new ClickableWidget(sliderDynamicTextureResolution));
		addChild(new ClickableWidget(sliderTrainOscillationMultiplier));
		addChild(new ClickableWidget(buttonDefaultRail3D));
		addChild(new ClickableWidget(buttonUseMTRFont));
		addChild(new ClickableWidget(buttonDisableShadowsForShaders));
		addChild(new ClickableWidget(buttonFasterDoorChecks));
		addChild(new ClickableWidget(buttonCheapShadows));
		addChild(new ClickableWidget(buttonRailLod));
		addChild(new ClickableWidget(sliderRailSimpleChunks));
		addChild(new ClickableWidget(sliderRailFarChunks));
		addChild(new ClickableWidget(buttonMergeTrainParts));
		addChild(new ClickableWidget(buttonTrainLod));
		addChild(new ClickableWidget(sliderTrainInteriorChunks));
		addChild(new ClickableWidget(sliderTrainBoxChunks));
		addChild(new ClickableWidget(buttonFasterModelLoading));
		addChild(new ClickableWidget(buttonBackgroundModelLoading));
		addChild(new ClickableWidget(buttonFreeUnusedModels));
		addChild(new ClickableWidget(buttonSupportPatreon));
	}

	@Override
	public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
		try {
			renderBackground(graphicsHolder);

			graphicsHolder.push();
			graphicsHolder.translate(0, -scrollOffset, 0);
			drawHeader(graphicsHolder);
			graphicsHolder.pop();

			final int yStart1 = SQUARE_SIZE + TEXT_PADDING + TEXT_PADDING / 2 - scrollOffset;
			int i = 1;
			graphicsHolder.drawText(TranslationProvider.OPTIONS_MTR_SHOW_ANNOUNCEMENT_MESSAGES.getMutableText(), SQUARE_SIZE, BUTTON_HEIGHT * (i++) + yStart1, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
			graphicsHolder.drawText(TranslationProvider.OPTIONS_MTR_USE_TTS_ANNOUNCEMENTS.getMutableText(), SQUARE_SIZE, BUTTON_HEIGHT * (i++) + yStart1, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
			graphicsHolder.drawText(TranslationProvider.OPTIONS_MTR_HIDE_TRANSLUCENT_PARTS.getMutableText(), SQUARE_SIZE, BUTTON_HEIGHT * (i++) + yStart1, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
			graphicsHolder.drawText(TranslationProvider.OPTIONS_MTR_HIDE_RAIN_INSIDE_VEHICLES.getMutableText(), SQUARE_SIZE, BUTTON_HEIGHT * (i++) + yStart1, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
			graphicsHolder.drawText(TranslationProvider.OPTIONS_MTR_LANGUAGE_OPTIONS.getMutableText(), SQUARE_SIZE, BUTTON_HEIGHT * (i++) + yStart1, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
			graphicsHolder.drawText(TranslationProvider.OPTIONS_MTR_DYNAMIC_TEXTURE_RESOLUTION.getMutableText(), SQUARE_SIZE, BUTTON_HEIGHT * (i++) + yStart1, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
			graphicsHolder.drawText(TranslationProvider.OPTIONS_MTR_VEHICLE_OSCILLATION_MULTIPLIER.getMutableText(), SQUARE_SIZE, BUTTON_HEIGHT * (i++) + yStart1, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
			graphicsHolder.drawText(TranslationProvider.OPTIONS_MTR_DEFAULT_RAIL_3D.getMutableText(), SQUARE_SIZE, BUTTON_HEIGHT * (i++) + yStart1, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
			graphicsHolder.drawText(TranslationProvider.OPTIONS_MTR_USE_MTR_FONT.getMutableText(), SQUARE_SIZE, BUTTON_HEIGHT * (i++) + yStart1, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
			graphicsHolder.drawText(TranslationProvider.OPTIONS_MTR_DISABLE_SHADOWS_FOR_SHADERS.getMutableText(), SQUARE_SIZE, BUTTON_HEIGHT * (i++) + yStart1, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
			graphicsHolder.drawText(TranslationProvider.OPTIONS_MTR_FASTER_DOOR_CHECKS.getMutableText(), SQUARE_SIZE, BUTTON_HEIGHT * (i++) + yStart1, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
			graphicsHolder.drawText(TranslationProvider.OPTIONS_MTR_CHEAP_SHADOWS.getMutableText(), SQUARE_SIZE, BUTTON_HEIGHT * (i++) + yStart1, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
			graphicsHolder.drawText(TranslationProvider.OPTIONS_MTR_RAIL_LOD.getMutableText(), SQUARE_SIZE, BUTTON_HEIGHT * (i++) + yStart1, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
			graphicsHolder.drawText(TranslationProvider.OPTIONS_MTR_RAIL_SIMPLE_CHUNKS.getMutableText(), SQUARE_SIZE, BUTTON_HEIGHT * (i++) + yStart1, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
			graphicsHolder.drawText(TranslationProvider.OPTIONS_MTR_RAIL_FAR_CHUNKS.getMutableText(), SQUARE_SIZE, BUTTON_HEIGHT * (i++) + yStart1, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
			graphicsHolder.drawText(TranslationProvider.OPTIONS_MTR_MERGE_TRAIN_PARTS.getMutableText(), SQUARE_SIZE, BUTTON_HEIGHT * (i++) + yStart1, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
			graphicsHolder.drawText(TranslationProvider.OPTIONS_MTR_TRAIN_LOD.getMutableText(), SQUARE_SIZE, BUTTON_HEIGHT * (i++) + yStart1, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
			graphicsHolder.drawText(TranslationProvider.OPTIONS_MTR_TRAIN_INTERIOR_CHUNKS.getMutableText(), SQUARE_SIZE, BUTTON_HEIGHT * (i++) + yStart1, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
			graphicsHolder.drawText(TranslationProvider.OPTIONS_MTR_TRAIN_BOX_CHUNKS.getMutableText(), SQUARE_SIZE, BUTTON_HEIGHT * (i++) + yStart1, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
			graphicsHolder.drawText(TranslationProvider.OPTIONS_MTR_FASTER_MODEL_LOADING.getMutableText(), SQUARE_SIZE, BUTTON_HEIGHT * (i++) + yStart1, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
			graphicsHolder.drawText(TranslationProvider.OPTIONS_MTR_BACKGROUND_MODEL_LOADING.getMutableText(), SQUARE_SIZE, BUTTON_HEIGHT * (i++) + yStart1, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
			graphicsHolder.drawText(TranslationProvider.OPTIONS_MTR_FREE_UNUSED_MODELS.getMutableText(), SQUARE_SIZE, BUTTON_HEIGHT * (i++) + yStart1, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
			graphicsHolder.drawText(TranslationProvider.OPTIONS_MTR_SUPPORT_PATREON.getMutableText(), SQUARE_SIZE, BUTTON_HEIGHT * (i++) + yStart1, 0xFFFFFF66, false, GraphicsHolder.getDefaultLight());

			final int yStart2 = BUTTON_HEIGHT * (i + 1) + yStart1;
			String tierTitle = "";
			int y = 0;
			int x = 0;
			int maxWidth = 0;
			for (final Patreon patreon : Patreon.PATREON_LIST) {
				if (!patreon.tierTitle.equals(tierTitle)) {
					x += maxWidth + TEXT_PADDING;
					y = 0;
					final MutableText text = TextHelper.literal(patreon.tierTitle);
					maxWidth = GraphicsHolder.getTextWidth(text);
					graphicsHolder.drawText(text, SQUARE_SIZE - TEXT_PADDING + x, yStart2, patreon.tierColor, false, GraphicsHolder.getDefaultLight());
				} else if (y + yStart2 + TEXT_HEIGHT + SQUARE_SIZE > height) {
					x += maxWidth + TEXT_PADDING;
					y = 0;
					maxWidth = 0;
				}

				tierTitle = patreon.tierTitle;
				final MutableText text = patreon.tierAmount < 1000 ? TranslationProvider.OPTIONS_MTR_ANONYMOUS.getMutableText() : TextHelper.literal(patreon.name);
				maxWidth = Math.max(maxWidth, GraphicsHolder.getTextWidth(text));
				graphicsHolder.drawText(text, SQUARE_SIZE - TEXT_PADDING + x, yStart2 + y + TEXT_HEIGHT + TEXT_PADDING, ARGB_LIGHT_GRAY, false, GraphicsHolder.getDefaultLight());
				y += TEXT_HEIGHT + 2;
			}

			super.render(graphicsHolder, mouseX, mouseY, delta);
		} catch (Exception e) {
			Init.LOGGER.error("", e);
		}
	}

	@Override
	public boolean mouseScrolled2(double mouseX, double mouseY, double amount) {
		final int newScrollOffset = Math.max(0, Math.min(maxScrollOffset(), scrollOffset - (int) Math.round(amount * BUTTON_HEIGHT)));
		if (newScrollOffset != scrollOffset) {
			scrollOffset = newScrollOffset;
			positionWidgets();
		}
		return super.mouseScrolled2(mouseX, mouseY, amount);
	}

	@Override
	public void onClose2() {
		super.onClose2();
		client.setDynamicTextureResolution(sliderDynamicTextureResolution.getIntValue());
		client.setVehicleOscillationMultiplier(sliderTrainOscillationMultiplier.getIntValue() / 10.0);
		client.setRailSimpleChunks(sliderRailSimpleChunks.getIntValue());
		client.setRailFarChunks(sliderRailFarChunks.getIntValue());
		client.setTrainInteriorChunks(sliderTrainInteriorChunks.getIntValue());
		client.setTrainBoxChunks(sliderTrainBoxChunks.getIntValue());
		DynamicTextureCache.instance.refresh();
		Config.save();
	}

	private void positionWidgets() {
		scrollOffset = Math.min(scrollOffset, maxScrollOffset());
		final int startY = TEXT_PADDING - scrollOffset;
		int i = 1;
		IDrawing.setPositionAndWidth(buttonShowAnnouncementMessages, width - SQUARE_SIZE - BUTTON_WIDTH, startY + BUTTON_HEIGHT * (i++) + SQUARE_SIZE, BUTTON_WIDTH);
		IDrawing.setPositionAndWidth(buttonUseTTSAnnouncements, width - SQUARE_SIZE - BUTTON_WIDTH, startY + BUTTON_HEIGHT * (i++) + SQUARE_SIZE, BUTTON_WIDTH);
		IDrawing.setPositionAndWidth(buttonHideTranslucentParts, width - SQUARE_SIZE - BUTTON_WIDTH, startY + BUTTON_HEIGHT * (i++) + SQUARE_SIZE, BUTTON_WIDTH);
		IDrawing.setPositionAndWidth(buttonHideRainInsideVehicles, width - SQUARE_SIZE - BUTTON_WIDTH, startY + BUTTON_HEIGHT * (i++) + SQUARE_SIZE, BUTTON_WIDTH);
		IDrawing.setPositionAndWidth(buttonLanguageOptions, width - SQUARE_SIZE - BUTTON_WIDTH, startY + BUTTON_HEIGHT * (i++) + SQUARE_SIZE, BUTTON_WIDTH);
		IDrawing.setPositionAndWidth(sliderDynamicTextureResolution, width - SQUARE_SIZE - BUTTON_WIDTH, startY + BUTTON_HEIGHT * (i++) + SQUARE_SIZE, BUTTON_WIDTH - TEXT_PADDING - GraphicsHolder.getTextWidth("100%"));
		IDrawing.setPositionAndWidth(sliderTrainOscillationMultiplier, width - SQUARE_SIZE - BUTTON_WIDTH, startY + BUTTON_HEIGHT * (i++) + SQUARE_SIZE, BUTTON_WIDTH - TEXT_PADDING - GraphicsHolder.getTextWidth("100%"));
		IDrawing.setPositionAndWidth(buttonDefaultRail3D, width - SQUARE_SIZE - BUTTON_WIDTH, startY + BUTTON_HEIGHT * (i++) + SQUARE_SIZE, BUTTON_WIDTH);
		IDrawing.setPositionAndWidth(buttonUseMTRFont, width - SQUARE_SIZE - BUTTON_WIDTH, startY + BUTTON_HEIGHT * (i++) + SQUARE_SIZE, BUTTON_WIDTH);
		IDrawing.setPositionAndWidth(buttonDisableShadowsForShaders, width - SQUARE_SIZE - BUTTON_WIDTH, startY + BUTTON_HEIGHT * (i++) + SQUARE_SIZE, BUTTON_WIDTH);
		IDrawing.setPositionAndWidth(buttonFasterDoorChecks, width - SQUARE_SIZE - BUTTON_WIDTH, startY + BUTTON_HEIGHT * (i++) + SQUARE_SIZE, BUTTON_WIDTH);
		IDrawing.setPositionAndWidth(buttonCheapShadows, width - SQUARE_SIZE - BUTTON_WIDTH, startY + BUTTON_HEIGHT * (i++) + SQUARE_SIZE, BUTTON_WIDTH);
		IDrawing.setPositionAndWidth(buttonRailLod, width - SQUARE_SIZE - BUTTON_WIDTH, startY + BUTTON_HEIGHT * (i++) + SQUARE_SIZE, BUTTON_WIDTH);
		IDrawing.setPositionAndWidth(sliderRailSimpleChunks, width - SQUARE_SIZE - BUTTON_WIDTH, startY + BUTTON_HEIGHT * (i++) + SQUARE_SIZE, BUTTON_WIDTH - TEXT_PADDING - GraphicsHolder.getTextWidth("100%"));
		IDrawing.setPositionAndWidth(sliderRailFarChunks, width - SQUARE_SIZE - BUTTON_WIDTH, startY + BUTTON_HEIGHT * (i++) + SQUARE_SIZE, BUTTON_WIDTH - TEXT_PADDING - GraphicsHolder.getTextWidth("100%"));
		IDrawing.setPositionAndWidth(buttonMergeTrainParts, width - SQUARE_SIZE - BUTTON_WIDTH, startY + BUTTON_HEIGHT * (i++) + SQUARE_SIZE, BUTTON_WIDTH);
		IDrawing.setPositionAndWidth(buttonTrainLod, width - SQUARE_SIZE - BUTTON_WIDTH, startY + BUTTON_HEIGHT * (i++) + SQUARE_SIZE, BUTTON_WIDTH);
		IDrawing.setPositionAndWidth(sliderTrainInteriorChunks, width - SQUARE_SIZE - BUTTON_WIDTH, startY + BUTTON_HEIGHT * (i++) + SQUARE_SIZE, BUTTON_WIDTH - TEXT_PADDING - GraphicsHolder.getTextWidth("100%"));
		IDrawing.setPositionAndWidth(sliderTrainBoxChunks, width - SQUARE_SIZE - BUTTON_WIDTH, startY + BUTTON_HEIGHT * (i++) + SQUARE_SIZE, BUTTON_WIDTH - TEXT_PADDING - GraphicsHolder.getTextWidth("100%"));
		IDrawing.setPositionAndWidth(buttonFasterModelLoading, width - SQUARE_SIZE - BUTTON_WIDTH, startY + BUTTON_HEIGHT * (i++) + SQUARE_SIZE, BUTTON_WIDTH);
		IDrawing.setPositionAndWidth(buttonBackgroundModelLoading, width - SQUARE_SIZE - BUTTON_WIDTH, startY + BUTTON_HEIGHT * (i++) + SQUARE_SIZE, BUTTON_WIDTH);
		IDrawing.setPositionAndWidth(buttonFreeUnusedModels, width - SQUARE_SIZE - BUTTON_WIDTH, startY + BUTTON_HEIGHT * (i++) + SQUARE_SIZE, BUTTON_WIDTH);
		IDrawing.setPositionAndWidth(buttonSupportPatreon, width - SQUARE_SIZE - BUTTON_WIDTH, startY + BUTTON_HEIGHT * i + SQUARE_SIZE, BUTTON_WIDTH);
		rowCount = i;
	}

	/** Scrolls far enough to show every row, the Patreon tier titles and their first names. */
	private int maxScrollOffset() {
		final int contentHeight = BUTTON_HEIGHT * (rowCount + 2) + SQUARE_SIZE + TEXT_PADDING + TEXT_PADDING / 2 + TEXT_HEIGHT * 2 + TEXT_PADDING + SQUARE_SIZE;
		return Math.max(0, contentHeight - height);
	}

	private void drawHeader(GraphicsHolder graphicsHolder) {
		MutableText titleText = TranslationProvider.GUI_MTR_BRAND.getMutableText();

		// Logo
		graphicsHolder.push();
		graphicsHolder.translate((getWidthMapped() - (GraphicsHolder.getTextWidth(titleText) * 1.5f) - HEADER_LOGO_SIZE - TEXT_PADDING) / 2, 0, 0);
		GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
		guiDrawing.beginDrawingTexture(HEADER_LOGO);
		guiDrawing.drawTexture(0, 0, HEADER_LOGO_SIZE, HEADER_LOGO_SIZE, 0, 0, 1, 1);
		guiDrawing.finishDrawingTexture();

		graphicsHolder.translate(HEADER_LOGO_SIZE, 0, 0);
		graphicsHolder.translate(TEXT_PADDING, 0, 0);

		// Brand Name
		graphicsHolder.push();
		graphicsHolder.scale(1.5f, 1.5f, 1.5f);
		graphicsHolder.drawText(titleText, 0, TEXT_PADDING, ARGB_WHITE, true, GraphicsHolder.getDefaultLight());
		graphicsHolder.pop();

		// Version
		graphicsHolder.drawText(Keys.MOD_VERSION, 0, (TEXT_PADDING * 4), ARGB_WHITE, true, GraphicsHolder.getDefaultLight());
		graphicsHolder.pop();
	}

	private static void setButtonText(ButtonWidget button, boolean state) {
		button.setMessage((state ? TranslationProvider.OPTIONS_MTR_ON : TranslationProvider.OPTIONS_MTR_OFF).getText());
	}
}
