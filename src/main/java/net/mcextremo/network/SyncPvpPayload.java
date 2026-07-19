package net.mcextremo.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncPvpPayload(boolean pvpActivo) implements CustomPacketPayload {
    public static final Type<SyncPvpPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("extr3mo", "sync_pvp"));

    public static final StreamCodec<FriendlyByteBuf, SyncPvpPayload> CODEC = StreamCodec.of(
            (buf, value) -> buf.writeBoolean(value.pvpActivo()),
            buf -> new SyncPvpPayload(buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}