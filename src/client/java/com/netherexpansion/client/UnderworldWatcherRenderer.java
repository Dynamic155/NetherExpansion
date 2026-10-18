package com.netherexpansion.client;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

import com.netherexpansion.entity.UnderworldWatcher;

/**
 * Draws the Watcher with its glowing eye, turns its head toward what it looks at, and makes the body skip
 * sideways for a frame now and then (and when hit, in place of knockback).
 *
 * <p>The {@code & GeoRenderState} bound is needed because GeckoLib's interface injection isn't visible at
 * compile time.
 */
public class UnderworldWatcherRenderer<R extends LivingEntityRenderState & GeoRenderState> extends GeoEntityRenderer<UnderworldWatcher, R> {
	public static final DataTicket<String> EYE = DataTicket.create("netherexpansion_watcher_eye", String.class);
	public static final DataTicket<Float> GLITCH = DataTicket.create("netherexpansion_watcher_glitch", Float.class);
	public static final DataTicket<Float> UNVEIL = DataTicket.create("netherexpansion_watcher_unveil", Float.class);

	/** How far each mask half swings open on its outer hinge, in radians. */
	private static final float MASK_OPEN_ANGLE = 65.0F * Mth.DEG_TO_RAD;

	public UnderworldWatcherRenderer(EntityRendererProvider.Context context) {
		super(context, new UnderworldWatcherModel());
		withRenderLayer(AutoGlowingGeoLayer::new);
		this.shadowRadius = 0.6F;
	}

	@Override
	public void addRenderData(UnderworldWatcher watcher, Void relatedObject, R state, float partialTick) {
		state.addGeckolibData(EYE, watcher.eyeVariant());
		state.addGeckolibData(GLITCH, glitchOffset(watcher));
		int unveil = watcher.getUnveilTicks();
		boolean opening = unveil > 0 && unveil < UnderworldWatcher.UNVEIL_LENGTH;
		state.addGeckolibData(UNVEIL, unveil + (opening ? partialTick : 0.0F));
	}

	/** Sideways offset in blocks: a jolt when hurt, and a skipped frame every few seconds. */
	private static float glitchOffset(UnderworldWatcher watcher) {
		float side = watcher.getId() % 2 == 0 ? 1.0F : -1.0F;
		if (watcher.isDeadOrDying()) {
			// Violent glitching while the veil tears, until it shatters
			if (watcher.deathTime < 50 && (watcher.deathTime + watcher.getId()) % 5 < 2) {
				return (watcher.deathTime % 2 == 0 ? 0.3F : -0.3F) * side;
			}
			return 0.0F;
		}
		if (watcher.hurtTime > 0 && watcher.hurtTime >= watcher.hurtDuration - 2) {
			return 0.35F * side;
		}
		int cycle = watcher.tickCount + watcher.getId() * 7;
		// False Sight copies skip frames more often than the real one
		if (cycle % (watcher.isIllusion() ? 31 : 97) < 2) {
			return ((cycle / 97) % 2 == 0 ? 0.15F : -0.15F);
		}
		return 0.0F;
	}

	/** No vanilla tip-over on death: the death animation does all the moving (and would slide sideways if tipped). */
	@Override
	protected float getDeathMaxRotation(GeoRenderState renderState) {
		return 0.0F;
	}

	@Override
	public void adjustRenderPose(RenderPassInfo<R> renderPassInfo) {
		super.adjustRenderPose(renderPassInfo);
		float glitch = renderPassInfo.getOrDefaultGeckolibData(GLITCH, 0.0F);
		if (glitch != 0.0F) {
			renderPassInfo.poseStack().translate(glitch, 0.0F, 0.0F);
		}
	}

	@Override
	public void adjustModelBonesForRender(RenderPassInfo<R> renderPassInfo, BoneSnapshots snapshots) {
		R state = renderPassInfo.renderState();
		snapshots.ifPresent("head", head -> {
			head.setRotX(head.getRotX() + state.xRot * Mth.DEG_TO_RAD);
			head.setRotY(head.getRotY() + state.yRot * Mth.DEG_TO_RAD);
		});

		// The Unveiling: the mask halves swing outward on their hinges, then the eye splits in three
		float unveil = renderPassInfo.getOrDefaultGeckolibData(UNVEIL, 0.0F);
		float open = Mth.clamp((unveil - UnderworldWatcher.UNVEIL_MASK_START) / 20.0F, 0.0F, 1.0F);
		boolean split = unveil >= UnderworldWatcher.UNVEIL_SPLIT_TICK;
		if (open > 0.0F) {
			snapshots.ifPresent("mask_left", mask -> mask.setRotY(mask.getRotY() + open * MASK_OPEN_ANGLE));
			snapshots.ifPresent("mask_right", mask -> mask.setRotY(mask.getRotY() - open * MASK_OPEN_ANGLE));
		}
		snapshots.ifPresent("eye", eye -> eye.skipRender(split));
		snapshots.ifPresent("eyes_split", eyes -> eyes.skipRender(!split));
	}
}
