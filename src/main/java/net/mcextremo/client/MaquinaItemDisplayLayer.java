package net.mcextremo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.mcextremo.entity.MaquinaEntity;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import org.joml.Quaternionf;
import java.util.ArrayList;
import java.util.List;

public class MaquinaItemDisplayLayer extends GeoRenderLayer<MaquinaEntity> {
    public MaquinaItemDisplayLayer(GeoRenderer<MaquinaEntity> entityRenderer) {
        super(entityRenderer);
    }

    @Override
    public void render(PoseStack poseStack, MaquinaEntity animatable, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {

        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();

        for (int i = 1; i <= 6; i++) {
            ItemStack itemStack = animatable.getDisplayItem(i);

            if (!itemStack.isEmpty()) {
                String boneName = "extra_item_" + i;
                GeoBone bone = bakedModel.getBone(boneName).orElse(null);

                if (bone != null) {
                    poseStack.pushPose();

                    // Obtenemos la cadena completa de huesos (Root -> Waist -> Estante -> Slot)
                    List<GeoBone> boneChain = new ArrayList<>();
                    GeoBone current = bone;
                    while (current != null) {
                        boneChain.add(0, current);
                        current = current.getParent();
                    }

                    float divisor = 16.0F;
                    GeoBone parent = null;

                    // TRASLACIÓN RELATIVA MATEMÁTICA (Calcula los desfasajes reales de Blockbench)
                    for (GeoBone b : boneChain) {
                        float pX = parent == null ? b.getPivotX() : (b.getPivotX() - parent.getPivotX());
                        float pY = parent == null ? b.getPivotY() : (b.getPivotY() - parent.getPivotY());
                        float pZ = parent == null ? b.getPivotZ() : (b.getPivotZ() - parent.getPivotZ());

                        // Trasladamos sumando la posición base y la animada
                        poseStack.translate((pX + b.getPosX()) / divisor, (pY + b.getPosY()) / divisor, (pZ + b.getPosZ()) / divisor);

                        // Aplicamos la rotación y escala de la jerarquía
                        poseStack.mulPose(new Quaternionf().rotationXYZ(b.getRotX(), b.getRotY(), b.getRotZ()));
                        poseStack.scale(b.getScaleX(), b.getScaleY(), b.getScaleZ());

                        parent = b;
                    }

                    // Ajuste final de tamaño para que quepa estéticamente dentro del cristal
                    poseStack.scale(0.4F, 0.4F, 0.4F);

                    // Rotación compensatoria de 180° para que los ítems miren hacia el frente del bloque
                    poseStack.mulPose(new Quaternionf().rotationY((float) Math.toRadians(180)));

                    // Renderizado tridimensional estático
                    itemRenderer.renderStatic(
                            itemStack,
                            ItemDisplayContext.FIXED,
                            packedLight,
                            packedOverlay,
                            poseStack,
                            bufferSource,
                            animatable.level(),
                            animatable.getId()
                    );

                    poseStack.popPose();
                }
            }
        }
    }
}