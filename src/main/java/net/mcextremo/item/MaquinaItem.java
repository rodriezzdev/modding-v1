package net.mcextremo.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.mcextremo.Extr3mo;
import net.mcextremo.entity.MaquinaEntity;

public class MaquinaItem extends Item {
    public MaquinaItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!level.isClientSide()) {
            BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
            MaquinaEntity entity = new MaquinaEntity(Extr3mo.MAQUINA_ENTITY, level);

            // Coloca la entidad centrada en el bloque y calcula su rotación según el jugador
            entity.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, context.getHorizontalDirection().toYRot(), 0.0F);

            level.addFreshEntity(entity);
            context.getItemInHand().shrink(1); // Consume 1 ítem del inventario
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
    }
}