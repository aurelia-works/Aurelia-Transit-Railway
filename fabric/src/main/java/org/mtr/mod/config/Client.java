package org.mtr.mod.config;

import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.tool.Utilities;
import org.mtr.mod.Keys;
import org.mtr.mod.generated.config.ClientSchema;
import org.mtr.mod.resource.PantographEnd;

public final class Client extends ClientSchema {

	public static final int DYNAMIC_RESOLUTION_COUNT = 8;
	public static final int TRAIN_OSCILLATION_COUNT = 15;
	public static final int RAIL_SIMPLE_CHUNKS_MAX = 16;
	public static final int RAIL_FAR_CHUNKS_MAX = 32;
	public static final int TRAIN_INTERIOR_CHUNKS_MAX = 16;
	public static final int TRAIN_BOX_CHUNKS_MAX = 32;
	public static final double PANTOGRAPH_RAISE_SECONDS_MAX = 10;
	public static final double PANTOGRAPH_STAY_UP_KILOMETERS_PER_HOUR_MAX = 200;
	public static final double SOUND_MAX_PITCH_MIN = 2;
	public static final double SOUND_MAX_PITCH_MAX = 8;
	public static final double MOTOR_PITCH_SMOOTHING_MAX = 0.4;
	public static final int DOOR_CHIME_COUNT = 3;

