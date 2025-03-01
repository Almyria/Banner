package com.mohistmc.banner.mixin.world.item;

import com.llamalad7.mixinextras.sugar.Local;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import org.bukkit.craftbukkit.v1_20_R1.event.CraftEventFactory;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_20_R1.util.DummyGeneratorAccess;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BucketItem.class)
public abstract class MixinBucketItem extends Item {

    @Shadow public abstract boolean emptyContents(@Nullable Player player, Level level, BlockPos pos, @Nullable BlockHitResult result);

    @Shadow @Final public Fluid content;

    public MixinBucketItem(Properties properties) {
        super(properties);
    }

    @Unique
    private AtomicReference<PlayerBucketFillEvent> banner$bucketFillEvent = new AtomicReference<>();

    @Inject(method = "use",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/BucketPickup;pickupBlock(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/item/ItemStack;"),
            cancellable = true)
    private void banner$use(Level level, Player player, InteractionHand usedHand,
                            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir,
                            @Local ItemStack itemStack, @Local BlockHitResult blockHitResult, @Local(ordinal = 0) BlockPos blockPos,
                            @Local BlockState blockState, @Local BucketPickup bucketPickup) {
        // CraftBukkit start
        ItemStack dummyFluid = bucketPickup.pickupBlock(DummyGeneratorAccess.INSTANCE, blockPos, blockState);
        if (dummyFluid.isEmpty()) cir.setReturnValue(InteractionResultHolder.fail(itemStack)); // Don't fire event if the bucket won't be filled.);
        banner$bucketFillEvent.set(CraftEventFactory.callPlayerBucketFillEvent((ServerLevel) level, player, blockPos, blockPos,
                blockHitResult.getDirection(), itemStack, dummyFluid.getItem(), usedHand));

        if (banner$bucketFillEvent.get().isCancelled()) {
            ((ServerPlayer) player).connection.send(new ClientboundBlockUpdatePacket(level, blockPos)); // SPIGOT-5163 (see PlayerInteractManager)
            ((ServerPlayer) player).getBukkitEntity().updateInventory(); // SPIGOT-4541
            cir.setReturnValue(InteractionResultHolder.fail(itemStack));
        }
    }

    @Redirect(method = "use",
            at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemUtils;createFilledResult(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack banner$filledResult(ItemStack emptyStack, Player player, ItemStack filledStack) {
        return ItemUtils.createFilledResult(emptyStack, player, CraftItemStack.asNMSCopy(banner$bucketFillEvent.get().getItemStack())); // CraftBukkit
    }

    @Inject(method = "emptyContents", at = @At("HEAD"),
            cancellable = true)
    private void banner$bucketFillEvent(Player entityhuman, Level world, BlockPos blockposition, BlockHitResult movingobjectpositionblock, CallbackInfoReturnable<Boolean> cir) {
        // CraftBukkit start
        if (this.content instanceof FlowingFluid && movingobjectpositionblock != null) {
            BlockState iblockdata = world.getBlockState(blockposition);
            Block block = iblockdata.getBlock();
            boolean flag = iblockdata.canBeReplaced(this.content);
            boolean flag1 = iblockdata.isAir() || flag || block instanceof LiquidBlockContainer && ((LiquidBlockContainer) block).canPlaceLiquid(world, blockposition, iblockdata, this.content);

            // CraftBukkit start
            var container = entityhuman.getItemInHand(entityhuman.getUsedItemHand());
            if (flag1 && entityhuman != null && container != null) {
                PlayerBucketEmptyEvent event = CraftEventFactory.callPlayerBucketEmptyEvent((ServerLevel) world, entityhuman, blockposition, movingobjectpositionblock.getBlockPos(), movingobjectpositionblock.getDirection(), container, entityhuman.getUsedItemHand());
                if (event.isCancelled()) {
                    ((ServerPlayer) entityhuman).connection.send(new ClientboundBlockUpdatePacket(world, blockposition));
                    (((ServerPlayer) entityhuman)).getBukkitEntity().updateInventory();
                    cir.setReturnValue(false);
                    return;
                }
            }
        }
    }
}
