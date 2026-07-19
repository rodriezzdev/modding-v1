package net.mcextremo.server;

import net.minecraft.server.level.ServerPlayer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.UUID;

public class VenganzaManager {
    // NUEVO: Almacena Víctima_UUID -> (Asesino_UUID -> Tiempo_Expiración_MS)
    public static final Map<UUID, Map<UUID, Long>> venganzasActivas = new ConcurrentHashMap<>();

    // Almacena los niveles acumulados usando llaves compuestas: "VictimaUUID->AsesinoUUID"
    public static final Map<String, Integer> nivelesPorVenganza = new ConcurrentHashMap<>();

    /**
     * Aplica el daño matemático dinámico evaluando las múltiples venganzas activas.
     */
    public static float ajustarDanio(ServerPlayer attacker, ServerPlayer target, float amount) {
        UUID idAtacante = attacker.getUUID();
        UUID idObjetivo = target.getUUID();

        // CASO 1: El atacante es una VÍCTIMA intentando vengarse de este objetivo específico (Asesino)
        Map<UUID, Long> misObjetivos = venganzasActivas.get(idAtacante);
        if (misObjetivos != null && misObjetivos.containsKey(idObjetivo)) {
            String key = idAtacante.toString() + "->" + idObjetivo.toString();
            int nivel = nivelesPorVenganza.getOrDefault(key, 1);
            float modificador = 0.20f * nivel; // +20%, +40%, +60%

            // La víctima inflige más daño al asesino
            return amount * (1.0f + modificador);
        }

        // CASO 2: El atacante es el ASESINO intentando golpear a una de sus víctimas activas
        Map<UUID, Long> objetivosDeLaVictima = venganzasActivas.get(idObjetivo);
        if (objetivosDeLaVictima != null && objetivosDeLaVictima.containsKey(idAtacante)) {
            String key = idObjetivo.toString() + "->" + idAtacante.toString();
            int nivel = nivelesPorVenganza.getOrDefault(key, 1);
            float modificador = 0.20f * nivel; // -20%, -40%, -60% de daño recibido

            // La víctima mitiga el daño entrante del asesino (Resistencia)
            return amount * (1.0f - modificador);
        }

        return amount;
    }
}