	public Client(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public boolean getChatAnnouncements() {
		return chatAnnouncements;
	}

	public boolean getTextToSpeechAnnouncements() {
		return textToSpeechAnnouncements;
	}

	public boolean getHideTranslucentParts() {
		return hideTranslucentParts;
	}

	public boolean getHideRainInsideVehicles() {
		return hideRainInsideVehicles;
	}

	public boolean getFasterDoorChecks() {
		return fasterDoorChecks;
	}

	public boolean getCheapShadows() {
		return cheapShadows;
	}

	public LanguageDisplay getLanguageDisplay() {
		return languageDisplay;
	}

	public int getDynamicTextureResolution() {
		return (int) dynamicTextureResolution;
	}

	public double getVehicleOscillationMultiplier() {
		return vehicleOscillationMultiplier;
	}

	public boolean getDefaultRail3D() {
		return defaultRail3D;
	}

	public boolean getRailLod() {
		return railLod;
	}

	public int getRailSimpleChunks() {
		return (int) railSimpleChunks;
	}

	public int getRailFarChunks() {
		return (int) railFarChunks;
	}

	public boolean getPerfOnF3() {
		return perfOnF3;
	}

	public boolean getShaderPackFastPath() {
		return shaderPackFastPath;
	}

	public boolean getMergeTrainParts() {
		return mergeTrainParts;
	}

	public boolean getTrainLod() {
		return trainLod;
	}

	public int getTrainInteriorChunks() {
		return (int) trainInteriorChunks;
	}

	public int getTrainBoxChunks() {
		return (int) trainBoxChunks;
	}

	public boolean getUseMTRFont() {
		return useMTRFont;
	}

	public boolean getDisableShadowsForShaders() {
		return disableShadowsForShaders;
	}

	public boolean getFasterModelLoading() {
		return fasterModelLoading;
	}

	public boolean getBackgroundModelLoading() {
		return backgroundModelLoading;
	}

	public boolean getFreeUnusedModels() {
		return freeUnusedModels;
	}

	public boolean matchesPreloadResourcePattern(String id) {
		return id.replaceFirst(preloadResourcePattern, "").isEmpty();
	}

	public boolean showBetaWarningScreen() {
		return false && !Keys.MOD_VERSION.equals(betaWarningVersion);
	}

	public void toggleChatAnnouncements() {
		chatAnnouncements = !chatAnnouncements;
	}

	public void toggleTextToSpeechAnnouncements() {
		textToSpeechAnnouncements = !textToSpeechAnnouncements;
	}

	public void toggleHideTranslucentParts() {
		hideTranslucentParts = !hideTranslucentParts;
	}

	public void toggleHideRainInsideVehicles() {
		hideRainInsideVehicles = !hideRainInsideVehicles;
	}

	public void cycleLanguageDisplay() {
		languageDisplay = LanguageDisplay.values()[(languageDisplay.ordinal() + 1) % LanguageDisplay.values().length];
	}

	public void setDynamicTextureResolution(int dynamicTextureResolution) {
		this.dynamicTextureResolution = Utilities.clamp(dynamicTextureResolution, 0, DYNAMIC_RESOLUTION_COUNT);
	}

	public void setVehicleOscillationMultiplier(double trainOscillationMultiplier) {
		this.vehicleOscillationMultiplier = Utilities.clamp(trainOscillationMultiplier, 0, (TRAIN_OSCILLATION_COUNT / 10.0));
	}

	public void toggleDefaultRail3D() {
		defaultRail3D = !defaultRail3D;
	}

	public void toggleRailLod() {
		railLod = !railLod;
	}

	public void setRailSimpleChunks(int railSimpleChunks) {
		this.railSimpleChunks = Utilities.clamp(railSimpleChunks, 0, RAIL_SIMPLE_CHUNKS_MAX);
	}

	public void setRailFarChunks(int railFarChunks) {
		this.railFarChunks = Utilities.clamp(railFarChunks, 0, RAIL_FAR_CHUNKS_MAX);
	}

	public void togglePerfOnF3() {
		perfOnF3 = !perfOnF3;
	}

	public void toggleShaderPackFastPath() {
		shaderPackFastPath = !shaderPackFastPath;
	}

	public void toggleMergeTrainParts() {
		mergeTrainParts = !mergeTrainParts;
	}

	public void toggleTrainLod() {
		trainLod = !trainLod;
	}

	public void setTrainInteriorChunks(int trainInteriorChunks) {
		this.trainInteriorChunks = Utilities.clamp(trainInteriorChunks, 0, TRAIN_INTERIOR_CHUNKS_MAX);
	}

	public void setTrainBoxChunks(int trainBoxChunks) {
		this.trainBoxChunks = Utilities.clamp(trainBoxChunks, 0, TRAIN_BOX_CHUNKS_MAX);
	}

	public void toggleUseMTRFont() {
		useMTRFont = !useMTRFont;
	}

	public void toggleDisableShadowsForShaders() {
		disableShadowsForShaders = !disableShadowsForShaders;
	}

	public void toggleFasterDoorChecks() {
		fasterDoorChecks = !fasterDoorChecks;
	}

	public void toggleCheapShadows() {
		cheapShadows = !cheapShadows;
	}

	public void toggleFasterModelLoading() {
		fasterModelLoading = !fasterModelLoading;
	}

	public void toggleBackgroundModelLoading() {
		backgroundModelLoading = !backgroundModelLoading;
	}

	public void toggleFreeUnusedModels() {
		freeUnusedModels = !freeUnusedModels;
	}

	public void hideBetaWarningScreen() {
		betaWarningVersion = Keys.MOD_VERSION;
	}

	public boolean getPantographAnimation() {
		return pantographAnimation;
	}

	public double getPantographRaiseSeconds() {
		return pantographRaiseSeconds;
	}

	/**
	 * The end used by pantograph parts that do not choose one. {@code DEFAULT} itself means both ends.
	 */
	public PantographEnd getPantographDefaultEnd() {
		return PantographEnd.values()[Utilities.clamp((int) pantographDefaultEnd, 0, PantographEnd.values().length - 1)];
	}

	public double getPantographStayUpKilometersPerHour() {
		return pantographStayUpKilometersPerHour;
	}

	public void togglePantographAnimation() {
		pantographAnimation = !pantographAnimation;
	}

	public void setPantographRaiseSeconds(double pantographRaiseSeconds) {
		this.pantographRaiseSeconds = Utilities.clamp(pantographRaiseSeconds, 0, PANTOGRAPH_RAISE_SECONDS_MAX);
	}

	public void cyclePantographDefaultEnd() {
		// front, rear, both; DEFAULT means "both" here too, so the panel skips it
		final int next = (int) pantographDefaultEnd + 1;
		pantographDefaultEnd = next < 1 || next >= PantographEnd.values().length ? 1 : next;
	}

	public void setPantographStayUpKilometersPerHour(double pantographStayUpKilometersPerHour) {
		this.pantographStayUpKilometersPerHour = Utilities.clamp(pantographStayUpKilometersPerHour, 0, PANTOGRAPH_STAY_UP_KILOMETERS_PER_HOUR_MAX);
	}

	public boolean getSoundWidePitch() {
		return soundWidePitch;
	}

	public void toggleSoundWidePitch() {
		soundWidePitch = !soundWidePitch;
	}

	public double getSoundMaxPitch() {
		return Utilities.clamp(soundMaxPitch, SOUND_MAX_PITCH_MIN, SOUND_MAX_PITCH_MAX);
	}

	public void setSoundMaxPitch(double soundMaxPitch) {
		this.soundMaxPitch = Utilities.clamp(soundMaxPitch, SOUND_MAX_PITCH_MIN, SOUND_MAX_PITCH_MAX);
	}

	public double getMotorPitchSmoothing() {
		return Utilities.clamp(motorPitchSmoothing, 0, MOTOR_PITCH_SMOOTHING_MAX);
	}

	public void setMotorPitchSmoothing(double motorPitchSmoothing) {
		this.motorPitchSmoothing = Utilities.clamp(motorPitchSmoothing, 0, MOTOR_PITCH_SMOOTHING_MAX);
	}

	/**
	 * 0 is off, 1 two-tone, 2 three-tone.
	 */
	public int getDoorChime() {
		return Utilities.clamp((int) doorChime, 0, DOOR_CHIME_COUNT - 1);
	}

	public void cycleDoorChime() {
		doorChime = (getDoorChime() + 1) % DOOR_CHIME_COUNT;
	}

	public double getDoorChimeVolume() {
		return Utilities.clamp(doorChimeVolume, 0, 1);
	}

	public void setDoorChimeVolume(double doorChimeVolume) {
		this.doorChimeVolume = Utilities.clamp(doorChimeVolume, 0, 1);
	}

	public double getFlangeVolume() {
		return Utilities.clamp(flangeVolume, 0, 1);
	}

	public void setFlangeVolume(double flangeVolume) {
		this.flangeVolume = Utilities.clamp(flangeVolume, 0, 1);
	}
}
