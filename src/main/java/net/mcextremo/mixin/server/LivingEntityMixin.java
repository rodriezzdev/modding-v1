package net.mcextremo.mixin.server;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.mcextremo.server.VenganzaManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
    private float aplicarMultiplicadoresVenganza(float amount, DamageSource source) {
        // Comprobamos si tanto la víctima como el atacante son jugadores reales en el servidor
        if ((Object) this instanceof ServerPlayer victima && source.getEntity() instanceof ServerPlayer atacante) {
            // Pasamos el daño por el mánager para aplicar el +20% o -20% matemático
            return VenganzaManager.ajustarDanio(atacante, victima, amount);
        }
        // Si es daño por caída, lava o contra otros mobs, el daño no cambia
        return amount;
    }
}