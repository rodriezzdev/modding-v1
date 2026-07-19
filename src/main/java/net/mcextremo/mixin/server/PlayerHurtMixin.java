package net.mcextremo.mixin.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.mcextremo.server.VenganzaManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Player.class)
public abstract class PlayerHurtMixin {

    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
    private float onHurtModifyAmount(float amount, DamageSource source) {
        Player target = (Player) (Object) this;

        // Comprobamos que estemos en el Servidor y que la entidad dañada sea un jugador real
        if (!target.level().isClientSide() && target instanceof ServerPlayer serverTarget) {
            // Comprobamos si la fuente del daño directo o indirecto fue provocado por otro jugador
            if (source.getEntity() instanceof ServerPlayer serverAttacker) {
                // Llamamos a nuestro mánager para que aplique los cálculos matemáticos correspondientes
                return VenganzaManager.ajustarDanio(serverAttacker, serverTarget, amount);
            }
        }
        return amount;
    }
}