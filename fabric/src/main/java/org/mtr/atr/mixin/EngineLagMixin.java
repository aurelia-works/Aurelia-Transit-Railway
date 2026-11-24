package org.mtr.atr.mixin;

import org.mtr.core.simulation.Simulator;
import org.mtr.mod.engine.EngineLag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Times each simulator tick for the Engine tab's lag readout. Two clock reads a tick, nothing else. */
@Mixin(value = Simulator.class, remap = false)
public abstract class EngineLagMixin {

	@Unique
	private long atr$tickStart;

	@Inject(method = "tick()V", at = @At("HEAD"))
	private void atr$startTiming(CallbackInfo callback) {
		atr$tickStart = System.nanoTime();
	}

	@Inject(method = "tick()V", at = @At("RETURN"))
	private void atr$stopTiming(CallbackInfo callback) {
		EngineLag.record(((Simulator) (Object) this).dimension, System.nanoTime() - atr$tickStart);
	}
}
