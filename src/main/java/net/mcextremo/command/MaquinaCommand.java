package net.mcextremo.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.ItemStack;
import net.mcextremo.Extr3mo;
import net.mcextremo.entity.MaquinaEntity;
import java.util.Optional;
import java.util.stream.StreamSupport;

public class MaquinaCommand {

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_MAQUINAS = (context, builder) -> {
        ServerLevel level = context.getSource().getLevel();
        return SharedSuggestionProvider.suggest(
                StreamSupport.stream(level.getAllEntities().spliterator(), false)
                        .filter(e -> e instanceof MaquinaEntity)
                        .map(e -> e.getCustomName())
                        .filter(java.util.Objects::nonNull)
                        .map(Component::getString),
                builder
        );
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(Commands.literal("ex3moon")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("maquina")
                        .then(Commands.literal("create")
                                .then(Commands.argument("nombre", StringArgumentType.word())
                                        .executes(MaquinaCommand::createMaquina)
                                )
                        )
                        .then(Commands.literal("remove")
                                .then(Commands.argument("nombre", StringArgumentType.word())
                                        .suggests(SUGGEST_MAQUINAS)
                                        .executes(MaquinaCommand::removeMaquina)
                                )
                        )
                        .then(Commands.literal("tradeo")
                                .then(Commands.argument("nombre", StringArgumentType.word())
                                        .suggests(SUGGEST_MAQUINAS)
                                        .then(Commands.literal("add")
                                                .then(Commands.argument("itemCompra", ItemArgument.item(registryAccess))
                                                        .then(Commands.argument("cantCompra", IntegerArgumentType.integer(1, 64))
                                                                .then(Commands.argument("itemVenta", ItemArgument.item(registryAccess))
                                                                        .then(Commands.argument("cantVenta", IntegerArgumentType.integer(1, 64))
                                                                                .executes(MaquinaCommand::addTrade)
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                        .then(Commands.literal("remove")
                                                .then(Commands.argument("itemCompra", ItemArgument.item(registryAccess))
                                                        .executes(MaquinaCommand::removeTrade)
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static int createMaquina(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String nombre = StringArgumentType.getString(context, "nombre");
        ServerLevel level = player.serverLevel();

        MaquinaEntity entity = new MaquinaEntity(Extr3mo.MAQUINA_ENTITY, level);
        entity.setCustomName(Component.literal(nombre));
        entity.setCustomNameVisible(false);
        entity.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);

        level.addFreshEntity(entity);
        context.getSource().sendSuccess(() -> Component.literal("§a[!] Máquina '" + nombre + "' creada exitosamente."), true);
        return 1;
    }

    private static int removeMaquina(CommandContext<CommandSourceStack> context) {
        String nombre = StringArgumentType.getString(context, "nombre");
        ServerLevel level = context.getSource().getLevel();
        final boolean[] found = {false};

        level.getAllEntities().forEach(e -> {
            if (e instanceof MaquinaEntity maquina && maquina.hasCustomName() && maquina.getCustomName().getString().equalsIgnoreCase(nombre)) {
                maquina.discard();
                found[0] = true;
            }
        });

        if (found[0]) {
            context.getSource().sendSuccess(() -> Component.literal("§c[!] Máquina '" + nombre + "' eliminada del mapa."), true);
        } else {
            context.getSource().sendFailure(Component.literal("§e[?] No se encontró ninguna máquina con el nombre: " + nombre));
        }
        return 1;
    }

    private static int addTrade(CommandContext<CommandSourceStack> context) {
        String nombre = StringArgumentType.getString(context, "nombre");
        ItemInput itemCompra = ItemArgument.getItem(context, "itemCompra");
        int cantCompra = IntegerArgumentType.getInteger(context, "cantCompra");
        ItemInput itemVenta = ItemArgument.getItem(context, "itemVenta");
        int cantVenta = IntegerArgumentType.getInteger(context, "cantVenta");

        ServerLevel level = context.getSource().getLevel();
        final boolean[] found = {false};
        final boolean[] isFull = {false};

        level.getAllEntities().forEach(e -> {
            if (e instanceof MaquinaEntity maquina && maquina.hasCustomName() && maquina.getCustomName().getString().equalsIgnoreCase(nombre)) {
                found[0] = true;

                // VALIDACIÓN: Límite estricto de 6 ofertas debido al espacio físico de la vitrina
                if (maquina.getCustomOffers().size() >= 6) {
                    isFull[0] = true;
                    return;
                }

                MerchantOffer offer = new MerchantOffer(
                        new ItemCost(itemCompra.getItem(), cantCompra),
                        Optional.empty(),
                        new ItemStack(itemVenta.getItem(), cantVenta),
                        0, 999, 0, 0.0F, 0
                );
                maquina.getCustomOffers().add(offer);
                maquina.updateClientSlots(); // Envía los nuevos ítems al renderizador del cliente de forma inmediata
            }
        });

        if (isFull[0]) {
            context.getSource().sendFailure(Component.literal("§c[!] La máquina '" + nombre + "' ya tiene el límite máximo de 6 tradeos (vitrina llena)."));
        } else if (found[0]) {
            context.getSource().sendSuccess(() -> Component.literal("§a[!] Tradeo agregado exitosamente a la máquina: " + nombre), true);
        } else {
            context.getSource().sendFailure(Component.literal("§e[?] No se encontró la máquina: " + nombre));
        }
        return 1;
    }

    private static int removeTrade(CommandContext<CommandSourceStack> context) {
        String nombre = StringArgumentType.getString(context, "nombre");
        ItemInput itemCompra = ItemArgument.getItem(context, "itemCompra");
        ServerLevel level = context.getSource().getLevel();
        final boolean[] found = {false};

        level.getAllEntities().forEach(e -> {
            if (e instanceof MaquinaEntity maquina && maquina.hasCustomName() && maquina.getCustomName().getString().equalsIgnoreCase(nombre)) {
                boolean removed = maquina.getCustomOffers().removeIf(offer -> offer.getItemCostA().item() == itemCompra.getItem());
                if (removed) {
                    found[0] = true;
                    maquina.updateClientSlots(); // Limpia el slot correspondiente en los vidrios del cliente
                }
            }
        });

        if (found[0]) {
            context.getSource().sendSuccess(() -> Component.literal("§c[!] Tradeo retirado de la máquina: " + nombre), true);
        } else {
            context.getSource().sendFailure(Component.literal("§e[?] No se encontró el tradeo o la máquina especificada."));
        }
        return 1;
    }
}