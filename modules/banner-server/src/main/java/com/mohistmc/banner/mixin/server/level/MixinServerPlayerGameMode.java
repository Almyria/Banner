package com.mohistmc.banner.mixin.server.level;

import com.mohistmc.banner.bukkit.BukkitSnapshotCaptures;
import com.mohistmc.banner.eventhandler.dispatcher.BlockEventDispatcher;
import com.mohistmc.banner.injection.server.level.InjectionServerPlayerGameMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.craftbukkit.v1_20_R1.block.CraftBlock;
import org.bukkit.craftbukkit.v1_20_R1.event.CraftEventFactory;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(ServerPlayerGameMode.class)
public abstract class MixinServerPlayerGameMode implements InjectionServerPlayerGameMode {

    @Shadow
    @Final
    protected ServerPlayer player;

    @Shadow
    protected ServerLevel level;

    @Shadow
    public abstract boolean isCreative();

    @Shadow
    protected abstract void debugLogging(BlockPos blockPos, boolean bl, int i, String string);

    @Shadow
    private GameType gameModeForPlayer;
    @Shadow
    private int destroyProgressStart;
    @Shadow
    private int gameTicks;
    @Shadow
    private boolean isDestroyingBlock;
    @Shadow
    private BlockPos destroyPos;
    @Shadow
    private int lastSentState;
    @Shadow
    private boolean hasDelayedDestroy;
    @Shadow
    private BlockPos delayedDestroyPos;
    @Shadow
    private int delayedTickStart;

    @Shadow
    @Final
    private static Logger LOGGER;

    @Shadow public abstract void destroyAndAck(BlockPos pos, int i, String string);

    @Inject(method = "changeGameModeForPlayer", cancellable = true, at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayerGameMode;setGameModeForPlayer(Lnet/minecraft/world/level/GameType;Lnet/minecraft/world/level/GameType;)V"))
    private void banner$gameModeEvent(GameType gameType, CallbackInfoReturnable<Boolean> cir) {
        PlayerGameModeChangeEvent event = new PlayerGameModeChangeEvent(((ServerPlayer) player).getBukkitEntity(), GameMode.getByValue(gameType.getId()));
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            cir.setReturnValue(false);
        }
    }

