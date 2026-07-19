package net.mcextremo.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.mcextremo.Extr3mo;

public record DeathNotificationPayload(
        String victimName,
        String attackerName,
        boolean isAttackerPlayer,
        boolean isEnvironmental,
        String envMessage,
        String attackerTypePath,
        int mode,
        String assistants // <-- NUEVO: Nombres de los que asistieron
) implements CustomPacketPayload {

    public static final Type<DeathNotificationPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Extr3mo.MOD_ID, \"death_notification\"));

    public static final StreamCodec<FriendlyByteBuf, DeathNotificationPayload> CODEC =
            StreamCodec.of(
                    (buf, value) -> {
                        buf.writeUtf(value.victimName);
                        buf.writeUtf(value.attackerName);
                        buf.writeBoolean(value.isAttackerPlayer);
                        buf.writeBoolean(value.isEnvironmental);
                        buf.writeUtf(value.envMessage);
                        buf.writeUtf(value.attackerTypePath);
                        buf.writeInt(value.mode);
                        buf.writeUtf(value.assistants); // <-- NUEVO: Escritura
                    },
                    buf -> new DeathNotificationPayload(
                            buf.readUtf(), buf.readUtf(), buf.readBoolean(),
                            buf.readBoolean(), buf.readUtf(), buf.readUtf(),
                            buf.readInt(), buf.readUtf() // <-- NUEVO: Lectura
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}