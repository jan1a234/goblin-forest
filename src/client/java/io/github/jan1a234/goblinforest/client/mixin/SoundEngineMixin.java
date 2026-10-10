package io.github.jan1a234.goblinforest.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.jan1a234.goblinforest.client.CommandView;
import net.minecraft.client.Camera;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * In der Draufsicht hängt die Kamera hoch über dem Feld; dort wären Schwerthiebe und Zauber kaum zu hören.
 * Deshalb hört der Spieler an einem Punkt knapp über der Bildmitte.
 */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
	@WrapOperation(method = "updateSource", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;position()Lnet/minecraft/world/phys/Vec3;"))
	private Vec3 goblinforest$listenAtFocus(Camera camera, Operation<Vec3> original) {
		Vec3 listener = CommandView.listenerPosition();
		return listener != null ? listener : original.call(camera);
	}
}