    @Redirect(method = "changeGameModeForPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;broadcastAll(Lnet/minecraft/network/protocol/Packet;)V"))
    private void banner$changeMessage(PlayerList instance, Packet<?> packet) {
        this.player.server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE, this.player), this.player);
    }

    /**
     * @author wdog5
     * @reason functionally replaced
     */
    @Overwrite
    public void handleBlockBreakAction(BlockPos blockposition, ServerboundPlayerActionPacket.Action packetplayinblockdig_enumplayerdigtype, Direction enumdirection, int i, int j) {
        if (this.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(blockposition)) > ServerGamePacketListenerImpl.MAX_INTERACTION_DISTANCE) {
            this.debugLogging(blockposition, false, j, "too far");
        } else if (blockposition.getY() >= i) {
            this.player.connection.send(new ClientboundBlockUpdatePacket(blockposition, this.level.getBlockState(blockposition)));
            this.debugLogging(blockposition, false, j, "too high");
        } else {
            if (packetplayinblockdig_enumplayerdigtype == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
                if (!this.level.mayInteract(this.player, blockposition)) {
                    // CraftBukkit start - fire PlayerInteractEvent
                    CraftEventFactory.callPlayerInteractEvent(this.player, Action.LEFT_CLICK_BLOCK, blockposition, enumdirection, this.player.getInventory().getSelected(), InteractionHand.MAIN_HAND);
                    this.player.connection.send(new ClientboundBlockUpdatePacket(blockposition, this.level.getBlockState(blockposition)));
                    this.debugLogging(blockposition, false, j, "may not interact");
                    // Update any tile entity data for this block
                    BlockEntity tileentity = level.getBlockEntity(blockposition);
                    if (tileentity != null) {
                        this.player.connection.send(tileentity.getUpdatePacket());
                    }
                    // CraftBukkit end
                    return;
                }

                // CraftBukkit start
                PlayerInteractEvent event = CraftEventFactory.callPlayerInteractEvent(this.player, Action.LEFT_CLICK_BLOCK, blockposition, enumdirection, this.player.getInventory().getSelected(), InteractionHand.MAIN_HAND);
                if (event.isCancelled()) {
                    // Let the client know the block still exists
                    this.player.connection.send(new ClientboundBlockUpdatePacket(this.level, blockposition));
                    // Update any tile entity data for this block
                    BlockEntity tileentity = this.level.getBlockEntity(blockposition);
                    if (tileentity != null) {
                        this.player.connection.send(tileentity.getUpdatePacket());
                    }
                    return;
                }
                // CraftBukkit end

                if (this.isCreative()) {
                    this.destroyAndAck(blockposition, j, "creative destroy");
                    return;
                }

                if (this.player.blockActionRestricted(this.level, blockposition, this.gameModeForPlayer)) {
                    this.player.connection.send(new ClientboundBlockUpdatePacket(blockposition, this.level.getBlockState(blockposition)));
                    this.debugLogging(blockposition, false, j, "block action restricted");
                    return;
                }

                this.destroyProgressStart = this.gameTicks;
                float f = 1.0F;

                BlockState iblockdata = this.level.getBlockState(blockposition);
                // CraftBukkit start - Swings at air do *NOT* exist.
                if (event.useInteractedBlock() == Event.Result.DENY) {
                    // If we denied a door from opening, we need to send a correcting update to the client, as it already opened the door.
                    BlockState data = this.level.getBlockState(blockposition);
                    if (data.getBlock() instanceof DoorBlock) {
                        // For some reason *BOTH* the bottom/top part have to be marked updated.
                        boolean bottom = data.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
                        this.player.connection.send(new ClientboundBlockUpdatePacket(this.level, blockposition));
                        this.player.connection.send(new ClientboundBlockUpdatePacket(this.level, bottom ? blockposition.above() : blockposition.below()));
                    } else if (data.getBlock() instanceof TrapDoorBlock) {
                        this.player.connection.send(new ClientboundBlockUpdatePacket(this.level, blockposition));
                    }
                } else if (!iblockdata.isAir()) {
                    iblockdata.attack(this.level, blockposition, this.player);
                    f = iblockdata.getDestroyProgress(this.player, this.player.level(), blockposition);
                }

                if (event.useItemInHand() == Event.Result.DENY) {
                    // If we 'insta destroyed' then the client needs to be informed.
                    if (f > 1.0f) {
                        this.player.connection.send(new ClientboundBlockUpdatePacket(this.level, blockposition));
                    }
                    return;
                }
                org.bukkit.event.block.BlockDamageEvent blockEvent = CraftEventFactory.callBlockDamageEvent(this.player, blockposition, this.player.getInventory().getSelected(), f >= 1.0f);

                if (blockEvent.isCancelled()) {
                    // Let the client know the block still exists
                    this.player.connection.send(new ClientboundBlockUpdatePacket(this.level, blockposition));
                    return;
                }

                if (blockEvent.getInstaBreak()) {
                    f = 2.0f;
                }
                // CraftBukkit end

                if (!iblockdata.isAir() && f >= 1.0F) {
                    this.destroyAndAck(blockposition, j, "insta mine");
                } else {
                    if (this.isDestroyingBlock) {
                        this.player.connection.send(new ClientboundBlockUpdatePacket(this.destroyPos, this.level.getBlockState(this.destroyPos)));
                        this.debugLogging(blockposition, false, j, "abort destroying since another started (client insta mine, server disagreed)");
                    }

                    this.isDestroyingBlock = true;
                    this.destroyPos = blockposition.immutable();
                    int k = (int) (f * 10.0F);

                    this.level.destroyBlockProgress(this.player.getId(), blockposition, k);
                    this.debugLogging(blockposition, true, j, "actual start of destroying");
                    this.lastSentState = k;
                }
            } else if (packetplayinblockdig_enumplayerdigtype == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK) {
                if (blockposition.equals(this.destroyPos)) {
                    int l = this.gameTicks - this.destroyProgressStart;

                    BlockState iblockdata = this.level.getBlockState(blockposition);
                    if (!iblockdata.isAir()) {
                        float f1 = iblockdata.getDestroyProgress(this.player, this.player.level(), blockposition) * (float) (l + 1);

                        if (f1 >= 0.7F) {
                            this.isDestroyingBlock = false;
                            this.level.destroyBlockProgress(this.player.getId(), blockposition, -1);
                            this.destroyAndAck(blockposition, j, "destroyed");
                            return;
                        }

                        if (!this.hasDelayedDestroy) {
                            this.isDestroyingBlock = false;
                            this.hasDelayedDestroy = true;
                            this.delayedDestroyPos = blockposition;
                            this.delayedTickStart = this.destroyProgressStart;
                        }
                    }
                }

                this.debugLogging(blockposition, true, j, "stopped destroying");
            } else if (packetplayinblockdig_enumplayerdigtype == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK) {
                this.isDestroyingBlock = false;
                if (!Objects.equals(this.destroyPos, blockposition)) {
                    LOGGER.debug("Mismatch in destroy block pos: {} {}", this.destroyPos, blockposition); // CraftBukkit - SPIGOT-5457 sent by client when interact event cancelled
                    this.level.destroyBlockProgress(this.player.getId(), this.destroyPos, -1);
                    this.debugLogging(blockposition, true, j, "aborted mismatched destroying");
                }

                this.level.destroyBlockProgress(this.player.getId(), blockposition, -1);
                this.debugLogging(blockposition, true, j, "aborted destroying");

                CraftEventFactory.callBlockDamageAbortEvent(this.player, blockposition, this.player.getInventory().getSelected()); // CraftBukkit
            }

        }
    }

    /**
     * @author
     * @reason
     */
    @Overwrite
    public boolean destroyBlock(BlockPos pos) {
        if (BukkitSnapshotCaptures.popPrimaryBlockBreakEvent() != null) {
            BlockBreakEvent breakEvent = BukkitSnapshotCaptures.popPrimaryBlockBreakEvent().getEvent();
            List<ItemEntity> blockDrops = BukkitSnapshotCaptures.popPrimaryBlockBreakEvent().getBlockDrops();
            org.bukkit.block.BlockState state = BukkitSnapshotCaptures.popPrimaryBlockBreakEvent().getBlockBreakPlayerState();

            if (blockDrops != null && (breakEvent == null || breakEvent.isDropItems())) {
                CraftBlock craftBlock = CraftBlock.at(this.level, pos);
                CraftEventFactory.handleBlockDropItemEvent(craftBlock, state, this.player, blockDrops);
            }
        }

        BlockState blockState = this.level.getBlockState(pos);
        BlockEntity blockEntity;
        Block block;
        boolean bl;
        if (!BlockEventDispatcher.onBlockBreak((ServerPlayerGameMode) (Object) this, this.level, this.player, pos, blockState, !this.player.getMainHandItem().getItem().canAttackBlock(blockState, this.level, pos, this.player))) {
            return false;
        } else {
            blockEntity = this.level.getBlockEntity(pos);
            block = blockState.getBlock();
            if (block instanceof GameMasterBlock && !this.player.canUseGameMasterBlocks()) {
                this.level.sendBlockUpdated(pos, blockState, blockState, 3);
                return false;
            } else if (this.player.blockActionRestricted(this.level, pos, this.gameModeForPlayer)) {
                return false;
            } else {
                block.playerWillDestroy(this.level, pos, blockState, this.player);
                bl = this.level.removeBlock(pos, false);
                if (bl) {
                    block.destroy(this.level, pos, blockState);
                }

                if (this.isCreative()) {
                    return true;
                } else {
                    ItemStack itemStack = this.player.getMainHandItem();
                    ItemStack itemStack2 = itemStack.copy();
                    boolean bl2 = this.player.hasCorrectToolForDrops(blockState);
                    itemStack.mineBlock(this.level, blockState, pos, this.player);
                    if (bl && bl2 && BukkitSnapshotCaptures.getBlockBreakDropItems()) {
                        block.playerDestroy(this.level, this.player, pos, blockState, blockEntity, itemStack2);
                    }
                    return true;
                }
            }
        }
    }

    @Inject(method = {"tick", "destroyAndAck"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayerGameMode;destroyBlock(Lnet/minecraft/core/BlockPos;)Z"))
    public void banner$clearCaptures(CallbackInfo ci) {
        BukkitSnapshotCaptures.clearBlockBreakEventContexts();
    }

    // CraftBukkit start - whole method
    @Unique
    public boolean interactResult = false;
    @Unique
    public boolean firedInteract = false;
    @Unique
    public BlockPos interactPosition;
    @Unique
    public InteractionHand interactHand;
    @Unique
    public ItemStack interactItemStack;

    /**
     * @author wdog4
     * @reason
     */
    @Overwrite
    public InteractionResult useItemOn(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, BlockHitResult hitResult) {
        BlockPos blockPos = hitResult.getBlockPos();
        BlockState blockState = level.getBlockState(blockPos);
        InteractionResult enuminteractionresult = InteractionResult.PASS;
        boolean cancelledBlock = false;
        if (!blockState.getBlock().isEnabled(level.enabledFeatures())) {
            return InteractionResult.FAIL;
        } else if (this.gameModeForPlayer == GameType.SPECTATOR) {
            MenuProvider menuProvider = blockState.getMenuProvider(level, blockPos);
            cancelledBlock = !(menuProvider instanceof MenuProvider);
        }
        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            cancelledBlock = true;
        }

        PlayerInteractEvent event = CraftEventFactory.callPlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, blockPos, hitResult.getDirection(), stack, cancelledBlock, hand, hitResult.getLocation());
        firedInteract = true;
        interactResult = event.useItemInHand() == Event.Result.DENY;
        interactPosition = blockPos.immutable();
        interactHand = hand;
        interactItemStack = stack.copy();

        if (event.useInteractedBlock() == Event.Result.DENY) {
            // If we denied a door from opening, we need to send a correcting update to the client, as it already opened the door.
            if (blockState.getBlock() instanceof DoorBlock) {
                boolean bottom = blockState.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
                player.connection.send(new ClientboundBlockUpdatePacket(level, bottom ? blockPos.above() : blockPos.below()));
            } else if (blockState.getBlock() instanceof CakeBlock) {
                player.getBukkitEntity().sendHealthUpdate(); // SPIGOT-1341 - reset health for cake
            } else if (interactItemStack.getItem() instanceof DoubleHighBlockItem) {
                // send a correcting update to the client, as it already placed the upper half of the bisected item
                player.connection.send(new ClientboundBlockUpdatePacket(level, blockPos.relative(hitResult.getDirection()).above()));

                // send a correcting update to the client for the block above as well, this because of replaceable blocks (such as grass, sea grass etc)
                player.connection.send(new ClientboundBlockUpdatePacket(level, blockPos.above()));
            }
            player.getBukkitEntity().updateInventory(); // SPIGOT-2867
            enuminteractionresult = (event.useItemInHand() != Event.Result.ALLOW) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        } else if (this.gameModeForPlayer == GameType.SPECTATOR) {
            MenuProvider menuProvider = blockState.getMenuProvider(level, blockPos);
            if (menuProvider != null) {
                player.openMenu(menuProvider);
                return InteractionResult.SUCCESS;
            } else {
                return InteractionResult.PASS;
            }
        } else {
            boolean bl = !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty();
            boolean bl2 = player.isSecondaryUseActive() && bl;
            ItemStack itemStack = stack.copy();
            if (!bl2) {
                enuminteractionresult = blockState.use(level, player, hand, hitResult);
                if (enuminteractionresult.consumesAction()) {
                    CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(player, blockPos, itemStack);
                    return enuminteractionresult;
                }
            }

            if (!stack.isEmpty() && enuminteractionresult != InteractionResult.SUCCESS && !interactResult) { // add !interactResult SPIGOT-764
                UseOnContext useOnContext = new UseOnContext(player, hand, hitResult);
                InteractionResult interactionResult2;
                if (this.isCreative()) {
                    int i = stack.getCount();
                    interactionResult2 = stack.useOn(useOnContext);
                    stack.setCount(i);
                } else {
                    interactionResult2 = stack.useOn(useOnContext);
                }

                if (interactionResult2.consumesAction()) {
                    CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(player, blockPos, itemStack);
                }

                return interactionResult2;
            }
        }
        return enuminteractionresult;
    }

    @Override
    public boolean bridge$isFiredInteract() {
        return firedInteract;
    }

    @Override
    public void bridge$setFiredInteract(boolean firedInteract) {
        this.firedInteract = firedInteract;
    }

    @Override
    public boolean bridge$getInteractResult() {
        return interactResult;
    }

    @Override
    public BlockPos bridge$getinteractPosition() {
        return interactPosition;
    }

    @Override
    public InteractionHand bridge$getinteractHand() {
        return interactHand;
    }

    @Override
    public ItemStack bridge$getinteractItemStack() {
        return interactItemStack;
    }
}
