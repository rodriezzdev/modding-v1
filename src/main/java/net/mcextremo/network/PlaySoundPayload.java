package net.mcextremo.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PlaySoundPayload(String soundType) implements CustomPacketPayload {
    public static final Type<PlaySoundPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("extr3mo", "play_sound"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlaySoundPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeUtf(payload.soundType()),
            buf -> new PlaySoundPayload(buf.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}