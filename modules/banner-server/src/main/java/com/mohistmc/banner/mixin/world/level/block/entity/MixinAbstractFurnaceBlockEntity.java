package com.mohistmc.banner.mixin.world.level.block.entity;

import com.llamalad7.mixinextras.sugar.Cancellable;
import com.mohistmc.banner.injection.world.level.block.entity.InjectionAbstractFurnaceBlockEntity;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.RecipeHolder;
import net.minecraft.world.inventory.StackedContentsCompatible;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_20_R1.block.CraftBlock;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_20_R1.util.CraftMagicNumbers;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class MixinAbstractFurnaceBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, RecipeHolder, StackedContentsCompatible, InjectionAbstractFurnaceBlockEntity {


    // @formatter:off
    @Shadow protected NonNullList<ItemStack> items;
    @Shadow  public abstract int getBurnDuration(ItemStack stack);
    @Shadow  public abstract boolean isLit();
    @Shadow @Final public Object2IntOpenHashMap<ResourceLocation> recipesUsed;
    @Shadow public abstract List<Recipe<?>> getRecipesToAwardAndPopExperience(ServerLevel p_154996_, Vec3 p_154997_);

    @Shadow
    private static boolean canBurn(RegistryAccess p_266924_, @Nullable Recipe<?> p_155006_, NonNullList<ItemStack> p_155007_, int p_155008_) {
        return false;
    }
    // @formatter:on

    @Shadow public abstract boolean isEmpty();

    @Unique
    public List<HumanEntity> transaction = new ArrayList<>();
    @Unique
    private int maxStack = MAX_STACK;
    @Unique
    private static AbstractFurnaceBlockEntity banner$captureFurnace;
    @Unique
    private static Player banner$capturePlayer;
    @Unique
    private static ItemStack banner$item;
    @Unique
    private static int banner$captureAmount;

    protected MixinAbstractFurnaceBlockEntity(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
    }

    @Redirect(method = "serverTick",
            at = @At(value = "INVOKE", ordinal = 3,
            target = "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;isLit()Z"))
    private static boolean banner$setBurnTime(AbstractFurnaceBlockEntity furnace, @Cancellable CallbackInfo ci) {
        ItemStack itemStack = furnace.getItem(1);
        CraftItemStack fuel = CraftItemStack.asCraftMirror(itemStack);
        FurnaceBurnEvent furnaceBurnEvent = new FurnaceBurnEvent(CraftBlock.at(furnace.level, furnace.getBlockPos()), fuel, furnace.getBurnDuration(itemStack));
        Bukkit.getPluginManager().callEvent(furnaceBurnEvent);

        if (furnaceBurnEvent.isCancelled()) {
            ci.cancel();
            return false;
        }
        return furnace.isLit() && furnaceBurnEvent.isBurning();
    }

    @Inject(method = "serverTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;burn(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/item/crafting/Recipe;Lnet/minecraft/core/NonNullList;I)Z"))
    private static void banner$smeltEvent(Level level,
                                          BlockPos pos,
                                          BlockState state,
                                          AbstractFurnaceBlockEntity blockEntity, CallbackInfo ci) {
    }

    @Redirect(method = "createExperience", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ExperienceOrb;award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V"))
    private static void banner$expEvent(ServerLevel level, Vec3 vec3, int amount) {
        if (banner$capturePlayer != null && banner$captureAmount != 0) {
            FurnaceExtractEvent event = new FurnaceExtractEvent(((ServerPlayer) banner$capturePlayer).getBukkitEntity(), CraftBlock.at(level, banner$captureFurnace.getBlockPos()), CraftMagicNumbers.getMaterial(banner$item.getItem()), banner$captureAmount, amount);
            Bukkit.getPluginManager().callEvent(event);
            amount = event.getExpToDrop();
        }
        ExperienceOrb.award(level, vec3, amount);
    }

    @Unique
    private static AtomicReference<Level> banner$level = new AtomicReference<>();
    @Unique
    private static AtomicReference<BlockPos> banner$pos = new AtomicReference<>();
    @Unique
    private static AtomicReference<Level> banner$world = new AtomicReference<>();
    @Unique
    private static AtomicReference<BlockPos> banner$blockPos = new AtomicReference<>();

    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void banner$getInfo(Level level, BlockPos pos, BlockState state,
                                       AbstractFurnaceBlockEntity blockEntity, CallbackInfo ci) {
        banner$world.set(level);
        banner$blockPos.set(pos);
    }

    @Redirect(method = "serverTick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;burn(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/item/crafting/Recipe;Lnet/minecraft/core/NonNullList;I)Z"))
    private static boolean banner$burnEvent(RegistryAccess registryAccess, Recipe<?> recipe, NonNullList<ItemStack> nonNullList, int i) {
        return burn(banner$world.get(), banner$blockPos.get(), registryAccess, recipe, nonNullList, i);
    }

    @Unique
    private static boolean burn(Level world, BlockPos blockposition, RegistryAccess iregistrycustom, @Nullable Recipe<?> irecipe, NonNullList<ItemStack> nonnulllist, int i) {
        banner$level.set(world);
        banner$pos.set(blockposition);
        return burn(iregistrycustom, irecipe, nonnulllist, i);
    }

    /**
     * @author wdog5
     * @reason bukkit
     */
    @Overwrite
    private static boolean burn(RegistryAccess iregistrycustom, @Nullable Recipe<?> irecipe, NonNullList<ItemStack> nonnulllist, int i) {
        if (irecipe != null && canBurn(iregistrycustom, irecipe, nonnulllist, i)) {
            ItemStack itemstack = (ItemStack) nonnulllist.get(0);
            ItemStack itemstack1 = irecipe.getResultItem(iregistrycustom);
            ItemStack itemstack2 = (ItemStack) nonnulllist.get(2);

            // CraftBukkit start - fire FurnaceSmeltEvent
            CraftItemStack source = CraftItemStack.asCraftMirror(itemstack);
            org.bukkit.inventory.ItemStack result = CraftItemStack.asBukkitCopy(itemstack1);
            FurnaceSmeltEvent furnaceSmeltEvent;
            if (irecipe.toBukkitRecipe() instanceof CookingRecipe cookingRecipe) {
                furnaceSmeltEvent = new FurnaceSmeltEvent(CraftBlock.at(banner$level.get(), banner$pos.get()), source, result, cookingRecipe); // Paper
            } else {
                furnaceSmeltEvent = new FurnaceSmeltEvent(CraftBlock.at(banner$level.get(), banner$pos.get()), source, result);
            }
            banner$level.get().getCraftServer().getPluginManager().callEvent(furnaceSmeltEvent);

            if (furnaceSmeltEvent.isCancelled()) {
                return false;
            }

            result = furnaceSmeltEvent.getResult();
            itemstack1 = CraftItemStack.asNMSCopy(result);

            if (!itemstack1.isEmpty()) {
                if (itemstack2.isEmpty()) {
                    nonnulllist.set(2, itemstack1.copy());
                } else if (CraftItemStack.asCraftMirror(itemstack2).isSimilar(result)) {
                    itemstack2.grow(itemstack1.getCount());
                } else {
                    return false;
                }
            }

            /*
            if (itemstack2.isEmpty()) {
                nonnulllist.set(2, itemstack1.copy());
            } else if (itemstack2.is(itemstack1.getItem())) {
                itemstack2.grow(1);
            }
            */
            // CraftBukkit end

            if (itemstack.is(Blocks.WET_SPONGE.asItem()) && !((ItemStack) nonnulllist.get(1)).isEmpty() && ((ItemStack) nonnulllist.get(1)).is(Items.BUCKET)) {
                nonnulllist.set(1, new ItemStack(Items.WATER_BUCKET));
            }

            itemstack.shrink(1);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public List<Recipe<?>> getRecipesToAwardAndPopExperience(ServerLevel world, Vec3 vec, BlockPos pos, Player entity, ItemStack itemStack, int amount) {
        try {
            banner$item = itemStack;
            banner$captureAmount = amount;
            banner$captureFurnace = (AbstractFurnaceBlockEntity) (Object) this;
            banner$capturePlayer = entity;
            List<Recipe<?>> list = this.getRecipesToAwardAndPopExperience(world, vec);
            entity.awardRecipes(list);
            this.recipesUsed.clear();
            return list;
        } finally {
            banner$item = null;
            banner$captureAmount = 0;
            banner$captureFurnace = null;
            banner$capturePlayer = null;
        }
    }

    @Override
    public List<ItemStack> getContents() {
        return this.items;
    }

    @Override
    public void onOpen(CraftHumanEntity who) {
        transaction.add(who);
    }

    @Override
    public void onClose(CraftHumanEntity who) {
        transaction.remove(who);
    }

    @Override
    public List<HumanEntity> getViewers() {
        return transaction;
    }

    @Override
    public void setOwner(InventoryHolder owner) {
    }

    @Override
    public int getMaxStackSize() {
        if (maxStack == 0) maxStack = MAX_STACK;
        return maxStack;
    }

    @Override
    public void setMaxStackSize(int size) {
        this.maxStack = size;
    }

    @Override
    public List<Recipe<?>> bridge$dropExp(ServerPlayer entity, ItemStack itemStack, int amount) {
        return getRecipesToAwardAndPopExperience(entity.serverLevel(), entity.position(), this.worldPosition, entity, itemStack, amount);
    }

    @Override
    public Object2IntOpenHashMap<ResourceLocation> getRecipesUsed() {
        return this.recipesUsed;
    }
}
