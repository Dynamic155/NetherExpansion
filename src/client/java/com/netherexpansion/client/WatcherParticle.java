package com.netherexpansion.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;

/** The Underworld Watcher's particles. One class; each kind sets its own motion, life and look. */
public class WatcherParticle extends SingleQuadParticle {
	private static final int FULL_BRIGHT = 0xF000F0;

	public enum Kind {
		/** Black ash flakes that drift down and tumble. */
		ASH,
		/** Embers that rise, flicker through their frames, and glow in the dark. */
		EMBER,
		/** Violet motes that sink slowly and fade out well before the ground. */
		VOID_MOTE,
		/** Obsidian shards that fall, spin, and sink away once they land. */
		OBSIDIAN_SHARD,
		/** White-violet sparks that flare and fade. */
		EYE_LIGHT
	}

	private final SpriteSet sprites;
	private final Kind kind;
	private float spin;

	public WatcherParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites, Kind kind) {
		super(level, x, y, z, sprites.first());
		this.sprites = sprites;
		this.kind = kind;
		// Keep the velocity exactly as sent; the base constructor would randomize it
		this.xd = xd;
		this.yd = yd;
		this.zd = zd;
		switch (kind) {
			case ASH -> {
				setSprite(sprites.get(random));
				lifetime = 40 + random.nextInt(40);
				gravity = 0.05F;
				friction = 0.92F;
				quadSize = 0.07F + random.nextFloat() * 0.05F;
				spin = (random.nextFloat() - 0.5F) * 0.2F;
				hasPhysics = true;
			}
			case EMBER -> {
				lifetime = 18 + random.nextInt(20);
				gravity = -0.1F;
				friction = 0.94F;
				quadSize = 0.05F + random.nextFloat() * 0.04F;
				hasPhysics = false;
			}
			case VOID_MOTE -> {
				lifetime = 14 + random.nextInt(14);
				gravity = 0.08F;
				friction = 0.9F;
				quadSize = 0.06F + random.nextFloat() * 0.03F;
				hasPhysics = false;
			}
			case OBSIDIAN_SHARD -> {
				setSprite(sprites.get(random));
				lifetime = 50 + random.nextInt(30);
				gravity = 1.0F;
				friction = 0.98F;
				quadSize = 0.09F + random.nextFloat() * 0.07F;
				spin = (random.nextFloat() - 0.5F) * 0.5F;
				hasPhysics = true;
			}
			case EYE_LIGHT -> {
				lifetime = 10 + random.nextInt(10);
				gravity = 0.0F;
				friction = 0.86F;
				quadSize = 0.1F + random.nextFloat() * 0.06F;
				hasPhysics = false;
			}
		}
		if (kind == Kind.EMBER || kind == Kind.VOID_MOTE || kind == Kind.EYE_LIGHT) {
			setSpriteFromAge(sprites);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (removed) {
			return;
		}
		oRoll = roll;
		roll += spin;
		float life = (float) age / lifetime;
		switch (kind) {
			case EMBER -> setSpriteFromAge(sprites);
			case VOID_MOTE, EYE_LIGHT -> {
				setSpriteFromAge(sprites);
				setAlpha(1.0F - life * life);
			}
			case ASH -> setAlpha(life < 0.8F ? 1.0F : (1.0F - life) / 0.2F);
			case OBSIDIAN_SHARD -> {
				// Once landed, it sinks: shrinks and stops spinning
				if (onGround) {
					quadSize *= 0.93F;
					spin = 0.0F;
				}
			}
		}
	}

	@Override
	protected int getLightCoords(float partialTick) {
		return kind == Kind.ASH || kind == Kind.OBSIDIAN_SHARD ? super.getLightCoords(partialTick) : FULL_BRIGHT;
	}

	@Override
	protected Layer getLayer() {
		return Layer.TRANSLUCENT;
	}
}
