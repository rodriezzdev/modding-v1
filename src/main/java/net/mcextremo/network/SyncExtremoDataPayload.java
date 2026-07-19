package net.mcextremo.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.mcextremo.Extr3mo;

public record SyncExtremoDataPayload(
        int vidas,
        int vidasRotas,
        int peligrosidadNivel,
        long peligrosidadExpiracion,
        int killsNivel3,
        String venganzaTargetName,
        String hunterName // <--- El nuevo parámetro que faltaba registrar
) implements CustomPacketPayload {

    public static final Type<SyncExtremoDataPayload> TYPE = new Type<>(Extr3mo.id("sync_extremo_data"));

    // Redirección manual de lectura y escritura para evitar conflictos con el límite de argumentos de composite()
    public static final StreamCodec<FriendlyByteBuf, SyncExtremoDataPayload> CODEC = new StreamCodec<>() {
        @Override
        public SyncExtremoDataPayload decode(FriendlyByteBuf buf) {
            return new SyncExtremoDataPayload(
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readVarLong(),
                    buf.readInt(),
                    buf.readUtf(),
                    buf.readUtf() // Lee la lista de cazadores
            );
        }

        @Override
        public void encode(FriendlyByteBuf buf, SyncExtremoDataPayload value) {
            buf.writeInt(value.vidas());
            buf.writeInt(value.vidasRotas());
            buf.writeInt(value.peligrosidadNivel());
            buf.writeVarLong(value.peligrosidadExpiracion());
            buf.writeInt(value.killsNivel3());
            buf.writeUtf(value.venganzaTargetName());
            buf.writeUtf(value.hunterName()); // Escribe la lista de cazadores
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}