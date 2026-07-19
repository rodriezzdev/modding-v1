package net.mcextremo.mixin.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.mcextremo.server.ExtremoData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerDeathMixin {

    @Inject(method = "dropEquipment", at = @At("HEAD"), cancellable = true)
    private void onDropEquipment(CallbackInfo ci) {
        Player player = (Player) (Object) this;

        if (player instanceof ServerPlayer serverPlayer) {
            ExtremoData data = ExtremoData.getServerState(serverPlayer.getServer());
            int vidasRestantes = data.getVidas(serverPlayer.getUUID());

            if (vidasRestantes > 0) {
                // Si le quedan vidas, cancelamos la caída de ítems al suelo de forma nativa
                ci.cancel();
            }
        }
    }
}