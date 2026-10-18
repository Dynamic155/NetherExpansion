package com.netherexpansion.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

import com.netherexpansion.NetherExpansion;

/** Sound events. Their files are set in assets/netherexpansion/sounds.json. */
public final class ModSounds {
	public static final SoundEvent WATCHER_AMBIENT = register("entity.underworld_watcher.ambient");
	public static final SoundEvent WATCHER_WHISPER = register("entity.underworld_watcher.whisper");
	public static final SoundEvent WATCHER_STEP = register("entity.underworld_watcher.step");
	public static final SoundEvent WATCHER_HEARTBEAT = register("entity.underworld_watcher.heartbeat");
	public static final SoundEvent WATCHER_HURT = register("entity.underworld_watcher.hurt");
	public static final SoundEvent WATCHER_DEATH = register("entity.underworld_watcher.death");
	public static final SoundEvent WATCHER_SHATTER = register("entity.underworld_watcher.shatter");
	public static final SoundEvent WATCHER_HUNT = register("entity.underworld_watcher.hunt");
	public static final SoundEvent WATCHER_CLAW_WINDUP = register("entity.underworld_watcher.claw_windup");
	public static final SoundEvent WATCHER_CLAW_HIT = register("entity.underworld_watcher.claw_hit");
	public static final SoundEvent WATCHER_RIFT_OUT = register("entity.underworld_watcher.rift_out");
	public static final SoundEvent WATCHER_RIFT_IN = register("entity.underworld_watcher.rift_in");
	public static final SoundEvent WATCHER_PULSE_CHARGE = register("entity.underworld_watcher.pulse_charge");
	public static final SoundEvent WATCHER_PULSE_SCREAM = register("entity.underworld_watcher.pulse_scream");
	public static final SoundEvent WATCHER_GRASP_SLAM = register("entity.underworld_watcher.grasp_slam");
	public static final SoundEvent WATCHER_GRASP_CRACK = register("entity.underworld_watcher.grasp_crack");
	public static final SoundEvent WATCHER_GRASP_ERUPT = register("entity.underworld_watcher.grasp_erupt");
	public static final SoundEvent WATCHER_UNVEIL = register("entity.underworld_watcher.unveil");
	public static final SoundEvent WATCHER_FALSE_SIGHT = register("entity.underworld_watcher.false_sight");
	public static final SoundEvent WATCHER_ILLUSION_BREAK = register("entity.underworld_watcher.illusion_break");
	public static final SoundEvent WATCHER_REND_ERUPT = register("entity.underworld_watcher.rend_erupt");
	public static final SoundEvent WATCHER_UNBLINKING_CHARGE = register("entity.underworld_watcher.unblinking_charge");
	public static final SoundEvent WATCHER_UNBLINKING_FIRE = register("entity.underworld_watcher.unblinking_fire");
	public static final SoundEvent WATCHER_UNBLINKING_FAIL = register("entity.underworld_watcher.unblinking_fail");

	private ModSounds() {
	}

	private static SoundEvent register(String name) {
		var id = NetherExpansion.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void init() {
		// Loads the class so the fields above register.
	}
}
