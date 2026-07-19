package net.mcextremo;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

public class MaquinaMerchant implements Merchant {
    private final Player tradingPlayer;
    private MerchantOffers offers = new MerchantOffers();

    public MaquinaMerchant(Player player) {
        this.tradingPlayer = player;
    }

    @Override
    public void setTradingPlayer(@Nullable Player player) {}

    @Nullable
    @Override
    public Player getTradingPlayer() {
        return this.tradingPlayer;
    }

    @Override
    public MerchantOffers getOffers() {
        return this.offers;
    }

    @Override
    public void overrideOffers(MerchantOffers offers) {
        this.offers = offers;
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {}

    @Override
    public void notifyTradeUpdated(ItemStack stack) {} // Cita:[cite: 4]

    @Override
    public int getVillagerXp() { return 0; }

    @Override
    public void overrideXp(int xp) {}

    @Override
    public boolean showProgressBar() { return false; }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.VILLAGER_YES;
    }

    @Override
    public boolean isClientSide() {
        return this.tradingPlayer.level().isClientSide(); // Cita:[cite: 4]
    }
}