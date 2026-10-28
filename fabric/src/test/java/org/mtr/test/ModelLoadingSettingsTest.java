package org.mtr.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mtr.mod.resource.ModelLoadingSettings;

import static org.junit.jupiter.api.Assertions.*;

class ModelLoadingSettingsTest {

	@AfterEach
	void clearOverride() {
		ModelLoadingSettings.override(null);
	}

	@Test
	void allThreeSwitchesDefaultToOn() {
		ModelLoadingSettings.override(null);
		assertTrue(ModelLoadingSettings.fasterModelLoading());
		assertTrue(ModelLoadingSettings.backgroundModelLoading());
		assertTrue(ModelLoadingSettings.freeUnusedModels());
	}

	@Test
	void overrideSetsEachSwitchAlone() {
		ModelLoadingSettings.override(new boolean[]{false, true, false});
		assertFalse(ModelLoadingSettings.fasterModelLoading());
		assertTrue(ModelLoadingSettings.backgroundModelLoading());
		assertFalse(ModelLoadingSettings.freeUnusedModels());
	}

	@Test
	void shaderRecompileIsSkippedOnlyWhenEveryConditionHolds() {
		assertTrue(ModelLoadingSettings.shouldSkipShaderRecompile(true, false, true, false));
		// Switch off: MTR recompiles on every build.
		assertFalse(ModelLoadingSettings.shouldSkipShaderRecompile(false, false, true, false));
		// Joban Client Mod does it already.
		assertFalse(ModelLoadingSettings.shouldSkipShaderRecompile(true, true, true, false));
		// Nothing compiled yet.
		assertFalse(ModelLoadingSettings.shouldSkipShaderRecompile(true, false, false, false));
		// Resource reload (loading overlay): shaders may have changed.
		assertFalse(ModelLoadingSettings.shouldSkipShaderRecompile(true, false, true, true));
	}
}
