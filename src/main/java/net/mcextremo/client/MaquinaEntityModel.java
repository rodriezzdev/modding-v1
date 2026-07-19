package net.mcextremo.client;

import net.minecraft.resources.ResourceLocation;
import net.mcextremo.Extr3mo;
import net.mcextremo.entity.MaquinaEntity;
import software.bernie.geckolib.model.GeoModel;

public class MaquinaEntityModel extends GeoModel<MaquinaEntity> {
    @Override
    public ResourceLocation getModelResource(MaquinaEntity animatable) {
        return Extr3mo.id("geo/maquina.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(MaquinaEntity animatable) {
        return Extr3mo.id("textures/maquina/maquina.png");
    }

    @Override
    public ResourceLocation getAnimationResource(MaquinaEntity animatable) {
        return Extr3mo.id("animations/maquina.animation.json");
    }
}