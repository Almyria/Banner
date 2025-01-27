package com.mohistmc.banner.eventhandler.dispatcher;

import java.util.ArrayList;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.PositionImpl;
import net.minecraft.world.level.dimension.LevelStem;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_20_R1.event.CraftEventFactory;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class EntityEventDispatcher {

    // TODO: not triggered
    public static void dispatchEntityEvent() {
        ServerEntityWorldChangeEvents.AFTER_ENTITY_CHANGE_WORLD.register((originalEntity, newEntity, origin, destination) -> {
            var pos = originalEntity.getOnPos();
            if (destination.getTypeKey() == LevelStem.NETHER) {
                originalEntity.callPortalEvent(originalEntity, destination, new PositionImpl(pos.getX(), pos.getY(), pos.getZ()),
                        PlayerTeleportEvent.TeleportCause.NETHER_PORTAL,
                        16, 16);
            }else if (destination.getTypeKey() == LevelStem.END) {
                if (Bukkit.getAllowEnd()) {
                    originalEntity.callPortalEvent(originalEntity, destination, new PositionImpl(pos.getX(), pos.getY(), pos.getZ()),
                            PlayerTeleportEvent.TeleportCause.END_PORTAL,
                            128, 16);
                }
            }else {
                originalEntity.callPortalEvent(originalEntity, destination, new PositionImpl(pos.getX(), pos.getY(), pos.getZ()),
                        PlayerTeleportEvent.TeleportCause.UNKNOWN,
                        0, 0);
            }
        });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            // CraftBukkit start
            PlayerChangedWorldEvent changeEvent = new PlayerChangedWorldEvent(player.getBukkitEntity(), origin.getWorld());
            player.level().getCraftServer().getPluginManager().callEvent(changeEvent);
            // CraftBukkit end
            var pos = player.getOnPos();
            if (destination.getTypeKey() == LevelStem.NETHER) {
                player.callPortalEvent(player, destination, new PositionImpl(pos.getX(), pos.getY(), pos.getZ()),
                        PlayerTeleportEvent.TeleportCause.NETHER_PORTAL,
                        16, 16);
            }else if (destination.getTypeKey() == LevelStem.END) {
                if (Bukkit.getAllowEnd()) {
                    player.callPortalEvent(player, destination, new PositionImpl(pos.getX(), pos.getY(), pos.getZ()),
                            PlayerTeleportEvent.TeleportCause.END_PORTAL,
                            128, 16);
                }
            }else {
                player.callPortalEvent(player, destination, new PositionImpl(pos.getX(), pos.getY(), pos.getZ()),
                        PlayerTeleportEvent.TeleportCause.UNKNOWN,
                        0, 0);
            }
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            CraftEventFactory.callEntityDeathEvent(entity, new ArrayList<>()); // TODO add drops
        });
    }
}
