package net.mcextremo.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public record PublicLivesPayload(UUID playerUuid, int lives, int brokenLives) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PublicLivesPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("mcextremo", "public_lives"));

    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, PublicLivesPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUUID(payload.playerUuid());
                buf.writeInt(payload.lives());
                buf.writeInt(payload.brokenLives());
            },
            buf -> new PublicLivesPayload(buf.readUUID(), buf.readInt(), buf.readInt())
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}