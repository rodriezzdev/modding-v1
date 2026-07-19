package net.mcextremo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.trading.ItemCost;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Optional;

public class MaquinaConfigLoader {
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "extr3mo_maquina.json"); // Cita:[cite: 3]

    public static MerchantOffers cargarOfertas() {
        MerchantOffers ofertas = new MerchantOffers();
        if (!CONFIG_FILE.exists()) {
            crearConfigPorDefecto();
        }

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element.isJsonArray()) {
                JsonArray array = element.getAsJsonArray();
                for (JsonElement e : array) {
                    if (e.isJsonObject()) {
                        JsonObject obj = e.getAsJsonObject();

                        ItemStack compra1 = parseStack(obj.getAsJsonObject("compra1"));
                        ItemStack venta = parseStack(obj.getAsJsonObject("venta"));
                        int maxUsos = obj.has("maxUsos") ? obj.get("maxUsos").getAsInt() : 999;

                        if (!compra1.isEmpty() && !venta.isEmpty()) {
                            ItemCost cost1 = new ItemCost(compra1.getItem(), compra1.getCount()); // Cita:[cite: 3]
                            Optional<ItemCost> cost2 = Optional.empty();

                            if (obj.has("compra2")) {
                                ItemStack c2 = parseStack(obj.getAsJsonObject("compra2"));
                                if (!c2.isEmpty()) {
                                    cost2 = Optional.of(new ItemCost(c2.getItem(), c2.getCount())); // Cita:[cite: 3]
                                }
                            }

                            MerchantOffer offer = new MerchantOffer(cost1, cost2, venta, 0, maxUsos, 0, 0.0F, 0); // Cita:[cite: 3]
                            ofertas.add(offer);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return ofertas;
    }

    private static ItemStack parseStack(JsonObject obj) {
        if (obj == null || !obj.has("item")) return ItemStack.EMPTY;
        String itemId = obj.get("item").getAsString();
        int count = obj.has("count") ? obj.get("count").getAsInt() : 1;
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        return new ItemStack(item, count);
    }

    private static void crearConfigPorDefecto() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            JsonArray array = new JsonArray();
            JsonObject ejemplo = new JsonObject();

            JsonObject c1 = new JsonObject();
            c1.addProperty("item", "extr3mo:minecoin");
            c1.addProperty("count", 8);

            JsonObject v = new JsonObject();
            v.addProperty("item", "extr3mo:elixir_de_la_redencion");
            v.addProperty("count", 1);

            ejemplo.add("compra1", c1);
            ejemplo.add("venta", v);
            ejemplo.addProperty("maxUsos", 999);

            array.add(ejemplo);
            writer.write(array.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}