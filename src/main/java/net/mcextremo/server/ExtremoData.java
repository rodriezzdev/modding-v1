package net.mcextremo.server;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ExtremoData extends SavedData {
    private final Map<UUID, Integer> vidas = new HashMap<>();
    private final Map<UUID, Integer> vidasRotas = new HashMap<>();
    private final Map<UUID, Integer> peligrosidadNivel = new HashMap<>();
    private final Map<UUID, Long> peligrosidadExpiracion = new HashMap<>();
    private final Map<UUID, Integer> killsNivel3 = new HashMap<>();
    private final Map<UUID, UUID> venganzaTarget = new HashMap<>();

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
        CompoundTag playersTag = new CompoundTag();
        vidas.forEach((uuid, v) -> {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putInt("vidas", v);
            playerTag.putInt("vidasRotas", vidasRotas.getOrDefault(uuid, 0));
            playerTag.putInt("peligrosidadNivel", peligrosidadNivel.getOrDefault(uuid, 0));
            playerTag.putLong("peligrosidadExpiracion", peligrosidadExpiracion.getOrDefault(uuid, 0L));
            playerTag.putInt("killsNivel3", killsNivel3.getOrDefault(uuid, 0));
            if (venganzaTarget.containsKey(uuid) && venganzaTarget.get(uuid) != null) {
                playerTag.putUUID("venganzaTarget", venganzaTarget.get(uuid));
            }
            playersTag.put(uuid.toString(), playerTag);
        });
        nbt.put("players", playersTag);
        return nbt;
    }

    public static ExtremoData load(CompoundTag nbt, HolderLookup.Provider provider) {
        ExtremoData data = new ExtremoData();
        if (nbt.contains("players")) {
            CompoundTag playersTag = nbt.getCompound("players");
            for (String key : playersTag.getAllKeys()) {
                UUID uuid = UUID.fromString(key);
                CompoundTag playerTag = playersTag.getCompound(key);
                data.vidas.put(uuid, playerTag.getInt("vidas"));
                data.vidasRotas.put(uuid, playerTag.getInt("vidasRotas"));
                data.peligrosidadNivel.put(uuid, playerTag.getInt("peligrosidadNivel"));
                data.peligrosidadExpiracion.put(uuid, playerTag.getLong("peligrosidadExpiracion"));
                data.killsNivel3.put(uuid, playerTag.getInt("killsNivel3"));
                if (playerTag.hasUUID("venganzaTarget")) {
                    data.venganzaTarget.put(uuid, playerTag.getUUID("venganzaTarget"));
                }
            }
        }
        return data;
    }

    public static ExtremoData getServerState(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ExtremoData::new, ExtremoData::load, null),
                "extr3mo_data"
        );
    }

    public int getVidas(UUID uuid) { return vidas.getOrDefault(uuid, 5); }
    public int getVidasRotas(UUID uuid) { return vidasRotas.getOrDefault(uuid, 0); }
    public int getPeligrosidadNivel(UUID uuid) { return peligrosidadNivel.getOrDefault(uuid, 0); }
    public long getPeligrosidadExpiracion(UUID uuid) { return peligrosidadExpiracion.getOrDefault(uuid, 0L); }
    public int getKillsNivel3(UUID uuid) { return killsNivel3.getOrDefault(uuid, 0); }
    public UUID getVenganzaTarget(UUID uuid) { return venganzaTarget.get(uuid); }

    public void setVidas(UUID uuid, int amount) { vidas.put(uuid, Math.max(0, amount)); setDirty(); }
    public void setVidasRotas(UUID uuid, int amount) { vidasRotas.put(uuid, Math.min(5, amount)); setDirty(); }
    public void setPeligrosidadNivel(UUID uuid, int nivel) { peligrosidadNivel.put(uuid, Math.min(3, Math.max(0, nivel))); setDirty(); }
    public void setPeligrosidadExpiracion(UUID uuid, long timestamp) { peligrosidadExpiracion.put(uuid, timestamp); setDirty(); }
    public void setKillsNivel3(UUID uuid, int kills) { killsNivel3.put(uuid, kills); setDirty(); }
    public void setVenganzaTarget(UUID uuid, UUID target) {
        if (target == null) venganzaTarget.remove(uuid);
        else venganzaTarget.put(uuid, target);
        setDirty();
    }

    public void checkExpiracionPeligrosidad(UUID uuid) {
        long expiracion = getPeligrosidadExpiracion(uuid);
        if (expiracion > 0 && System.currentTimeMillis() > expiracion) {
            setPeligrosidadNivel(uuid, 0);
            setKillsNivel3(uuid, 0);
            setPeligrosidadExpiracion(uuid, 0L);
        }
    }
}