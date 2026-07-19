package net.mcextremo.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.mcextremo.entity.MaquinaEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class MaquinaEntityRenderer extends GeoEntityRenderer<MaquinaEntity> {
    public MaquinaEntityRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new MaquinaEntityModel());
        // INYECTAR CAPA VISUAL: Dibuja los ítems expuestos en tiempo real
        this.addRenderLayer(new MaquinaItemDisplayLayer(this));
    }

    @Override
    public RenderType getRenderType(MaquinaEntity animatable, ResourceLocation texture,
                                    net.minecraft.client.renderer.MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public boolean shouldShowName(MaquinaEntity animatable) {
        return false;
    }
}