package com.mohistmc.banner.command;

import com.mohistmc.tools.HasteUtils;
import java.io.IOException;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.plugin.Plugin;

public class GetPluginListCommand extends BukkitCommand {
    private static String sendToHaste = "";

    public GetPluginListCommand(String name) {
        super(name);
        this.description = "Paste the list of your plugins on hastebin and get the link.";
        this.usageMessage = "/getpluginlist";
        this.setPermission("banner.command.getpluginlist");
    }

    @Override
    public boolean execute(CommandSender sender, String currentAlias, String[] args) {
        if (!testPermission(sender)) return true;
        for (Plugin p : Bukkit.getServer().getPluginManager().getPlugins()) {
            sendToHaste = sendToHaste + "\nName : " + p.getName() + "\nVersion : " + p.getDescription().getVersion() + "\n---------";
        }
        try {
            sender.sendMessage("Link of the list of your plugins : " + HasteUtils.pasteMohist(sendToHaste));
        } catch (IOException e) {
            System.out.println("Unable to paste the list of your plugins.");
        }

        return true;
    }
}
