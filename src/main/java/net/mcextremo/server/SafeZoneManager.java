package net.mcextremo.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class SafeZoneManager {
    public static class SafeZone {
        public String nombre;
        public String dimension;
        public int x1, y1, z1;
        public int x2, y2, z2;
        public boolean pvpPermitido = false;
        public boolean proteccionBloques = true; // true = Protegido (No romper/colocar)

        public SafeZone(String nombre, String dimension, BlockPos p1, BlockPos p2) {
            this.nombre = nombre;
            this.dimension = dimension;
            this.x1 = Math.min(p1.getX(), p2.getX());
            this.y1 = Math.min(p1.getY(), p2.getY());
            this.z1 = Math.min(p1.getZ(), p2.getZ());
            this.x2 = Math.max(p1.getX(), p2.getX());
            this.y2 = Math.max(p1.getY(), p2.getY());
            this.z2 = Math.max(p1.getZ(), p2.getZ());
        }

        public boolean contiene(ServerPlayer player) {
            if (!player.level().dimension().location().toString().equals(this.dimension)) return false;
            BlockPos pos = player.blockPosition();
            return pos.getX() >= x1 && pos.getX() <= x2 &&
                    pos.getY() >= y1 && pos.getY() <= y2 &&
                    pos.getZ() >= z1 && pos.getZ() <= z2;
        }

        public boolean contienePos(String dim, BlockPos pos) {
            if (!dim.equals(this.dimension)) return false;
            return pos.getX() >= x1 && pos.getX() <= x2 &&
                    pos.getY() >= y1 && pos.getY() <= y2 &&
                    pos.getZ() >= z1 && pos.getZ() <= z2;
        }
    }

    public static final Map<String, SafeZone> ZONAS = new HashMap<>();
    private static final File FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "extr3mo_safezones.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void cargar() {
        if (!FILE.exists()) return;
        try (FileReader reader = new FileReader(FILE)) {
            Type type = new TypeToken<Map<String, SafeZone>>(){}.getType();
            Map<String, SafeZone> cargadas = GSON.fromJson(reader, type);
            if (cargadas != null) {
                ZONAS.clear();
                ZONAS.putAll(cargadas);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void guardar() {
        try (FileWriter writer = new FileWriter(FILE)) {
            GSON.toJson(ZONAS, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static SafeZone obtenerZonaEn(ServerPlayer player) {
        for (SafeZone zona : ZONAS.values()) {
            if (zona.contiene(player)) return zona;
        }
        return null;
    }

    public static SafeZone obtenerZonaEnPos(String dim, BlockPos pos) {
        for (SafeZone zona : ZONAS.values()) {
            if (zona.contienePos(dim, pos)) return zona;
        }
        return null;
    }
}