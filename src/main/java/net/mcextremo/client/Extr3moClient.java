package net.mcextremo.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import com.mojang.blaze3d.systems.RenderSystem;
import net.mcextremo.Extr3mo;
import net.mcextremo.network.SyncExtremoDataPayload;
import net.mcextremo.network.SyncPvpPayload;
import net.mcextremo.network.TriggerEventPayload;
import net.mcextremo.network.DeathNotificationPayload;
import net.mcextremo.network.PublicLivesPayload;
import net.mcextremo.network.KillFeedPayload;
import net.mcextremo.client.KillFeedOverlay;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Extr3moClient implements ClientModInitializer {
    public static int vidas = 5;
    public static int vidasRotas = 0;
    public static int peligrosidadNivel = 0;
    public static long peligrosidadExpiracion = 0L;
    public static int killsNivel3 = 0;
    public static String venganzaTargetName = "";
    public static String hunterName = "";
    public static boolean pvpActivo = false;
    public static String latestVictimName = "";

    private static long lastDeathAnimTime = 0L;
    private static long overlayStartTime = 0L;
    private static int currentEventId = 0;

    public record PlayerLivesData(int lives, int brokenLives) {}
    public static final ConcurrentHashMap<UUID, PlayerLivesData> otrasVidas = new ConcurrentHashMap<>();

    private static final ResourceLocation CORAZON_LLENO = Extr3mo.id("textures/gui/corazones_custom.png");
    private static final ResourceLocation CORAZON_VACIO = Extr3mo.id("textures/gui/corazones_vacios.png");
    private static final ResourceLocation CORAZON_ROTO = Extr3mo.id("textures/gui/corazones_rotos.png");

    public static class DeathNotification {
        public final String victimName;
        public final String attackerName;
        public final boolean isAttackerPlayer;
        public final boolean isEnvironmental;
        public final String envMessage;
        public final String attackerTypePath;
        public final int mode;
        public final long startTime;

        public DeathNotification(String victimName, String attackerName, boolean isAttackerPlayer, boolean isEnvironmental, String envMessage, String attackerTypePath, int mode) {
            this.victimName = victimName;
            this.attackerName = attackerName;
            this.isAttackerPlayer = isAttackerPlayer;
            this.isEnvironmental = isEnvironmental;
            this.envMessage = envMessage;
            this.attackerTypePath = attackerTypePath;
            this.mode = mode;
            this.startTime = System.currentTimeMillis();
        }
    }
    public static final java.util.List<DeathNotification> activeNotifications = new java.util.ArrayList<>();

    private static void drawPlayerHead(net.minecraft.client.gui.GuiGraphics drawContext, String playerName, int x, int y, int size) {
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() == null) return;
        net.minecraft.client.multiplayer.PlayerInfo playerInfo = client.getConnection().getPlayerInfo(playerName);
        ResourceLocation skinLocation;
        if (playerInfo != null) {
            skinLocation = playerInfo.getSkin().texture();
        } else {
            skinLocation = net.minecraft.client.resources.DefaultPlayerSkin.getDefaultTexture();
        }
        drawContext.blit(skinLocation, x, y, size, size, 8.0F, 8.0F, 8, 8, 64, 64);
        drawContext.blit(skinLocation, x, y, size, size, 40.0F, 8.0F, 8, 8, 64, 64);
    }

    private static ItemStack getSpawnEggOrFallback(String typePath) {
        if (typePath == null || typePath.isEmpty()) {
            return new ItemStack(Items.SPIDER_EYE);
        }
        try {
            ResourceLocation entityType = ResourceLocation.tryParse(typePath);
            if (entityType != null) {
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(entityType);
                SpawnEggItem egg = SpawnEggItem.byId(type);
                if (egg != null) return new ItemStack(egg);
            }
        } catch (Exception ignored) {}
        return new ItemStack(Items.SPIDER_EYE);
    }

    private static void drawHeartQuad(com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffers, ResourceLocation texture, float x, float y, float size) {
        net.minecraft.client.renderer.RenderType renderType = net.minecraft.client.renderer.RenderType.entityCutoutNoCull(texture);
        com.mojang.blaze3d.vertex.VertexConsumer consumer = buffers.getBuffer(renderType);
        com.mojang.blaze3d.vertex.PoseStack.Pose entry = poseStack.last();

        consumer.addVertex(entry, x, y, 0.0f).setColor(255, 255, 255, 255).setUv(0.0f, 0.0f).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(entry, 0.0f, 0.0f, 1.0f);
        consumer.addVertex(entry, x, y + size, 0.0f).setColor(255, 255, 255, 255).setUv(0.0f, 1.0f).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(entry, 0.0f, 0.0f, 1.0f);
        consumer.addVertex(entry, x + size, y + size, 0.0f).setColor(255, 255, 255, 255).setUv(1.0f, 1.0f).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(entry, 0.0f, 0.0f, 1.0f);
        consumer.addVertex(entry, x + size, y, 0.0f).setColor(255, 255, 255, 255).setUv(1.0f, 0.0f).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(entry, 0.0f, 0.0f, 1.0f);
    }

    @Override
    public void onInitializeClient() {
        KillFeedOverlay.registrar();

        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                Extr3mo.MAQUINA_ENTITY,
                MaquinaEntityRenderer::new
        );

        PayloadTypeRegistry.playS2C().register(PublicLivesPayload.TYPE, PublicLivesPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(KillFeedPayload.TYPE, KillFeedPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(KillFeedPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                KillFeedOverlay.mostrarNotificacion(payload.textoPrincipal(), payload.textoAsistencias());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncExtremoDataPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                vidas = payload.vidas();
                vidasRotas = payload.vidasRotas();
                peligrosidadNivel = payload.peligrosidadNivel();
                peligrosidadExpiracion = payload.peligrosidadExpiracion();
                killsNivel3 = payload.killsNivel3();
                venganzaTargetName = payload.venganzaTargetName();
                hunterName = payload.hunterName();
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(PublicLivesPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                otrasVidas.put(payload.playerUuid(), new PlayerLivesData(payload.lives(), payload.brokenLives()));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncPvpPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> { pvpActivo = payload.pvpActivo(); });
        });

        ClientPlayNetworking.registerGlobalReceiver(TriggerEventPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                int id = payload.eventId();

                if (id == 3 || id == 4 || id == 5 || id == 6 || id == 7) {
                    if (System.currentTimeMillis() - lastDeathAnimTime < 6000L) {
                        return;
                    }
                    lastDeathAnimTime = System.currentTimeMillis();
                }

                String soundName = "";
                switch (id) {
                    case 1 -> soundName = "pvpon";
                    case 2 -> soundName = "pvpoff";
                    case 3 -> soundName = "loseheartplayer";
                    case 4 -> soundName = "loseheart";
                    case 5 -> soundName = "loseallheart";
                    case 6 -> soundName = "slotperdidoplayer";
                    case 7 -> soundName = "slotperdidoall";
                    case 8, 9 -> soundName = "cambio";
                }

                currentEventId = id;
                overlayStartTime = System.currentTimeMillis();

                if (!soundName.isEmpty()) {
                    float pitchInicial = (id == 9) ? 1.71F : 1.0F;
                    context.client().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(Extr3mo.id(soundName)), pitchInicial)
                    );
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(DeathNotificationPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                activeNotifications.add(new DeathNotification(
                        payload.victimName(), payload.attackerName(), payload.isAttackerPlayer(),
                        payload.isEnvironmental(), payload.envMessage(), payload.attackerTypePath(), payload.mode()
                ));
                latestVictimName = payload.victimName();
            });
        });

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            Minecraft client = Minecraft.getInstance();
            if (client.level == null || client.player == null) return;

            net.minecraft.world.phys.Vec3 posicionCamara = client.gameRenderer.getMainCamera().getPosition();
            net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource = client.renderBuffers().bufferSource();

            for (net.minecraft.client.player.AbstractClientPlayer jugadorMundo : client.level.players()) {
                if (jugadorMundo == client.player && client.options.getCameraType().isFirstPerson()) continue;
                if (jugadorMundo.isInvisible() || jugadorMundo.isSpectator()) continue;

                double distanciaCuadrada = jugadorMundo.distanceToSqr(client.player);
                if (distanciaCuadrada > 4096) continue;

                PlayerLivesData dataVidas = otrasVidas.get(jugadorMundo.getUUID());
                if (dataVidas == null) continue;

                int targetVidas = dataVidas.lives();
                int targetRotas = dataVidas.brokenLives();
                int maxVidasPosibles = 5;

                float tickDelta = context.tickCounter().getGameTimeDeltaPartialTick(true);
                double posX = net.minecraft.util.Mth.lerp(tickDelta, jugadorMundo.xo, jugadorMundo.getX()) - posicionCamara.x;
                double posY = net.minecraft.util.Mth.lerp(tickDelta, jugadorMundo.yo, jugadorMundo.getY()) - posicionCamara.y;
                double posZ = net.minecraft.util.Mth.lerp(tickDelta, jugadorMundo.zo, jugadorMundo.getZ()) - posicionCamara.z;

                posY += jugadorMundo.getBbHeight() + 0.88F;

                com.mojang.blaze3d.vertex.PoseStack matrixStack = context.matrixStack();
                matrixStack.pushPose();
                matrixStack.translate(posX, posY, posZ);

                matrixStack.mulPose(client.getEntityRenderDispatcher().cameraOrientation());
                matrixStack.scale(-0.025F, -0.025F, 0.025F);

                float tamañoCorazon = 12.0f;
                float separacion = 14.0f;
                float anchoTotal = (maxVidasPosibles * separacion) - 2.0f;
                float startX = -(anchoTotal / 2.0f);

                for (int i = 0; i < maxVidasPosibles; i++) {
                    ResourceLocation textura;
                    if (i >= (maxVidasPosibles - targetRotas)) {
                        textura = CORAZON_ROTO;
                    } else if (i < targetVidas) {
                        textura = CORAZON_LLENO;
                    } else {
                        textura = CORAZON_VACIO;
                    }

                    float heartX = startX + (i * separacion);
                    drawHeartQuad(matrixStack, bufferSource, textura, heartX, 0.0f, tamañoCorazon);
                }

                matrixStack.popPose();
            }
            bufferSource.endBatch();
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.options.hideGui) return;

            int width = client.getWindow().getGuiScaledWidth();
            int height = client.getWindow().getGuiScaledHeight();
            int centroX = width / 2;

            int maxVidasPosibles = 5;
            int tamañoCorazon = 16;
            int espacioEntreCorazones = 18;
            int totalAnchoCorazones = maxVidasPosibles * espacioEntreCorazones;
            int heartsStartX = centroX - (totalAnchoCorazones / 2) + 2;
            int currentY = 8;

            for (int i = 0; i < maxVidasPosibles; i++) {
                ResourceLocation textura;
                if (i >= (maxVidasPosibles - vidasRotas)) {
                    textura = CORAZON_ROTO;
                } else if (i < vidas) {
                    textura = CORAZON_LLENO;
                } else {
                    textura = CORAZON_VACIO;
                }
                drawContext.blit(textura, heartsStartX + (i * espacioEntreCorazones), currentY, 0, 0, tamañoCorazon, tamañoCorazon, tamañoCorazon, tamañoCorazon);
            }

            currentY += 24;

            float yaw = client.player.getYRot();
            float heading = (yaw % 360 + 360) % 360;

            String[] puntosCardinales = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
            int[] angulosCardinales = {0, 45, 90, 135, 180, 225, 270, 315};

            for (int i = 0; i < puntosCardinales.length; i++) {
                float diferenciaAngulo = angulosCardinales[i] - heading;

                if (diferenciaAngulo < -180) diferenciaAngulo += 360;
                if (diferenciaAngulo > 180) diferenciaAngulo -= 360;

                if (Math.abs(diferenciaAngulo) <= 65) {
                    int desfasamientoX = (int) (diferenciaAngulo * 1.8f);
                    int dibujoPuntoX = centroX + desfasamientoX - (client.font.width(puntosCardinales[i]) / 2);
                    drawContext.drawString(client.font, puntosCardinales[i], dibujoPuntoX, currentY, 0xFFFFFF, true);
                }
            }

            currentY += 14;

            String textoCoords = "X: " + client.player.getBlockX() + "  Y: " + client.player.getBlockY() + "  Z: " + client.player.getBlockZ();
            int anchoCoords = client.font.width(textoCoords);
            drawContext.drawString(client.font, textoCoords, centroX - (anchoCoords / 2), currentY, 0xFFFFFF, true);

            currentY += 10;

            String textoPvp = pvpActivo ? "PvP Activado" : "PvP Desactivado";
            int colorPvp = pvpActivo ? 0xFF5555 : 0x55FF55;
            int anchoPvp = client.font.width(textoPvp);
            drawContext.drawString(client.font, textoPvp, centroX - (anchoPvp / 2), currentY, colorPvp, true);

            boolean tieneVenganzas = (venganzaTargetName != null && !venganzaTargetName.isEmpty());
            boolean tieneHunters = (hunterName != null && !hunterName.isEmpty());

            if (tieneVenganzas || tieneHunters) {
                drawContext.pose().pushPose();
                float scaleFactor = 0.95f;
                drawContext.pose().scale(scaleFactor, scaleFactor, 1.0f);
                int containerX = (int) (8 / scaleFactor);

                int countVenganzas = tieneVenganzas ? venganzaTargetName.split("\\|").length : 0;
                int countHunters = tieneHunters ? hunterName.split("\\|").length : 0;
                int spaceVenganzaTitle = tieneVenganzas ? 14 : 0;
                int spaceHunterTitle = tieneHunters ? 14 : 0;
                int totalHeight = (countVenganzas * 25) + (countHunters * 25) + spaceVenganzaTitle + spaceHunterTitle;

                int startContainerY = (int) (((height / 2) - (totalHeight / 2)) / scaleFactor);

                if (tieneVenganzas) {
                    drawContext.drawString(client.font, "§f🐰 VENGANZA", containerX, startContainerY, 0xFFFFFF, true);
                    startContainerY += 12;

                    String[] items = venganzaTargetName.split("\\|");
                    for (String entry : items) {
                        String[] data = entry.split(",");
                        if (data.length < 3) continue;

                        String name = data[0];
                        int nivel = Integer.parseInt(data[1]);
                        long restMs = Long.parseLong(data[2]);

                        int porcentaje = Math.max(0, 60 - (nivel * 20));

                        long totalSegs = restMs / 1000;
                        String timerStr = String.format("%02d:%02d", totalSegs / 60, totalSegs % 60);

                        drawContext.fill(containerX, startContainerY, containerX + 153, startContainerY + 22, 0x44141414);
                        drawContext.fill(containerX, startContainerY, containerX + 2, startContainerY + 22, 0xFFDD2C00);

                        float ratio = (float) restMs / (20L * 60L * 1000L);
                        int barW = (int) (153 * ratio);
                        drawContext.fill(containerX, startContainerY + 20, containerX + barW, startContainerY + 22, 0xFFDD2C00);

                        drawPlayerHead(drawContext, name, containerX + 5, startContainerY + 3, 13);
                        drawContext.renderFakeItem(new ItemStack(Items.FILLED_MAP), containerX + 21, startContainerY + 2);

                        drawContext.drawString(client.font, name, containerX + 40, startContainerY + 2, 0xFFFFFF, true);

                        drawContext.pose().pushPose();
                        drawContext.pose().translate(containerX + 40, startContainerY + 12, 0);
                        drawContext.pose().scale(0.65f, 0.65f, 1.0f);
                        drawContext.drawString(client.font, "§7⚔ +" + porcentaje + "%  🛡 +" + porcentaje + "%", 0, 0, 0xFFFFFF, true);
                        drawContext.pose().popPose();

                        int timerW = client.font.width(timerStr);
                        drawContext.drawString(client.font, timerStr, containerX + 149 - timerW, startContainerY + 3, 0xDFDFDF, true);

                        startContainerY += 25;
                    }
                }

                if (tieneHunters) {
                    if (tieneVenganzas) startContainerY += 5;

                    drawContext.drawString(client.font, "§f🐰 TE BUSCAN", containerX, startContainerY, 0xFFFFFF, true);
                    startContainerY += 12;

                    String[] items = hunterName.split("\\|");
                    for (String entry : items) {
                        String[] data = entry.split(",");
                        if (data.length < 3) continue;

                        String name = data[0];
                        int nivel = Integer.parseInt(data[1]);
                        long restMs = Long.parseLong(data[2]);

                        int porcentaje = Math.max(0, 60 - (nivel * 20));

                        long totalSegs = restMs / 1000;
                        String timerStr = String.format("%02d:%02d", totalSegs / 60, totalSegs % 60);

                        drawContext.fill(containerX, startContainerY, containerX + 153, startContainerY + 22, 0x44141414);
                        drawContext.fill(containerX, startContainerY, containerX + 2, startContainerY + 22, 0xFFFFAB00);

                        float ratio = (float) restMs / (20L * 60L * 1000L);
                        int barW = (int) (153 * ratio);
                        drawContext.fill(containerX, startContainerY + 20, containerX + barW, startContainerY + 22, 0xFFFFAB00);

                        drawPlayerHead(drawContext, name, containerX + 5, startContainerY + 3, 13);
                        drawContext.renderFakeItem(new ItemStack(Items.FILLED_MAP), containerX + 21, startContainerY + 2);

                        drawContext.drawString(client.font, name, containerX + 40, startContainerY + 2, 0xFFFFFF, true);

                        drawContext.pose().pushPose();
                        drawContext.pose().translate(containerX + 40, startContainerY + 12, 0);
                        drawContext.pose().scale(0.65f, 0.65f, 1.0f);
                        drawContext.drawString(client.font, "§7⚔ -" + porcentaje + "%  🛡 -" + porcentaje + "%", 0, 0, 0xFFFFFF, true);
                        drawContext.pose().popPose();

                        int timerW = client.font.width(timerStr);
                        drawContext.drawString(client.font, timerStr, containerX + 149 - timerW, startContainerY + 3, 0xDFDFDF, true);

                        startContainerY += 25;
                    }
                }
                drawContext.pose().popPose();
            }

            if (currentEventId != 0) {
                long tiempoTranscurrido = System.currentTimeMillis() - overlayStartTime;

                String folderName = "";
                int totalFrames = 0;
                int maxFrameIndex = 0;
                int duracionPorFrame = 40;
                int renderSizeX = 140; int renderSizeY = 140;

                switch (currentEventId) {
                    case 1 -> {
                        folderName = "pvpon";
                        totalFrames = 64; maxFrameIndex = 63; duracionPorFrame = 60;
                        renderSizeX = 196; renderSizeY = 196;
                    }
                    case 2 -> {
                        folderName = "pvpoff";
                        totalFrames = 64; maxFrameIndex = 63; duracionPorFrame = 60;
                        renderSizeX = 196; renderSizeY = 196;
                    }
                    case 3, 4 -> {
                        folderName = "vida_perdida";
                        totalFrames = 90; maxFrameIndex = 63; duracionPorFrame = 70;
                        renderSizeX = 230; renderSizeY = 230;
                    }
                    case 5 -> {
                        folderName = "eliminado";
                        totalFrames = 92; maxFrameIndex = 63; duracionPorFrame = 70;
                        renderSizeX = 230; renderSizeY = 230;
                    }
                    case 6, 7 -> {
                        folderName = "slot_perdido";
                        totalFrames = 90; maxFrameIndex = 63; duracionPorFrame = 70;
                        renderSizeX = 230; renderSizeY = 230;
                    }
                    case 8 -> {
                        folderName = "cambios";
                        totalFrames = 65; maxFrameIndex = 63; duracionPorFrame = 60;
                        renderSizeX = 200; renderSizeY = 200;
                    }
                    case 9 -> {
                        folderName = "cambios";
                        totalFrames = 65; maxFrameIndex = 63; duracionPorFrame = 35;
                        renderSizeX = 200; renderSizeY = 200;
                    }
                }

                int frameActual = (int) (tiempoTranscurrido / duracionPorFrame);

                if (frameActual < totalFrames && !folderName.isEmpty()) {
                    int indexFijo = Math.min(frameActual, maxFrameIndex);
                    ResourceLocation fotograma = Extr3mo.id("textures/gui/anim/" + folderName + "/frame_" + indexFijo + ".png");

                    int posX = centroX - (renderSizeX / 2);
                    int posY = (height / 2) - (renderSizeY / 2);

                    if (currentEventId == 3 || currentEventId == 4 || currentEventId == 5 || currentEventId == 6 || currentEventId == 7) {
                        posY -= 55;
                    } else if (currentEventId == 8 || currentEventId == 9) {
                        posY -= 21;
                        posX += 3;
                    } else {
                        posY -= 30;
                    }

                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

                    drawContext.blit(fotograma, posX, posY, renderSizeX, renderSizeY, 0.0F, 0.0F, 512, 512, 512, 512);

                    RenderSystem.disableBlend();

                    if ((currentEventId == 3 || currentEventId == 4 || currentEventId == 5 || currentEventId == 6 || currentEventId == 7) && latestVictimName != null && !latestVictimName.isEmpty()) {
                        String linea1 = "§e§l" + latestVictimName;
                        String linea2 = (currentEventId == 5) ? "§cHa sido ELIMINADO." :
                                (currentEventId == 6 || currentEventId == 7) ? "§c§lSLOT PERDIDO" : "§eHa perdido una vida.";

                        int textoY1 = posY + renderSizeY + 6;
                        int textoY2 = textoY1 + 12;

                        drawContext.drawString(client.font, linea1, centroX - (client.font.width(linea1) / 2), textoY1, 0xFFFFFF, true);
                        drawContext.drawString(client.font, linea2, centroX - (client.font.width(linea2) / 2), textoY2, 0xFFFFFF, true);
                    }
                } else {
                    if (currentEventId == 8 || currentEventId == 9) {
                        float pitchFinal = (currentEventId == 9) ? 1.71F : 1.0F;
                        client.getSoundManager().play(
                                SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(Extr3mo.id("noti")), pitchFinal)
                        );
                    }
                    currentEventId = 0;
                }
            }

            int hotbarRightX = (width / 2) + 98;
            int hotbarY = height - 22;

            ResourceLocation iconPeligro = Extr3mo.id("textures/gui/level" + peligrosidadNivel + ".png");
            drawContext.blit(iconPeligro, hotbarRightX, hotbarY, 0, 0, 16, 16, 16, 16);

            if (peligrosidadNivel > 0) {
                long timeLeft = peligrosidadExpiracion - System.currentTimeMillis();
                if (timeLeft > 0) {
                    long totalSegundos = timeLeft / 1000;
                    String tiempoTexto = String.format("%02d:%02d:%02d", totalSegundos / 3600, (totalSegundos % 3600) / 60, totalSegundos % 60);
                    if (peligrosidadNivel == 3 && killsNivel3 > 0) { tiempoTexto += " x" + (killsNivel3 + 1); }

                    int colorTimer = switch (peligrosidadNivel) { case 1 -> 0xFFFF55; case 2 -> 0xFFAA00; case 3 -> 0xFF5555; default -> 0xAAAAAA; };
                    drawContext.drawString(client.font, tiempoTexto, hotbarRightX + 20, hotbarY + 4, colorTimer, true);
                }
            } else {
                drawContext.drawString(client.font, "Neutral", hotbarRightX + 20, hotbarY + 4, 0xAAAAAA, true);
            }

            activeNotifications.removeIf(notif -> System.currentTimeMillis() - notif.startTime > 5000);
            int notifY = 6;

            for (int i = 0; i < activeNotifications.size(); i++) {
                DeathNotification notif = activeNotifications.get(i);
                long elapsed = System.currentTimeMillis() - notif.startTime;

                int textEliminoW = client.font.width("eliminó a");
                int textVictimW = client.font.width(notif.victimName);
                int totalWidth = 0;

                if (!notif.isEnvironmental) {
                    int textAttackerW = client.font.width(notif.attackerName);
                    int espacioEspada = notif.isAttackerPlayer ? (16 + 6) : 0;
                    totalWidth = 6 + 16 + 4 + textAttackerW + 6 + espacioEspada + textEliminoW + 6 + 16 + 4 + textVictimW + 6;
                } else {
                    int textEnvW = client.font.width(notif.envMessage);
                    totalWidth = 6 + 16 + 4 + 16 + 4 + textVictimW + 6 + textEnvW + 6;
                }

                float offsetX = 0;
                if (elapsed < 350) {
                    float p = elapsed / 350.0f;
                    offsetX = (1.0f - p) * (totalWidth + 20);
                } else if (elapsed > 4650) {
                    float p = (elapsed - 4650) / 350.0f;
                    if (p > 1.0f) p = 1.0f;
                    offsetX = p * (totalWidth + 20);
                }

                int xPos = width - totalWidth - 6 + (int) offsetX;
                int yPos = notifY;

                drawContext.fill(xPos, yPos, xPos + totalWidth, yPos + 22, 0x44141414);
                int accentColor = (notif.mode == 2) ? 0xFFFF1111 : 0xFFBC2626;
                drawContext.fill(xPos + totalWidth - 2, yPos, xPos + totalWidth, yPos + 22, accentColor);

                int currentX = xPos + 6;
                int contentY = yPos + 3;
                int textY = yPos + 7;

                if (!notif.isEnvironmental) {
                    if (notif.isAttackerPlayer) { drawPlayerHead(drawContext, notif.attackerName, currentX, contentY, 16); }
                    else { drawContext.renderFakeItem(getSpawnEggOrFallback(notif.attackerTypePath), currentX, contentY); }
                    currentX += 16 + 4;

                    drawContext.drawString(client.font, notif.attackerName, currentX, textY, 0xFFFFFF, true);
                    currentX += client.font.width(notif.attackerName) + 6;

                    if (notif.isAttackerPlayer) {
                        drawContext.renderFakeItem(new ItemStack(Items.DIAMOND_SWORD), currentX, contentY);
                        currentX += 16 + 6;
                    }

                    drawContext.drawString(client.font, "eliminó a", currentX, textY, 0xDFDFDF, true);
                    currentX += textEliminoW + 6;

                    drawPlayerHead(drawContext, notif.victimName, currentX, contentY, 16);
                    currentX += 16 + 4;
                    drawContext.drawString(client.font, notif.victimName, currentX, textY, 0xFFF06292, true);
                } else {
                    drawContext.renderFakeItem(new ItemStack(Items.SKELETON_SKULL), currentX, contentY);
                    currentX += 16 + 4;
                    drawPlayerHead(drawContext, notif.victimName, currentX, contentY, 16);
                    currentX += 16 + 4;
                    drawContext.drawString(client.font, notif.victimName, currentX, textY, 0xFFF06292, true);
                    currentX += textVictimW + 6;
                    drawContext.drawString(client.font, notif.envMessage, currentX, textY, 0xDFDFDF, true);
                }
                notifY += 28;
            }
        });
    }
}