package net.mcextremo.server;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.mcextremo.Extr3mo;
import net.mcextremo.PvpDamageHandler;
import net.mcextremo.network.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Extr3moServer implements DedicatedServerModInitializer {
    public static boolean pvpActivo = false;
    public static boolean pvpAleatorio = false;
    public static int ticksParaSiguienteCambio = 0;
    private static final Random RANDOM = new Random();

    // INTERRUPTORES MAESTROS DE SISTEMAS
    public static boolean sistemaVenganzaActivo = true;
    public static boolean sistemaPeligrosidadActivo = true;

    public static final long TIEMPO_PELIGROSIDAD_MS = 24L * 60L * 60L * 1000L;
    public static final long DURACION_VENGANZA_MS = 20L * 60L * 1000L;
    public static final Map<UUID, List<ItemStack>> INVENTARIOS_SEGUROS = new HashMap<>();
    public static final Map<UUID, Map<UUID, Long>> CRONOLOGIA_DE_ATANQUES = new ConcurrentHashMap<>();
    public static final Map<UUID, Set<UUID>> PRIMER_GOLPE_VENGANZA = new ConcurrentHashMap<>();

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();

    private static void ejecutarConRetraso(MinecraftServer server, long delayMs, Runnable task) {
        SCHEDULER.schedule(() -> {
            server.execute(task);
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private static void enviarAAdmins(MinecraftServer server, String mensaje) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.hasPermissions(2)) {
                p.sendSystemMessage(Component.literal(mensaje));
            }
        }
    }

    @Override
    public void onInitializeServer() {
        SafeZoneManager.cargar();
        PayloadTypeRegistry.playS2C().register(PublicLivesPayload.TYPE, PublicLivesPayload.CODEC);

        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer serverPlayer && !serverPlayer.hasPermissions(2)) {
                SafeZoneManager.SafeZone zona = SafeZoneManager.obtenerZonaEnPos(level.dimension().location().toString(), pos);
                if (zona != null && zona.proteccionBloques) {
                    if (state.is(Blocks.OAK_FENCE) || state.is(Blocks.PLAYER_HEAD) || state.is(Blocks.PLAYER_WALL_HEAD)) {
                        return true;
                    }
                    serverPlayer.sendSystemMessage(Component.literal("§c[Ex3]: No puedes modificar bloques en la zona segura: " + zona.nombre), true);
                    return false;
                }
            }
            return true;
        });

        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer && !serverPlayer.hasPermissions(2)) {
                BlockPos pos = hitResult.getBlockPos();
                SafeZoneManager.SafeZone zona = SafeZoneManager.obtenerZonaEnPos(level.dimension().location().toString(), pos);
                if (zona != null && zona.proteccionBloques) {
                    net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
                    if (state.is(Blocks.OAK_FENCE) || state.is(Blocks.PLAYER_HEAD) || state.is(Blocks.PLAYER_WALL_HEAD)) {
                        return InteractionResult.PASS;
                    }
                    serverPlayer.sendSystemMessage(Component.literal("§c[Ex3]: Zonas protegidas activas (" + zona.nombre + ")"), true);
                    return InteractionResult.FAIL;
                }
            }
            return InteractionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack itemStack = player.getItemInHand(hand);
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer && itemStack.is(Extr3mo.ELIXIR_DE_LA_REDENCION)) {
                ExtremoData data = ExtremoData.getServerState(serverPlayer.getServer());
                int vidasActuales = data.getVidas(player.getUUID());
                int vidasRotas = data.getVidasRotas(player.getUUID());
                int maxVidasPermitidas = 5 - vidasRotas;

                if (vidasActuales < maxVidasPermitidas) {
                    data.setVidas(player.getUUID(), vidasActuales + 1);
                    itemStack.shrink(1);
                    serverPlayer.sendSystemMessage(Component.literal("§a[Ex3]: Has recuperado una vida."));
                    sincronizarJugador(serverPlayer);
                } else {
                    serverPlayer.sendSystemMessage(Component.literal("§c[Ex3]: No puedes recuperar más vidas (Tus corazones rotos son permanentes)."));
                }
                return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide());
            }
            return InteractionResultHolder.pass(itemStack);
        });

        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, amount, heading, killed) -> {
            if (entity instanceof ServerPlayer victima && source.getEntity() instanceof ServerPlayer atacante) {
                if (victima != atacante) {
                    CRONOLOGIA_DE_ATANQUES.computeIfAbsent(victima.getUUID(), k -> new ConcurrentHashMap<>())
                            .put(atacante.getUUID(), System.currentTimeMillis());
                }
            }
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer victima && source.getEntity() instanceof ServerPlayer atacante && victima != atacante) {

                SafeZoneManager.SafeZone zonaAtacante = SafeZoneManager.obtenerZonaEn(atacante);
                SafeZoneManager.SafeZone zonaVictima = SafeZoneManager.obtenerZonaEn(victima);
                boolean bloqueadoPorZona = (zonaAtacante != null && !zonaAtacante.pvpPermitido) || (zonaVictima != null && !zonaVictima.pvpPermitido);

                Map<UUID, Long> venganzasAtacante = VenganzaManager.venganzasActivas.get(atacante.getUUID());
                boolean atacanteTieneVenganzaActiva = sistemaVenganzaActivo && venganzasAtacante != null && venganzasAtacante.containsKey(victima.getUUID());

                Map<UUID, Long> venganzasVictima = VenganzaManager.venganzasActivas.get(victima.getUUID());
                boolean victimaTieneVenganzaActiva = sistemaVenganzaActivo && venganzasVictima != null && venganzasVictima.containsKey(atacante.getUUID());

                long ahora = System.currentTimeMillis();
                boolean enCombateZonalActivo = false;
                if (CRONOLOGIA_DE_ATANQUES.containsKey(victima.getUUID()) && CRONOLOGIA_DE_ATANQUES.get(victima.getUUID()).containsKey(atacante.getUUID())) {
                    if (ahora - CRONOLOGIA_DE_ATANQUES.get(victima.getUUID()).get(atacante.getUUID()) < 20000) enCombateZonalActivo = true;
                }
                if (CRONOLOGIA_DE_ATANQUES.containsKey(atacante.getUUID()) && CRONOLOGIA_DE_ATANQUES.get(atacante.getUUID()).containsKey(victima.getUUID())) {
                    if (ahora - CRONOLOGIA_DE_ATANQUES.get(atacante.getUUID()).get(victima.getUUID()) < 20000) enCombateZonalActivo = true;
                }

                if (bloqueadoPorZona) {
                    if (atacanteTieneVenganzaActiva || (victimaTieneVenganzaActiva && enCombateZonalActivo)) {
                        return true;
                    }
                    atacante.displayClientMessage(Component.literal("§c[Ex3]: El PvP se encuentra deshabilitado en esta zona segura."), true);
                    return false;
                }

                if (pvpActivo) return true;

                if (victimaTieneVenganzaActiva) {
                    Set<UUID> golpeados = PRIMER_GOLPE_VENGANZA.get(victima.getUUID());
                    if (golpeados == null || !golpeados.contains(atacante.getUUID())) {
                        atacante.displayClientMessage(Component.literal("§c[Ex3]: La víctima debe dar el primer golpe para activar el PvP de Venganza."), true);
                        return false;
                    }
                }

                if (atacanteTieneVenganzaActiva) {
                    PRIMER_GOLPE_VENGANZA.computeIfAbsent(atacante.getUUID(), k -> ConcurrentHashMap.newKeySet()).add(victima.getUUID());
                }
            }
            return true;
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register(new PvpDamageHandler());

        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
            if (entity instanceof ServerPlayer player) {
                ExtremoData data = ExtremoData.getServerState(player.getServer());
                int vidasRestantes = data.getVidas(player.getUUID()) - 1;

                if (vidasRestantes > 0) {
                    List<ItemStack> guardado = new ArrayList<>();
                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                        guardado.add(player.getInventory().getItem(i).copy());
                        player.getInventory().setItem(i, ItemStack.EMPTY);
                    }
                    INVENTARIOS_SEGUROS.put(player.getUUID(), guardado);
                } else {
                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                        ItemStack objetoRanura = player.getInventory().getItem(i);
                        if (!objetoRanura.isEmpty()) {
                            player.drop(objetoRanura, true, false);
                            player.getInventory().setItem(i, ItemStack.EMPTY);
                        }
                    }
                }
            }
            return true;
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayer victima) {
                MinecraftServer server = victima.getServer();
                if (server == null) return;
                ExtremoData data = ExtremoData.getServerState(server);
                UUID idVictima = victima.getUUID();

                boolean esMuerteConCorazonRoto = sistemaPeligrosidadActivo && (data.getPeligrosidadNivel(idVictima) == 3);

                if (esMuerteConCorazonRoto) {
                    data.setVidasRotas(idVictima, data.getVidasRotas(idVictima) + 1);
                    enviarAAdmins(server, "§c[Ex3]: §4¡" + victima.getScoreboardName() + " murió con Peligrosidad 3 y ha recibido un CORAZÓN ROTO permanente!");
                }

                data.setVidas(idVictima, data.getVidas(idVictima) - 1);
                int vidasRestantes = data.getVidas(idVictima);

                if (vidasRestantes <= 0) {
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        ServerPlayNetworking.send(p, new TriggerEventPayload(5));
                    }
                    enviarAAdmins(server, "§4[Ex3]: §c" + victima.getScoreboardName() + " fue ELIMINADO permanentemente.");
                } else {
                    if (esMuerteConCorazonRoto) {
                        ServerPlayNetworking.send(victima, new TriggerEventPayload(6));
                        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                            if (p != victima) {
                                ServerPlayNetworking.send(p, new TriggerEventPayload(7));
                            }
                        }
                    } else {
                        ServerPlayNetworking.send(victima, new TriggerEventPayload(3));
                        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                            if (p != victima) {
                                ServerPlayNetworking.send(p, new TriggerEventPayload(4));
                            }
                        }
                    }
                }

                String victimName = victima.getScoreboardName();
                String attackerName = "";
                boolean isAttackerPlayer = false;
                boolean isEnvironmental = true;
                String envMessage = "";
                String attackerTypePath = "";

                Entity attackerEntity = damageSource.getEntity();

                if (attackerEntity instanceof ServerPlayer playerAttacker) {
                    attackerName = playerAttacker.getScoreboardName();
                    isAttackerPlayer = true;
                    isEnvironmental = false;
                } else if (attackerEntity instanceof LivingEntity livingAttacker) {
                    attackerName = livingAttacker.getDisplayName().getString();
                    isAttackerPlayer = false;
                    isEnvironmental = false;
                    attackerTypePath = BuiltInRegistries.ENTITY_TYPE.getKey(livingAttacker.getType()).toString();
                } else {
                    isEnvironmental = true;
                    String fullMsg = damageSource.getLocalizedDeathMessage(victima).getString();
                    envMessage = fullMsg.replace(victimName + " ", "");
                }

                int modoNotif = (vidasRestantes <= 0) ? 2 : 1;
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    ServerPlayNetworking.send(p, new DeathNotificationPayload(
                            victimName, attackerName, isAttackerPlayer, isEnvironmental, envMessage, attackerTypePath, modoNotif
                    ));
                }

                Set<UUID> todosLosAtacantes = new HashSet<>();
                if (attackerEntity instanceof ServerPlayer pKill) {
                    todosLosAtacantes.add(pKill.getUUID());
                }

                Map<UUID, Long> registroReciente = CRONOLOGIA_DE_ATANQUES.get(idVictima);
                if (registroReciente != null) {
                    long ahoraLimite = System.currentTimeMillis() - 20000;
                    for (Map.Entry<UUID, Long> entrada : registroReciente.entrySet()) {
                        if (entrada.getValue() > ahoraLimite) {
                            todosLosAtacantes.add(entrada.getKey());
                        }
                    }
                }

                for (UUID idAsesino : todosLosAtacantes) {
                    ServerPlayer asesino = server.getPlayerList().getPlayer(idAsesino);
                    if (asesino == null || asesino == victima) continue;

                    Map<UUID, Long> misVenganzasActivas = VenganzaManager.venganzasActivas.get(asesino.getUUID());

                    if (sistemaVenganzaActivo && misVenganzasActivas != null && misVenganzasActivas.containsKey(idVictima)) {
                        misVenganzasActivas.remove(idVictima);
                        VenganzaManager.nivelesPorVenganza.remove(asesino.getUUID().toString() + "->" + idVictima.toString());

                        int vidasAsesino = data.getVidas(asesino.getUUID());
                        int rotasAsesino = data.getVidasRotas(asesino.getUUID());
                        if (vidasAsesino < (5 - rotasAsesino)) {
                            data.setVidas(asesino.getUUID(), vidasAsesino + 1);
                        }
                        enviarAAdmins(server, "§6[Venganza]: §f¡" + asesino.getScoreboardName() + " ha cobrado su VENGANZA de " + victima.getScoreboardName() + "!");
                    } else {
                        long ahora = System.currentTimeMillis();

                        if (sistemaPeligrosidadActivo) {
                            int nivelAsesino = data.getPeligrosidadNivel(idAsesino);
                            if (nivelAsesino == 0) {
                                data.setPeligrosidadNivel(idAsesino, 1);
                                data.setPeligrosidadExpiracion(idAsesino, ahora + TIEMPO_PELIGROSIDAD_MS);
                                data.setKillsNivel3(idAsesino, 0);
                            } else if (nivelAsesino == 1) {
                                data.setPeligrosidadNivel(idAsesino, 2);
                                data.setPeligrosidadExpiracion(idAsesino, ahora + TIEMPO_PELIGROSIDAD_MS);
                            } else if (nivelAsesino == 2) {
                                data.setPeligrosidadNivel(idAsesino, 3);
                                data.setPeligrosidadExpiracion(idAsesino, ahora + TIEMPO_PELIGROSIDAD_MS);
                                data.setKillsNivel3(idAsesino, 0);
                            } else if (nivelAsesino == 3) {
                                data.setKillsNivel3(idAsesino, data.getKillsNivel3(idAsesino) + 1);
                                data.setPeligrosidadExpiracion(idAsesino, ahora + TIEMPO_PELIGROSIDAD_MS);
                            }
                        }

                        if (sistemaVenganzaActivo) {
                            if (vidasRestantes <= 0) {
                                Map<UUID, Long> objetivosDeVictima = VenganzaManager.venganzasActivas.get(idVictima);
                                if (objetivosDeVictima != null) {
                                    objetivosDeVictima.remove(idAsesino);
                                }
                                VenganzaManager.nivelesPorVenganza.remove(idVictima.toString() + "->" + idAsesino.toString());

                                if (misVenganzasActivas != null) {
                                    misVenganzasActivas.remove(idVictima);
                                }
                                VenganzaManager.nivelesPorVenganza.remove(idAsesino.toString() + "->" + idAsesino.toString());
                            } else {
                                Map<UUID, Long> objetivosDeVictima = VenganzaManager.venganzasActivas.computeIfAbsent(idVictima, k -> new ConcurrentHashMap<>());
                                objetivosDeVictima.put(idAsesino, ahora + DURACION_VENGANZA_MS);
                            }
                        }
                    }
                    sincronizarJugador(asesino);
                }

                CRONOLOGIA_DE_ATANQUES.remove(idVictima);
                CRONOLOGIA_DE_ATANQUES.values().forEach(mapaProvocacion -> mapaProvocacion.remove(idVictima));

                PRIMER_GOLPE_VENGANZA.remove(idVictima);
                PRIMER_GOLPE_VENGANZA.values().forEach(set -> set.remove(idVictima));

                BlockPos pos = victima.blockPosition();
                ServerLevel level = (ServerLevel) victima.level();
                level.setBlockAndUpdate(pos, Blocks.OAK_FENCE.defaultBlockState());
                level.setBlockAndUpdate(pos.above(), Blocks.PLAYER_HEAD.defaultBlockState());
                BlockEntity be = level.getBlockEntity(pos.above());
                if (be instanceof SkullBlockEntity skull) {
                    skull.setOwner(new ResolvableProfile(victima.getGameProfile()));
                    be.setChanged();
                }

                sincronizarJugador(victima);
            }
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            ExtremoData data = ExtremoData.getServerState(newPlayer.getServer());
            if (data.getVidas(newPlayer.getUUID()) <= 0) {
                newPlayer.setGameMode(GameType.SPECTATOR);
            } else {
                if (INVENTARIOS_SEGUROS.containsKey(newPlayer.getUUID())) {
                    List<ItemStack> guardado = INVENTARIOS_SEGUROS.remove(newPlayer.getUUID());
                    for (int i = 0; i < guardado.size(); i++) {
                        newPlayer.getInventory().setItem(i, guardado.get(i));
                    }
                }
            }
            sincronizarJugador(newPlayer);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            ExtremoData data = ExtremoData.getServerState(server);

            long ahora = System.currentTimeMillis();
            long exp = data.getPeligrosidadExpiracion(player.getUUID());

            if (sistemaPeligrosidadActivo && exp > 0 && ahora >= exp) {
                data.setPeligrosidadNivel(player.getUUID(), 0);
                data.setPeligrosidadExpiracion(player.getUUID(), 0L);
                data.setKillsNivel3(player.getUUID(), 0);
            }

            sincronizarJugador(player);
            ServerPlayNetworking.send(player, new SyncPvpPayload(pvpActivo));
        });

        ServerTickEvents.START_SERVER_TICK.register(server -> {
            if (pvpAleatorio) {
                ticksParaSiguienteCambio--;
                if (ticksParaSiguienteCambio <= 0) {
                    pvpActivo = !pvpActivo;
                    notificarCambioPvp(server);
                    ticksParaSiguienteCambio = 3600 + RANDOM.nextInt(6000);
                }
            }

            if (server.getTickCount() % 20 == 0) {
                ExtremoData data = ExtremoData.getServerState(server);
                long ahora = System.currentTimeMillis();

                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    boolean requiereSincronizacion = false;

                    long exp = data.getPeligrosidadExpiracion(p.getUUID());
                    if (sistemaPeligrosidadActivo && exp > 0 && ahora >= exp) {
                        data.setPeligrosidadNivel(p.getUUID(), 0);
                        data.setPeligrosidadExpiracion(p.getUUID(), 0L);
                        data.setKillsNivel3(p.getUUID(), 0);
                        requiereSincronizacion = true;
                    }

                    Map<UUID, Long> mapaObjetivos = VenganzaManager.venganzasActivas.get(p.getUUID());
                    if (sistemaVenganzaActivo && mapaObjetivos != null && !mapaObjetivos.isEmpty()) {
                        Iterator<Map.Entry<UUID, Long>> iterator = mapaObjetivos.entrySet().iterator();
                        while (iterator.hasNext()) {
                            Map.Entry<UUID, Long> entrada = iterator.next();
                            if (ahora > entrada.getValue()) {
                                UUID targetId = entrada.getKey();
                                iterator.remove();
                                VenganzaManager.nivelesPorVenganza.remove(p.getUUID().toString() + "->" + targetId.toString());
                                requiereSincronizacion = true;
                            }
                        }
                    }

                    if (!requiereSincronizacion) {
                        if (sistemaVenganzaActivo && mapaObjetivos != null && !mapaObjetivos.isEmpty()) {
                            requiereSincronizacion = true;
                        } else if (sistemaVenganzaActivo) {
                            for (ServerPlayer otro : server.getPlayerList().getPlayers()) {
                                Map<UUID, Long> otrosObjetivos = VenganzaManager.venganzasActivas.get(otro.getUUID());
                                if (otrosObjetivos != null && otrosObjetivos.containsKey(p.getUUID())) {
                                    long restanteMs = otrosObjetivos.get(p.getUUID()) - ahora;
                                    if (restanteMs > 0) {
                                        requiereSincronizacion = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    if (requiereSincronizacion) {
                        sincronizarJugador(p);
                    }
                }
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("ex3moon")
                    .then(Commands.literal("zona")
                            .requires(source -> source.hasPermission(2))
                            .then(Commands.literal("crear")
                                    .then(Commands.argument("nombre", StringArgumentType.word())
                                            .then(Commands.argument("pos1", BlockPosArgument.blockPos())
                                                    .then(Commands.argument("pos2", BlockPosArgument.blockPos())
                                                            .executes(context -> {
                                                                String nombre = StringArgumentType.getString(context, "nombre");
                                                                BlockPos p1 = BlockPosArgument.getLoadedBlockPos(context, "pos1");
                                                                BlockPos p2 = BlockPosArgument.getLoadedBlockPos(context, "pos2");
                                                                String dim = context.getSource().getLevel().dimension().location().toString();

                                                                SafeZoneManager.SafeZone nueva = new SafeZoneManager.SafeZone(nombre, dim, p1, p2);
                                                                SafeZoneManager.ZONAS.put(nombre, nueva);
                                                                SafeZoneManager.guardar();

                                                                context.getSource().sendSuccess(() -> Component.literal("§a[Ex3]: Zona '" + nombre + "' creada con éxito."), true);
                                                                return 1;
                                                            })
                                                    )
                                            )
                                    )
                            )
                            .then(Commands.literal("eliminar")
                                    .then(Commands.argument("nombre", StringArgumentType.word())
                                            .suggests((context, builder) -> {
                                                SafeZoneManager.ZONAS.keySet().forEach(builder::suggest);
                                                return builder.buildFuture();
                                            })
                                            .executes(context -> {
                                                String nombre = StringArgumentType.getString(context, "nombre");
                                                if (SafeZoneManager.ZONAS.remove(nombre) != null) {
                                                    SafeZoneManager.guardar();
                                                    context.getSource().sendSuccess(() -> Component.literal("§e[Ex3]: Zona '" + nombre + "' eliminada."), true);
                                                    return 1;
                                                }
                                                context.getSource().sendFailure(Component.literal("§c[Ex3]: Esa zona no existe."));
                                                return 0;
                                            })
                                    )
                            )
                            .then(Commands.literal("pvp")
                                    .then(Commands.argument("nombre", StringArgumentType.word())
                                            .suggests((context, builder) -> {
                                                SafeZoneManager.ZONAS.keySet().forEach(builder::suggest);
                                                return builder.buildFuture();
                                            })
                                            .then(Commands.argument("permitido", BoolArgumentType.bool())
                                                    .executes(context -> {
                                                        String nombre = StringArgumentType.getString(context, "nombre");
                                                        boolean pvp = BoolArgumentType.getBool(context, "permitido");
                                                        SafeZoneManager.SafeZone z = SafeZoneManager.ZONAS.get(nombre);
                                                        if (z != null) {
                                                            z.pvpPermitido = pvp;
                                                            SafeZoneManager.guardar();
                                                            context.getSource().sendSuccess(() -> Component.literal("§a[Ex3]: PvP en '" + nombre + "' cambiado a: " + pvp), true);
                                                            return 1;
                                                        }
                                                        context.getSource().sendFailure(Component.literal("§c[Ex3]: Zona no encontrada."));
                                                        return 0;
                                                    })
                                            )
                                    )
                            )
                            .then(Commands.literal("proteccion")
                                    .then(Commands.argument("nombre", StringArgumentType.word())
                                            .suggests((context, builder) -> {
                                                SafeZoneManager.ZONAS.keySet().forEach(builder::suggest);
                                                return builder.buildFuture();
                                            })
                                            .then(Commands.argument("protegido", BoolArgumentType.bool())
                                                    .executes(context -> {
                                                        String nombre = StringArgumentType.getString(context, "nombre");
                                                        boolean prot = BoolArgumentType.getBool(context, "protegido");
                                                        SafeZoneManager.SafeZone z = SafeZoneManager.ZONAS.get(nombre);
                                                        if (z != null) {
                                                            z.proteccionBloques = prot;
                                                            SafeZoneManager.guardar();
                                                            context.getSource().sendSuccess(() -> Component.literal("§a[Ex3]: Protección de bloques en '" + nombre + "' fijada en: " + prot), true);
                                                            return 1;
                                                        }
                                                        context.getSource().sendFailure(Component.literal("§c[Ex3]: Zona no encontrada."));
                                                        return 0;
                                                    })
                                            )
                                    )
                            )
                            .then(Commands.literal("lista")
                                    .executes(context -> {
                                        if (SafeZoneManager.ZONAS.isEmpty()) {
                                            context.getSource().sendSuccess(() -> Component.literal("§7No hay zonas seguras configuradas."), false);
                                            return 1;
                                        }
                                        context.getSource().sendSuccess(() -> Component.literal("§6=== LISTA DE ZONAS EX3MO ==="), false);
                                        for (SafeZoneManager.SafeZone z : SafeZoneManager.ZONAS.values()) {
                                            context.getSource().sendSuccess(() -> Component.literal("§e• " + z.nombre + " §7- PvP: " + (z.pvpPermitido ? "§aSI" : "§cNO") + " §7| Protegida: " + (z.proteccionBloques ? "§aSI" : "§cNO")), false);
                                        }
                                        return 1;
                                    })
                            )
                    )
                    .then(Commands.literal("pvp")
                            .requires(source -> source.hasPermission(2))
                            .executes(context -> {
                                pvpActivo = !pvpActivo;
                                pvpAleatorio = false;
                                notificarCambioPvp(context.getSource().getServer());
                                return 1;
                            })
                            .then(Commands.literal("aleatorio")
                                    .executes(context -> {
                                        pvpAleatorio = !pvpAleatorio;
                                        MinecraftServer srv = context.getSource().getServer();
                                        if (pvpAleatorio) {
                                            ticksParaSiguienteCambio = 3600 + RANDOM.nextInt(6000);
                                            srv.getPlayerList().broadcastSystemMessage(Component.literal("§a[Ex3]: ¡El ciclo de PvP Aleatorio ha sido ACTIVADO!"), false);
                                        } else {
                                            srv.getPlayerList().broadcastSystemMessage(Component.literal("§c[Ex3]: PvP Aleatorio DESACTIVADO (Modo manual activo)."), false);
                                        }
                                        return 1;
                                    })
                            )
                    )
                    .then(Commands.literal("anuncio")
                            .requires(source -> source.hasPermission(2))
                            .then(Commands.argument("velocidad", StringArgumentType.word())
                                    .suggests((context, builder) -> builder.suggest("normal").suggest("rapido").buildFuture())
                                    .then(Commands.argument("color", StringArgumentType.word())
                                            .suggests((context, builder) -> builder.suggest("c").suggest("a").suggest("e").suggest("d").suggest("6").suggest("b").suggest("f").buildFuture())
                                            .then(Commands.argument("titulo", StringArgumentType.string())
                                                    .then(Commands.argument("mensaje", StringArgumentType.greedyString())
                                                            .executes(context -> {
                                                                String vel = StringArgumentType.getString(context, "velocidad");
                                                                String colorCode = StringArgumentType.getString(context, "color");
                                                                String titulo = StringArgumentType.getString(context, "titulo");
                                                                String msgBruto = StringArgumentType.getString(context, "mensaje");
                                                                MinecraftServer srv = context.getSource().getServer();

                                                                if (msgBruto.startsWith("\"") && msgBruto.endsWith("\"") && msgBruto.length() > 1) {
                                                                    msgBruto = msgBruto.substring(1, msgBruto.length() - 1);
                                                                }
                                                                final String mensajeFinal = msgBruto;

                                                                boolean esRapido = vel.equalsIgnoreCase("rapido");
                                                                int pckId = esRapido ? 9 : 8;
                                                                long tiempoDeEspera = esRapido ? (65 * 35L) : (65 * 60L);

                                                                for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                                                                    ServerPlayNetworking.send(p, new TriggerEventPayload(pckId));
                                                                }

                                                                ejecutarConRetraso(srv, tiempoDeEspera, () -> {
                                                                    String chatFormat = "§" + colorCode + "§l" + titulo + "\n§" + colorCode + mensajeFinal;
                                                                    srv.getPlayerList().broadcastSystemMessage(Component.literal(chatFormat), false);
                                                                });
                                                                return 1;
                                                            })
                                                    )
                                            )
                                    )
                            )
                    )
                    // =====================================================================================
                    // NUEVO: INTERRUPTOR GLOBAL DE SISTEMA DE VENGANZA
                    // =====================================================================================
                    .then(Commands.literal("venganza")
                            .requires(source -> source.hasPermission(2))
                            .then(Commands.literal("sistema")
                                    .then(Commands.argument("habilitado", BoolArgumentType.bool())
                                            .executes(context -> {
                                                sistemaVenganzaActivo = BoolArgumentType.getBool(context, "habilitado");
                                                MinecraftServer srv = context.getSource().getServer();
                                                String msg = sistemaVenganzaActivo ? "§a[Ex3]: Sistema de Venganzas HABILITADO." : "§c[Ex3]: Sistema de Venganzas DESHABILITADO.";
                                                srv.getPlayerList().broadcastSystemMessage(Component.literal(msg), false);
                                                for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                                                    sincronizarJugador(p);
                                                }
                                                return 1;
                                            })
                                    )
                            )
                    )
                    // =====================================================================================
                    // NUEVO: INTERRUPTOR GLOBAL DE SISTEMA DE PELIGROSIDAD
                    // =====================================================================================
                    .then(Commands.literal("peligrosidad")
                            .requires(source -> source.hasPermission(2))
                            .then(Commands.literal("sistema")
                                    .then(Commands.argument("habilitado", BoolArgumentType.bool())
                                            .executes(context -> {
                                                sistemaPeligrosidadActivo = BoolArgumentType.getBool(context, "habilitado");
                                                MinecraftServer srv = context.getSource().getServer();
                                                String msg = sistemaPeligrosidadActivo ? "§a[Ex3]: Sistema de Peligrosidad HABILITADO." : "§c[Ex3]: Sistema de Peligrosidad DESHABILITADO.";
                                                srv.getPlayerList().broadcastSystemMessage(Component.literal(msg), false);
                                                for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                                                    sincronizarJugador(p);
                                                }
                                                return 1;
                                            })
                                    )
                            )
                            .then(Commands.argument("jugador", EntityArgument.player())
                                    .then(Commands.argument("nivel", StringArgumentType.word())
                                            .suggests((context, builder) -> builder.suggest("neutral").suggest("1").suggest("2").suggest("3").buildFuture())
                                            .executes(context -> {
                                                ServerPlayer target = EntityArgument.getPlayer(context, "jugador");
                                                String valor = StringArgumentType.getString(context, "nivel");
                                                ExtremoData data = ExtremoData.getServerState(context.getSource().getServer());

                                                int nivel = 0;
                                                if (valor.equalsIgnoreCase("neutral")) nivel = 0;
                                                else if (valor.equals("1")) nivel = 1;
                                                else if (valor.equals("2")) nivel = 2;
                                                else if (valor.equals("3")) nivel = 3;
                                                else {
                                                    context.getSource().sendFailure(Component.literal("§c[Ex3]: Nivel no válido. Usa neutral, 1, 2 o 3."));
                                                    return 0;
                                                }

                                                data.setPeligrosidadNivel(target.getUUID(), nivel);
                                                if (nivel > 0) {
                                                    data.setPeligrosidadExpiracion(target.getUUID(), System.currentTimeMillis() + TIEMPO_PELIGROSIDAD_MS);
                                                } else {
                                                    data.setPeligrosidadExpiracion(target.getUUID(), 0L);
                                                    data.setKillsNivel3(target.getUUID(), 0);
                                                }

                                                sincronizarJugador(target);
                                                int finalNivel = nivel;
                                                context.getSource().sendSuccess(() -> Component.literal("§a[Ex3]: Peligrosidad de " + target.getScoreboardName() + " establecida en " + (finalNivel == 0 ? "Neutral" : "Nivel " + finalNivel)), true);
                                                return 1;
                                            })
                                    )
                            )
                    )
                    .then(Commands.literal("vidas")
                            .then(Commands.literal("check")
                                    .then(Commands.argument("jugador", EntityArgument.player())
                                            .executes(context -> {
                                                ServerPlayer target = EntityArgument.getPlayer(context, "jugador");
                                                ExtremoData data = ExtremoData.getServerState(context.getSource().getServer());
                                                int v = data.getVidas(target.getUUID());
                                                int vr = data.getVidasRotas(target.getUUID());
                                                int maxPosible = 5 - vr;

                                                context.getSource().sendSuccess(() -> Component.literal("§e[Ex3] Estado de " + target.getScoreboardName() + ": §f" + v + " Vidas §7/ §c" + vr + " Corazones Rotos §8(Máx. actual: " + maxPosible + ")"), false);
                                                return 1;
                                            })
                                    )
                            )
                            .then(Commands.literal("add")
                                    .requires(source -> source.hasPermission(2))
                                    .then(Commands.argument("jugador", EntityArgument.player())
                                            .then(Commands.argument("cantidad", IntegerArgumentType.integer(1))
                                                    .executes(context -> {
                                                        ServerPlayer target = EntityArgument.getPlayer(context, "jugador");
                                                        int cantidad = IntegerArgumentType.getInteger(context, "cantidad");
                                                        ExtremoData data = ExtremoData.getServerState(context.getSource().getServer());
                                                        int currentVidas = data.getVidas(target.getUUID());
                                                        int rotas = data.getVidasRotas(target.getUUID());
                                                        int maxPermitido = 5 - rotas;

                                                        int nuevasVidas = Math.max(0, Math.min(maxPermitido, currentVidas + cantidad));
                                                        data.setVidas(target.getUUID(), nuevasVidas);
                                                        sincronizarJugador(target);

                                                        context.getSource().sendSuccess(() -> Component.literal("§a[Ex3]: Añadidas " + cantidad + " vidas a " + target.getScoreboardName() + ". Ahora tiene " + nuevasVidas), true);
                                                        return 1;
                                                    })
                                            )
                                    )
                            )
                            .then(Commands.literal("set")
                                    .requires(source -> source.hasPermission(2))
                                    .then(Commands.argument("jugador", EntityArgument.player())
                                            .then(Commands.argument("cantidad", IntegerArgumentType.integer(0, 5))
                                                    .executes(context -> {
                                                        ServerPlayer target = EntityArgument.getPlayer(context, "jugador");
                                                        int cantidad = IntegerArgumentType.getInteger(context, "cantidad");
                                                        ExtremoData data = ExtremoData.getServerState(context.getSource().getServer());

                                                        data.setVidas(target.getUUID(), cantidad);
                                                        sincronizarJugador(target);

                                                        context.getSource().sendSuccess(() -> Component.literal("§a[Ex3]: Vidas de " + target.getScoreboardName() + " establecidas en " + cantidad), true);
                                                        return 1;
                                                    })
                                            )
                                    )
                            )
                            .then(Commands.literal("broken_add")
                                    .requires(source -> source.hasPermission(2))
                                    .then(Commands.argument("jugador", EntityArgument.player())
                                            .then(Commands.argument("cantidad", IntegerArgumentType.integer(1))
                                                    .executes(context -> {
                                                        ServerPlayer target = EntityArgument.getPlayer(context, "jugador");
                                                        int cantidad = IntegerArgumentType.getInteger(context, "cantidad");
                                                        ExtremoData data = ExtremoData.getServerState(context.getSource().getServer());
                                                        int currentBroken = data.getVidasRotas(target.getUUID());
                                                        int nuevosBroken = Math.max(0, Math.min(5, currentBroken + cantidad));

                                                        data.setVidasRotas(target.getUUID(), nuevosBroken);
                                                        int maxPermitido = 5 - nuevosBroken;
                                                        if (data.getVidas(target.getUUID()) > maxPermitido) {
                                                            data.setVidas(target.getUUID(), maxPermitido);
                                                        }

                                                        sincronizarJugador(target);
                                                        context.getSource().sendSuccess(() -> Component.literal("§c[Ex3]: Añados " + cantidad + " Corazones Rotos a " + target.getScoreboardName() + ". Total: " + nuevosBroken), true);
                                                        return 1;
                                                    })
                                            )
                                    )
                            )
                            .then(Commands.literal("broken_set")
                                    .requires(source -> source.hasPermission(2))
                                    .then(Commands.argument("jugador", EntityArgument.player())
                                            .then(Commands.argument("cantidad", IntegerArgumentType.integer(0, 5))
                                                    .executes(context -> {
                                                        ServerPlayer target = EntityArgument.getPlayer(context, "jugador");
                                                        int cantidad = IntegerArgumentType.getInteger(context, "cantidad");
                                                        ExtremoData data = ExtremoData.getServerState(context.getSource().getServer());

                                                        data.setVidasRotas(target.getUUID(), cantidad);
                                                        int maxPermitido = 5 - cantidad;
                                                        if (data.getVidas(target.getUUID()) > maxPermitido) {
                                                            data.setVidas(target.getUUID(), maxPermitido);
                                                        }

                                                        sincronizarJugador(target);
                                                        context.getSource().sendSuccess(() -> Component.literal("§c[Ex3]: Corazones Rotos de " + target.getScoreboardName() + " establecidos en " + cantidad), true);
                                                        return 1;
                                                    })
                                            )
                                    )
                            )
                    )
            );
        });
    }

    private static void notificarCambioPvp(MinecraftServer server) {
        CRONOLOGIA_DE_ATANQUES.clear();

        if (pvpActivo) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                ServerPlayNetworking.send(p, new SyncPvpPayload(true));
                ServerPlayNetworking.send(p, new TriggerEventPayload(1));
            }

            ejecutarConRetraso(server, 4840L, () -> {
                String comunicadoPvp = "§c§l▶ PVP ACTIVADO:\n" +
                        "§cEl PvP ha sido activado. Durante este período podrás\n" +
                        "§cgolpear a otros jugadores. Si tienes una Venganza activa\n" +
                        "§cpodrás combatir incluso en zonas seguras.";
                server.getPlayerList().broadcastSystemMessage(Component.literal(comunicadoPvp), false);
            });
        } else {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                ServerPlayNetworking.send(p, new SyncPvpPayload(false));
                ServerPlayNetworking.send(p, new TriggerEventPayload(2));
            }

            ejecutarConRetraso(server, 3840L, () -> {
                String comunicadoPvpOff = "§e§l▶ PVP DESACTIVADO:\n" +
                        "§eEl PvP ha sido desactivado. Si tienes una Venganza activa\n" +
                        "§epodrás combatir incluso en zonas seguras hasta que\n" +
                        "§etermine el tiempo.";
                server.getPlayerList().broadcastSystemMessage(Component.literal(comunicadoPvpOff), false);
            });
        }
    }

    public static void sincronizarJugador(ServerPlayer player) {
        ExtremoData data = ExtremoData.getServerState(player.getServer());
        long ahora = System.currentTimeMillis();

        int vidasActuales = data.getVidas(player.getUUID());
        int rotasActuales = data.getVidasRotas(player.getUUID());
        PublicLivesPayload packetEsteJugador = new PublicLivesPayload(player.getUUID(), vidasActuales, rotasActuales);

        for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, packetEsteJugador);
        }
        for (ServerPlayer otro : player.getServer().getPlayerList().getPlayers()) {
            if (otro != player) {
                ServerPlayNetworking.send(player, new PublicLivesPayload(otro.getUUID(), data.getVidas(otro.getUUID()), data.getVidasRotas(otro.getUUID())));
            }
        }

        Map<UUID, Long> objetivos = VenganzaManager.venganzasActivas.get(player.getUUID());
        StringBuilder sbTargets = new StringBuilder();
        int miNivel = data.getPeligrosidadNivel(player.getUUID());

        if (sistemaVenganzaActivo && objetivos != null && !objetivos.isEmpty()) {
            for (Map.Entry<UUID, Long> entry : objetivos.entrySet()) {
                UUID tId = entry.getKey();
                long restanteMs = entry.getValue() - ahora;

                if (restanteMs > 0) {
                    String tName = player.getServer().getProfileCache().get(tId)
                            .map(com.mojang.authlib.GameProfile::getName).orElse("?");

                    if (sbTargets.length() > 0) sbTargets.append("|");
                    sbTargets.append(tName).append(",").append(miNivel).append(",").append(restanteMs);
                }
            }
        }

        StringBuilder sbHunters = new StringBuilder();
        for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
            Map<UUID, Long> pTargets = VenganzaManager.venganzasActivas.get(p.getUUID());
            if (sistemaVenganzaActivo && pTargets != null && pTargets.containsKey(player.getUUID())) {
                long restanteMsActualizado = pTargets.get(player.getUUID()) - ahora;
                if (restanteMsActualizado > 0) {
                    int hunterNivel = data.getPeligrosidadNivel(p.getUUID());

                    if (sbHunters.length() > 0) sbHunters.append("|");
                    sbHunters.append(p.getScoreboardName()).append(",").append(hunterNivel).append(",").append(restanteMsActualizado);
                }
            }
        }

        // Si los sistemas están apagados a nivel de servidor, enviamos valores limpios a los clientes para ocultar la UI instantáneamente
        ServerPlayNetworking.send(player, new SyncExtremoDataPayload(
                data.getVidas(player.getUUID()), data.getVidasRotas(player.getUUID()),
                sistemaPeligrosidadActivo ? data.getPeligrosidadNivel(player.getUUID()) : 0,
                sistemaPeligrosidadActivo ? data.getPeligrosidadExpiracion(player.getUUID()) : 0L,
                sistemaPeligrosidadActivo ? data.getKillsNivel3(player.getUUID()) : 0,
                sistemaVenganzaActivo ? sbTargets.toString() : "",
                sistemaVenganzaActivo ? sbHunters.toString() : ""
        ));
    }
}