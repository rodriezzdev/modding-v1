package net.mcextremo;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.mcextremo.server.VenganzaManager;
import net.mcextremo.server.Extr3moServer;
import java.util.UUID;
import java.util.Map;

public class PvpDamageHandler implements ServerLivingEntityEvents.AllowDamage {

    @Override
    public boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (entity instanceof ServerPlayer victim && source.getEntity() instanceof ServerPlayer attacker) {

            if (Extr3moServer.pvpActivo) {
                return true;
            }

            UUID attackerId = attacker.getUUID();
            UUID victimId = victim.getUUID();

            if (hasVengeanceAgainst(attackerId, victimId)) {
                return true;
            }

            if (hasVengeanceAgainst(victimId, attackerId)) {
                Map<UUID, Long> ataquesRecibidos = Extr3moServer.CRONOLOGIA_DE_ATANQUES.get(attackerId);
                if (ataquesRecibidos != null && ataquesRecibidos.containsKey(victimId)) {
                    long ultimoGolpeRecibido = ataquesRecibidos.get(victimId);
                    long ventanaDefensaMs = 60000L;

                    if (System.currentTimeMillis() - ultimoGolpeRecibido < ventanaDefensaMs) {
                        return true;
                    }
                }
            }
            return false;
        }
        return true;
    }

    private boolean hasVengeanceAgainst(UUID attackerId, UUID victimId) {
        Map<UUID, Long> misObjetivos = VenganzaManager.venganzasActivas.get(attackerId);
        if (misObjetivos != null && misObjetivos.containsKey(victimId)) {
            long tiempoExpiracion = misObjetivos.get(victimId);
            return System.currentTimeMillis() < tiempoExpiracion;
        }
        return false;
    }
}