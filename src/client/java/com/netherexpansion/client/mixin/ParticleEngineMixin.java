package com.netherexpansion.client.mixin;

import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.netherexpansion.entity.WatcherEffects;

/** The Unveiling: Nether particles freeze in the air for a moment. */
@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void netherexpansion$freezeParticles(CallbackInfo ci) {
		if (WatcherEffects.particleFreezeTicks > 0) {
			WatcherEffects.particleFreezeTicks--;
			ci.cancel();
		}
	}
}
