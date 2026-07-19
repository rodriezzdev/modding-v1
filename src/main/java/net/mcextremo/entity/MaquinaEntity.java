package net.mcextremo.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundEvent;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class MaquinaEntity extends Entity implements GeoEntity, Merchant {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final MerchantOffers offers = new MerchantOffers();
    private boolean firstTick = true;

    private Player tradingPlayer;
    private MerchantOffers activeMerchantOffers = new MerchantOffers();
    private int merchantXp = 0;

    private static final EntityDataAccessor<Integer> LEVER_TICKS = SynchedEntityData.defineId(MaquinaEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<ItemStack> SLOT_1 = SynchedEntityData.defineId(MaquinaEntity.class, EntityDataSerializers.ITEM_STACK);
    public static final EntityDataAccessor<ItemStack> SLOT_2 = SynchedEntityData.defineId(MaquinaEntity.class, EntityDataSerializers.ITEM_STACK);
    public static final EntityDataAccessor<ItemStack> SLOT_3 = SynchedEntityData.defineId(MaquinaEntity.class, EntityDataSerializers.ITEM_STACK);
    public static final EntityDataAccessor<ItemStack> SLOT_4 = SynchedEntityData.defineId(MaquinaEntity.class, EntityDataSerializers.ITEM_STACK);
    public static final EntityDataAccessor<ItemStack> SLOT_5 = SynchedEntityData.defineId(MaquinaEntity.class, EntityDataSerializers.ITEM_STACK);
    public static final EntityDataAccessor<ItemStack> SLOT_6 = SynchedEntityData.defineId(MaquinaEntity.class, EntityDataSerializers.ITEM_STACK);

    public MaquinaEntity(EntityType<? extends Entity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    public MerchantOffers getCustomOffers() { return this.offers; }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(LEVER_TICKS, 0);
        builder.define(SLOT_1, ItemStack.EMPTY);
        builder.define(SLOT_2, ItemStack.EMPTY);
        builder.define(SLOT_3, ItemStack.EMPTY);
        builder.define(SLOT_4, ItemStack.EMPTY);
        builder.define(SLOT_5, ItemStack.EMPTY);
        builder.define(SLOT_6, ItemStack.EMPTY);
    }

    public void updateClientSlots() {
        if (this.level().isClientSide()) return;
        this.entityData.set(SLOT_1, this.offers.size() > 0 ? this.offers.get(0).getResult() : ItemStack.EMPTY);
        this.entityData.set(SLOT_2, this.offers.size() > 1 ? this.offers.get(1).getResult() : ItemStack.EMPTY);
        this.entityData.set(SLOT_3, this.offers.size() > 2 ? this.offers.get(2).getResult() : ItemStack.EMPTY);
        this.entityData.set(SLOT_4, this.offers.size() > 3 ? this.offers.get(3).getResult() : ItemStack.EMPTY);
        this.entityData.set(SLOT_5, this.offers.size() > 4 ? this.offers.get(4).getResult() : ItemStack.EMPTY);
        this.entityData.set(SLOT_6, this.offers.size() > 5 ? this.offers.get(5).getResult() : ItemStack.EMPTY);
    }

    public ItemStack getDisplayItem(int slot) {
        return switch (slot) {
            case 1 -> this.entityData.get(SLOT_1);
            case 2 -> this.entityData.get(SLOT_2);
            case 3 -> this.entityData.get(SLOT_3);
            case 4 -> this.entityData.get(SLOT_4);
            case 5 -> this.entityData.get(SLOT_5);
            case 6 -> this.entityData.get(SLOT_6);
            default -> ItemStack.EMPTY;
        };
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            if (this.firstTick) { this.updateClientSlots(); this.firstTick = false; }
            int ticks = this.entityData.get(LEVER_TICKS);
            if (ticks > 0) this.entityData.set(LEVER_TICKS, ticks - 1);
        }
    }

    public void setTradingPlayer(Player player) { this.tradingPlayer = player; }
    public Player getTradingPlayer() { return this.tradingPlayer; }
    public MerchantOffers getOffers() { return this.activeMerchantOffers; }
    public void overrideOffers(MerchantOffers offers) { this.activeMerchantOffers = offers; }
    public void notifyTrade(MerchantOffer offer) { this.entityData.set(LEVER_TICKS, 20); }
    public void notifyTradeUpdated(ItemStack stack) {}
    public int getVillagerXp() { return this.merchantXp; }
    public void setVillagerXp(int xp) { this.merchantXp = xp; }
    public void overrideXp(int xp) { this.merchantXp = xp; }
    public boolean showProgressBar() { return false; }
    public SoundEvent getNotifyTradeSound() { return SoundEvents.VILLAGER_YES; }
    public boolean isClientSide() { return this.level().isClientSide(); }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!this.level().isClientSide() && hand == InteractionHand.MAIN_HAND) {
            if (player instanceof ServerPlayer serverPlayer) {
                this.setTradingPlayer(serverPlayer);

                MerchantOffers activeOffers = new MerchantOffers();
                activeOffers.addAll(this.offers);
                this.overrideOffers(activeOffers);

                serverPlayer.openMenu(new SimpleMenuProvider((id, playerInv, p) -> new MerchantMenu(id, playerInv, this),
                        this.getCustomName() != null ? this.getCustomName() : Component.literal("Máquina de Tradeos")));

                if (serverPlayer.containerMenu instanceof MerchantMenu) {
                    serverPlayer.connection.send(new ClientboundMerchantOffersPacket(
                            serverPlayer.containerMenu.containerId, this.getOffers(), 1, 0, false, false));
                }
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("maquina_trades")) {
            RegistryOps<net.minecraft.nbt.Tag> ops = RegistryOps.create(NbtOps.INSTANCE, this.level().registryAccess());
            MerchantOffers.CODEC.parse(ops, tag.get("maquina_trades")).result().ifPresent(parsed -> {
                this.offers.clear(); this.offers.addAll(parsed); this.updateClientSlots();
            });
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        RegistryOps<net.minecraft.nbt.Tag> ops = RegistryOps.create(NbtOps.INSTANCE, this.level().registryAccess());
        MerchantOffers.CODEC.encodeStart(ops, this.offers).result().ifPresent(nbt -> tag.put("maquina_trades", nbt));
    }

    @Override
    public boolean isPickable() { return true; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "lever_controller", 2, state -> {
            if (this.entityData.get(LEVER_TICKS) > 0) return state.setAndContinue(RawAnimation.begin().thenPlay("usar"));
            return state.setAndContinue(RawAnimation.begin().thenLoop("idle"));
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return this.cache; }
}