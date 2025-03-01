package com.mohistmc.banner.bukkit;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.item.SignItem;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.bukkit.TreeType;
import org.bukkit.plugin.Plugin;

public class BukkitFieldHooks {

    private static final VarHandle H_EVENT_FIRED;
    private static final VarHandle currentTick;
    private static final VarHandle treeType;
    private static final VarHandle pluginTicket;
    private static final VarHandle pluginTicketType;
    private static final VarHandle openSign;
    private static final VarHandle looting_mod;

    static {
        try {
            var field = DispenserBlock.class.getDeclaredField("eventFired");
            H_EVENT_FIRED = MethodHandles.lookup().unreflectVarHandle(field);
            var currentTickField = MinecraftServer.class.getDeclaredField("currentTick");
            currentTick = MethodHandles.lookup().unreflectVarHandle(currentTickField);
            var treeTypeField = SaplingBlock.class.getDeclaredField("treeType");
            treeType = MethodHandles.lookup().unreflectVarHandle(treeTypeField);
            var pluginTicketField = TicketType.class.getDeclaredField("PLUGIN");
            pluginTicket = MethodHandles.lookup().unreflectVarHandle(pluginTicketField);
            var pluginTicketTypeField = TicketType.class.getDeclaredField("PLUGIN_TICKET");
            pluginTicketType = MethodHandles.lookup().unreflectVarHandle(pluginTicketTypeField);
            var openSignField = SignItem.class.getDeclaredField("openSign");
            openSign = MethodHandles.lookup().unreflectVarHandle(openSignField);
            var looting_modField = LootContextParams.class.getDeclaredField("LOOTING_MOD");
            looting_mod = MethodHandles.lookup().unreflectVarHandle(looting_modField);
        } catch (Throwable t) {
            throw new ExceptionInInitializerError(t);
        }
    }

    public static int currentTick() {
        return (int) currentTick.get();
    }

    public static void setCurrentTick(int tick) {
        currentTick.set(tick);
    }

    public static boolean isEventFired() {
        return (boolean) H_EVENT_FIRED.get();
    }

    public static void setEventFired(boolean b) {
        H_EVENT_FIRED.set(b);
    }

    public static TreeType treeType() {
        return (TreeType) treeType.get();
    }

    public static void setTreeType(TreeType newType){
        treeType.set(newType);
    }

    public static TicketType<Unit> pluginTicket() {
        return (TicketType<Unit>) pluginTicket.get();
    }

    public static TicketType<Plugin> pluginTicketType() {
        return (TicketType<Plugin>) pluginTicketType.get();
    }

    public static BlockPos openSign() {
        return (BlockPos) openSign.get();
    }

    public static void setOpenSign(BlockPos newSign) {
        openSign.set(newSign);
    }

    public static LootContextParam<Integer> looting_mod() {
        return (LootContextParam<Integer>) looting_mod.get();
    }

    public static void setLootingMod(LootContextParam<Integer> new_looting_mod) {
        looting_mod.set(new_looting_mod);
    }
}
