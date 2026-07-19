package net.mcextremo;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.mcextremo.item.BolsaItem; // Añadido
import java.util.ArrayList;
import java.util.List;

public class BolsaMenu extends AbstractContainerMenu {
    private final Container container;
    private final ItemStack bagStack;

    public BolsaMenu(int id, Inventory playerInv, Container container, ItemStack bagStack) {
        super(MenuType.GENERIC_9x3, id);
        this.container = container;
        this.bagStack = bagStack;
        container.startOpen(playerInv.player);

        // CORREGIDO: Sobrescribimos mayPlace para impedir clicks manuales con mochilas
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(container, col + row * 9, 8 + col * 18, 18 + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return !(stack.getItem() instanceof BolsaItem);
                    }
                });
            }
        }

        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getMainHandItem() == bagStack || player.getOffhandItem() == bagStack;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < this.slots.size()) {
            Slot slot = this.slots.get(slotId);
            if (slot.hasItem() && slot.getItem() == bagStack) {
                return;
            }
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);

        if (!player.level().isClientSide()) {
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < container.getContainerSize(); i++) {
                items.add(container.getItem(i).copy());
            }
            bagStack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack itemStack2 = slot.getItem();
            itemStack = itemStack2.copy();

            if (index < 27) {
                if (!this.moveItemStackTo(itemStack2, 27, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // CORREGIDO: Bloquea el Shift+Click si el ítem entrante es otra bolsa
                if (itemStack2.getItem() instanceof BolsaItem) {
                    return ItemStack.EMPTY;
                }
                if (!this.moveItemStackTo(itemStack2, 0, 27, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (itemStack2.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return itemStack;
    }
}