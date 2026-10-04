package io.github.jan1a234.goblinforest.client.mixin;

import io.github.jan1a234.goblinforest.client.CommandView;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Setzt die Kamera in der Kommandoansicht über das Schlachtfeld. Greift am Ende von {@code alignWithEntity},
 * damit Sichtfeld und Frustum-Culling danach schon mit der neuen Position berechnet werden.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	protected abstract void setPosition(double x, double y, double z);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void goblinforest$commandView(float partialTicks, CallbackInfo ci) {
		CommandView.Pose pose = CommandView.pose(partialTicks);
		if (pose != null) {
			setRotation(pose.yaw(), pose.pitch());
			setPosition(pose.x(), pose.y(), pose.z());
		}
	}
}
