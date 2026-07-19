package net.mcextremo.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncVidasPayload(int vidas) implements CustomPacketPayload {
    public static final Type<SyncVidasPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("extr3mo", "sync_vidas"));

    public static final StreamCodec<FriendlyByteBuf, SyncVidasPayload> CODEC = StreamCodec.of(
            (buf, value) -> buf.writeInt(value.vidas()),
            buf -> new SyncVidasPayload(buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}