package io.github.jan1a234.goblinforest.client;

import net.minecraft.client.renderer.entity.state.PiglinRenderState;

/** Render-Zustand eines Goblins: wie ein Piglin, plus Einheitentyp und Clan für die Textur. */
public class GoblinRenderState extends PiglinRenderState {
	public String unit = "slave";
	public String team = "red";
}
