package io.github.jan1a234.goblinforest.net;

import io.github.jan1a234.goblinforest.GoblinForest;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server bittet den Client, das Kriegsmenü zu öffnen (Rechtsklick auf das Kriegshorn in der Hotbar). */
public record OpenMenuPayload() implements CustomPacketPayload {
	public static final OpenMenuPayload INSTANCE = new OpenMenuPayload();
	public static final Type<OpenMenuPayload> TYPE = new Type<>(GoblinForest.id("open_menu"));
	public static final StreamCodec<ByteBuf, OpenMenuPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
