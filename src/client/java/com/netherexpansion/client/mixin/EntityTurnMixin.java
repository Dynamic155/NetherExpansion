package com.netherexpansion.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.netherexpansion.client.ExecutionLock;

/** The execution: the held player cannot turn their camera away from the Watcher. */
@Mixin(Entity.class)
public class EntityTurnMixin {
	@Inject(method = "turn", at = @At("HEAD"), cancellable = true)
	private void netherexpansion$lockCamera(double yaw, double pitch, CallbackInfo ci) {
		if (ExecutionLock.isHeld() && (Object) this == Minecraft.getInstance().player) {
			ci.cancel();
		}
	}
}
