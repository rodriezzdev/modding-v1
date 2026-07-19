package net.mcextremo;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashMap;
import java.util.UUID;

public class VidasData extends SavedData {
    private final HashMap<UUID, Integer> vidasJugadores = new HashMap<>();

    public int getVidas(UUID uuid) {
        return vidasJugadores.getOrDefault(uuid, 5);
    }

    public void setVidas(UUID uuid, int vidas) {
        vidasJugadores.put(uuid, vidas);
        this.setDirty();
    }

    public void restarVida(UUID uuid) {
        int vidasActuales = getVidas(uuid);
        if (vidasActuales > 0) {
            setVidas(uuid, vidasActuales - 1);
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag jugadoresTag = new CompoundTag();
        vidasJugadores.forEach((uuid, vidas) -> {
            jugadoresTag.putInt(uuid.toString(), vidas);
        });
        tag.put("VidasJugadores", jugadoresTag);
        return tag;
    }

    public static VidasData load(CompoundTag tag, HolderLookup.Provider provider) {
        VidasData data = new VidasData();
        if (tag.contains("VidasJugadores")) {
            CompoundTag jugadoresTag = tag.getCompound("VidasJugadores");
            for (String key : jugadoresTag.getAllKeys()) {
                UUID uuid = UUID.fromString(key);
                int vidas = jugadoresTag.getInt(key);
                data.vidasJugadores.put(uuid, vidas);
            }
        }
        return data;
    }

    public static VidasData getServerState(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
        if (overworld == null) return new VidasData();

        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(VidasData::new, VidasData::load, null),
                "mcextremo_vidas"
        );
    }
}