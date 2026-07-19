package net.mcextremo;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback; // Añadido
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.mcextremo.item.BolsaItem;
import net.mcextremo.entity.MaquinaEntity;
import net.mcextremo.command.MaquinaCommand; // Añadido
import net.mcextremo.network.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Extr3mo implements ModInitializer {
	public static final String MOD_ID = "extr3mo";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final Item ELIXIR_DE_LA_REDENCION = Registry.register(
			BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(MOD_ID, "elixir_de_la_redencion"),
			new Item(new Item.Properties().stacksTo(1))
	);

	public static final Item BOLSA = Registry.register(
			BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(MOD_ID, "bolsa"),
			new BolsaItem(new Item.Properties())
	);

	public static final Item MINECOIN = Registry.register(
			BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(MOD_ID, "minecoin"),
			new Item(new Item.Properties())
	);

	// REGISTRO DE LA ENTIDAD MÁQUINA
	public static final EntityType<MaquinaEntity> MAQUINA_ENTITY = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			ResourceLocation.fromNamespaceAndPath(MOD_ID, "maquina"),
			EntityType.Builder.of(MaquinaEntity::new, MobCategory.MISC)
					.sized(2.375F, 4.0F)
					.build("maquina")
	);

	@Override
	public void onInitialize() {
		// REGISTRO AUTOMÁTICO DE LOS COMANDOS DE LA MÁQUINA
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			MaquinaCommand.register(dispatcher, registryAccess);
		});

		PayloadTypeRegistry.playS2C().register(SyncExtremoDataPayload.TYPE, SyncExtremoDataPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SyncPvpPayload.TYPE, SyncPvpPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(TriggerEventPayload.TYPE, TriggerEventPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SyncVidasPayload.TYPE, SyncVidasPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(PlaySoundPayload.TYPE, PlaySoundPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(DeathNotificationPayload.TYPE, DeathNotificationPayload.CODEC);
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}