package io.github.jan1a234.goblinforest.registry;

import io.github.jan1a234.goblinforest.GoblinForest;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * Eigene Klänge der Mod (assets/goblinforest/sounds.json). Sie sind selbst synthetisiert,
 * siehe tools/generate_sounds.py.
 */
public final class ModSounds {
	/** Zwei Takte Kriegstrommeln, nahtlos wiederholbar; spielt während der Schlacht als Musik. */
	public static final SoundEvent WAR_DRUMS = register("war_drums");
	/** Münzklimpern beim Kopfgeld. */
	public static final SoundEvent COINS = register("coins");
	/** Kriegshorn zum Schlachtbeginn und beim Sammelruf. */
	public static final SoundEvent WAR_HORN = register("war_horn");

	private ModSounds() {
	}

	private static SoundEvent register(String name) {
		Identifier id = GoblinForest.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	/** Lädt die Klasse und registriert damit alle Klänge. */
	public static void register() {
	}
}
