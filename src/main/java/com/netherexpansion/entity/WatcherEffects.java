package com.netherexpansion.entity;

/**
 * Client-side effects of the Unveiling that live outside the entity. Set from entity events on the client and
 * read by client mixins; never touched on a dedicated server.
 */
public final class WatcherEffects {
	/** While above zero, every particle in the world hangs frozen in the air (counts down each client tick). */
	public static int particleFreezeTicks;

	private WatcherEffects() {
	}
}
