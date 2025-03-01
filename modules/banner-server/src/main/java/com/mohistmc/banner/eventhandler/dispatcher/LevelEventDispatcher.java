package com.mohistmc.banner.eventhandler.dispatcher;

import java.util.Locale;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.server.level.ServerLevel;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_20_R1.CraftServer;

public class LevelEventDispatcher {

    public static void dispatchLevel() {
        ServerWorldEvents.UNLOAD.register((server, world) -> {
            removeWorld(world);
        });
    }

    public static void removeWorld(ServerLevel world) {
        if (world == null) {
            return;
        }
        ((CraftServer) Bukkit.getServer()).getWorlds().remove(world.getWorld().getName().toLowerCase(Locale.ROOT));// Banner - use Root instead of English
    }
}
