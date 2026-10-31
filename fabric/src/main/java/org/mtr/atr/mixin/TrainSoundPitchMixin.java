package org.mtr.atr.mixin;

import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.mtr.mod.Init;
import org.mtr.mod.config.Config;
import org.mtr.mod.config.Client;
import org.mtr.mod.sound.SoundTuning;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla clamps every sound's pitch to 0.5-2. Train sounds (mod id {@code mtr}) get the wider range set on the Aurelia
 * panel, so BVE motor tables that ask for more than 2 sound as written.
 */
@Mixin(SoundSystem.class)
public abstract class TrainSoundPitchMixin {

	@Inject(method = "getAdjustedPitch", at = @At("HEAD"), cancellable = true)
	private void atr$widePitch(SoundInstance sound, CallbackInfoReturnable<Float> callbackInfoReturnable) {
		final Client config = Config.getClient();
		if (config.getSoundWidePitch() && Init.MOD_ID.equals(sound.getId().getNamespace())) {
			callbackInfoReturnable.setReturnValue(SoundTuning.pitch(sound.getPitch(), (float) config.getSoundMaxPitch()));
		}
	}
}
