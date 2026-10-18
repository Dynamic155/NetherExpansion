package com.netherexpansion.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.netherexpansion.item.UmbralPerks;

/** Umbral Netherite's perks: damage against and from Underworld mobs, and the helmet's sight. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float netherexpansion$umbralDamage(float amount, @Local(argsOnly = true) DamageSource source) {
		return UmbralPerks.modifyDamage((LivingEntity) (Object) this, source, amount);
	}

	@Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
	private void netherexpansion$umbralSight(MobEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
		if (UmbralPerks.blocksEffect((LivingEntity) (Object) this, effect)) {
			cir.setReturnValue(false);
		}
	}
}
