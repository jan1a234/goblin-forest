package io.github.jan1a234.goblinforest.net;

import io.github.jan1a234.goblinforest.GoblinForest;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Wunsch des Spielers an den Server, z. B. {@code recruit:warrior}, {@code upgrade:armor:archer}, {@code cast:fireball},
 * {@code ability:bloodlust}, {@code stance:next}, {@code rally} oder {@code hit_structure}.
 * Der Server prüft jede Aktion selbst; der Client entscheidet nichts.
 */
public record ActionPayload(String action) implements CustomPacketPayload {
	public static final Type<ActionPayload> TYPE = new Type<>(GoblinForest.id("action"));
	public static final StreamCodec<ByteBuf, ActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, ActionPayload::action,
			ActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
