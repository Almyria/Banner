package com.mohistmc.banner.eventhandler.dispatcher;

import com.mohistmc.banner.bukkit.BukkitSnapshotCaptures;
import com.mojang.datafixers.util.Pair;
import io.izzel.arclight.mixin.DecorationOps;
import java.util.Arrays;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

public class PlayerEventDispatcher {

    public static void dispatcherPlayer() {
        EntitySleepEvents.START_SLEEPING.register((entity, sleepingPos) -> {
            if (!entity.level().dimensionType().bedWorks()) {
                explodeBed(entity.getBlockStateOn(), entity.level(), entity.getOnPos());
            }
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (hitResult == null) return InteractionResult.PASS;
            if (player instanceof ServerPlayer serverPlayer) {
                PlayerInteractEntityEvent event;
                Vec3 vec3 = hitResult.getLocation();
                if (vec3 != null) {
                    event = new PlayerInteractAtEntityEvent((Player) serverPlayer.getBukkitEntity(), entity.getBukkitEntity(),
                            new org.bukkit.util.Vector(vec3.x, vec3.y, vec3.z), (hand == InteractionHand.OFF_HAND) ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND);
                } else {
                    event = new PlayerInteractEntityEvent((Player) serverPlayer.getBukkitEntity(), entity.getBukkitEntity(), (hand == InteractionHand.OFF_HAND) ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND);
                }
                ItemStack itemInHand = serverPlayer.getItemInHand(hand);
                boolean triggerLeashUpdate = itemInHand != null && itemInHand.getItem() == Items.LEAD && entity instanceof Mob;
                Item origItem = serverPlayer.getInventory().getSelected() == null ? null : serverPlayer.getInventory().getSelected().getItem();
                Bukkit.getPluginManager().callEvent(event);

                // Fish bucket - SPIGOT-4048
                if ((entity instanceof Bucketable && entity instanceof LivingEntity && origItem != null && origItem.asItem() == Items.WATER_BUCKET) && (event.isCancelled() || serverPlayer.getInventory().getSelected() == null || player.getInventory().getSelected().getItem() != origItem)) {
                    serverPlayer.connection.send(new ClientboundAddEntityPacket(entity));
                    player.containerMenu.sendAllDataToRemote();
                }

                if (triggerLeashUpdate && (event.isCancelled() || player.getInventory().getSelected() == null || player.getInventory().getSelected().getItem() != origItem)) {
                    // Refresh the current leash state
                    serverPlayer.connection.send(new ClientboundSetEntityLinkPacket(entity, ((Mob) entity).getLeashHolder()));
                }

                if (event.isCancelled() || serverPlayer.getInventory().getSelected() == null || serverPlayer.getInventory().getSelected().getItem() != origItem) {
                    // Refresh the current entity metadata
                    entity.getEntityData().refresh(serverPlayer);
                    if (entity instanceof Allay) {
                        serverPlayer.connection.send(new ClientboundSetEquipmentPacket(entity.getId(), Arrays.stream(net.minecraft.world.entity.EquipmentSlot.values()).map((slot) -> Pair.of(slot, ((LivingEntity) entity).getItemBySlot(slot).copy())).collect(Collectors.toList())));
                        serverPlayer.containerMenu.sendAllDataToRemote();
                    }
                }

                if (event.isCancelled()) {
                    return InteractionResult.PASS;
                }
                if (!itemInHand.isEmpty() && itemInHand.getCount() <= -1) {
                    serverPlayer.containerMenu.sendAllDataToRemote();
                }
            }

            return InteractionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            BukkitSnapshotCaptures.capturePlaceEventHand(hand);
            BukkitSnapshotCaptures.getPlaceEventHand(InteractionHand.MAIN_HAND);
            return InteractionResult.PASS;
        });
    }

    // CraftBukkit start
    private static void explodeBed(BlockState iblockdata, Level world, BlockPos blockposition) {
        {
            {
                world.removeBlock(blockposition, false);
                BlockPos blockposition1 = blockposition.relative((Direction) (iblockdata.getValue(BedBlock.FACING)).getOpposite());

                if (world.getBlockState(blockposition1).getBlock() instanceof BedBlock) {
                    world.removeBlock(blockposition1, false);
                }

                Vec3 vec3d = blockposition.getCenter();

                world.explode((Entity) null, world.damageSources().badRespawnPointExplosion(vec3d), (ExplosionDamageCalculator) null, vec3d, 5.0F, true, Level.ExplosionInteraction.BLOCK);
            }
        }
    }
    // CraftBukkit end

}
