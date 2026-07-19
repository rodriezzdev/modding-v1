package net.mcextremo.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.mcextremo.Extr3mo;

public record TriggerEventPayload(int eventId) implements CustomPacketPayload {
    public static final Type<TriggerEventPayload> TYPE = new Type<>(Extr3mo.id("trigger_event"));

    public static final StreamCodec<ByteBuf, TriggerEventPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, TriggerEventPayload::eventId,
            TriggerEventPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}