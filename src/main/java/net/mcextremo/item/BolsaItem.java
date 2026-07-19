package net.mcextremo.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.mcextremo.BolsaMenu;
import java.util.List;

public class BolsaItem extends Item {
    public BolsaItem(Item.Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide) {
            SimpleContainer container = new SimpleContainer(27);
            ItemContainerContents contents = stack.get(DataComponents.CONTAINER);

            if (contents != null) {
                NonNullList<ItemStack> list = NonNullList.withSize(27, ItemStack.EMPTY);
                contents.copyInto(list);

                for (int i = 0; i < list.size(); i++) {
                    container.setItem(i, list.get(i));
                }
            }

            player.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new BolsaMenu(id, inv, container, stack),
                    Component.literal("Bolsa")
            ));
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipLines, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipLines, tooltipFlag);

        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);

        if (contents != null && !contents.equals(ItemContainerContents.EMPTY)) {
            int displayedCount = 0;
            int remainingSlotsCount = 0;

            for (ItemStack item : contents.nonEmptyItems()) {
                if (displayedCount < 5) {
                    tooltipLines.add(Component.literal("§7- ")
                            .append(item.getHoverName())
                            .append(Component.literal(" x" + item.getCount()).withStyle(ChatFormatting.GRAY)));
                    displayedCount++;
                } else {
                    remainingSlotsCount++;
                }
            }

            if (remainingSlotsCount > 0) {
                tooltipLines.add(Component.translatable("container.shulkerBox.more", remainingSlotsCount)
                        .withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
            }
        } else {
            tooltipLines.add(Component.literal("§8Vacío").withStyle(ChatFormatting.ITALIC));
        }
    }
}