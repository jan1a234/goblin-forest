package io.github.jan1a234.goblinforest.client;

import io.github.jan1a234.goblinforest.game.MatchPhase;
import io.github.jan1a234.goblinforest.net.MatchStatePayload;
import io.github.jan1a234.goblinforest.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/**
 * Kriegstrommeln als Schlachtmusik: laufen in Schleife, solange die Schlacht tobt, und werden im Sudden Death
 * schneller und lauter. Läuft über den Musik-Regler, die normale Minecraft-Musik pausiert so lange.
 */
final class WarDrums {
	private static final float VOLUME = 0.55F;
	private static final float SUDDEN_DEATH_VOLUME = 0.8F;
	private static final float SUDDEN_DEATH_PITCH = 1.15F;

	private static Loop loop;

	private WarDrums() {
	}

	static void tick(Minecraft client) {
		boolean battle = battleRunning();
		if (battle) {
			if (client.getMusicManager().getCurrentMusicTranslationKey() != null) {
				client.getMusicManager().stopPlaying();
			}
			if (loop == null || !client.getSoundManager().isActive(loop)) {
				loop = new Loop();
				client.getSoundManager().play(loop);
			}
		} else if (loop != null) {
			client.getSoundManager().stop(loop);
			loop = null;
		}
	}

	static void reset(Minecraft client) {
		if (loop != null) {
			client.getSoundManager().stop(loop);
			loop = null;
		}
	}

	private static boolean battleRunning() {
		MatchStatePayload state = ClientMatchState.get();
		return ClientMatchState.active() && state != null && state.phase() == MatchPhase.BATTLE.ordinal();
	}

	/** Nicht-räumliche Endlosschleife; Tonhöhe und Lautstärke folgen dem Spielstand. */
	private static final class Loop extends AbstractTickableSoundInstance {
		Loop() {
			super(ModSounds.WAR_DRUMS, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
			looping = true;
			delay = 0;
			relative = true;
			attenuation = SoundInstance.Attenuation.NONE;
			volume = VOLUME;
		}

		@Override
		public void tick() {
			if (!battleRunning()) {
				stop();
				return;
			}
			MatchStatePayload state = ClientMatchState.get();
			boolean suddenDeath = state != null && state.suddenDeath();
			float targetPitch = suddenDeath ? SUDDEN_DEATH_PITCH : 1.0F;
			float targetVolume = suddenDeath ? SUDDEN_DEATH_VOLUME : VOLUME;
			pitch += (targetPitch - pitch) * 0.05F;
			volume += (targetVolume - volume) * 0.05F;
		}
	}
}
