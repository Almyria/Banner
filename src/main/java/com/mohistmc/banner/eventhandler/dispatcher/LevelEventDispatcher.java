package com.mohistmc.banner.eventhandler.dispatcher;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_19_R3.CraftServer;
import org.bukkit.craftbukkit.v1_19_R3.scoreboard.CraftScoreboardManager;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;

public class LevelEventDispatcher {

    public static void dispatchLevel() {
        ServerWorldEvents.LOAD.register((server, world) -> {
            if (((CraftServer) Bukkit.getServer()).scoreboardManager == null) {
                ((CraftServer) Bukkit.getServer()).scoreboardManager = new CraftScoreboardManager(server, world.getScoreboard());
            }
            if (world.bridge$generator() != null) {
                world.getWorld().getPopulators().addAll(
                        world.bridge$generator().getDefaultPopulators(
                                world.getWorld()));
            }
            Bukkit.getPluginManager().callEvent(new WorldInitEvent(world.getWorld()));
            Bukkit.getPluginManager().callEvent(new WorldLoadEvent(world.getWorld()));
        });
    }
}