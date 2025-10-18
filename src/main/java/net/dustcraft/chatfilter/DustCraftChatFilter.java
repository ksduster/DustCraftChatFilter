package net.dustcraft.chatfilter;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

public final class DustCraftChatFilter extends JavaPlugin {

    private static DustCraftChatFilter instance;
    private FileConfiguration config;
    private Logger log;
    private DiscordLogger discordLogger;
    private ChatListener chatListener;
    private StaffActionListener staffActionListener;

    @Override
    public void onEnable() {
        instance = this;
        log = getLogger();

        // Load config and save defaults if missing
        saveDefaultConfig();
        config = getConfig();

        log.info("================================================");
        log.info(" DustCraftChatFilter v" + getDescription().getVersion());
        log.info(" Author: DustCraft Minecraft Community");
        log.info(" Website: https://dustcraft.serveminecraft.net");
        log.info("================================================");

        // Initialize Discord webhook logger
        discordLogger = new DiscordLogger(config, log);

        // Register listeners
        chatListener = new ChatListener(this, config, discordLogger);
        Bukkit.getPluginManager().registerEvents(chatListener, this);

        // Optional: only register staff action logging if enabled
        if (config.getBoolean("discord-webhook.staff-actions.enabled", false)) {
            staffActionListener = new StaffActionListener(config, discordLogger);
            Bukkit.getPluginManager().registerEvents(staffActionListener, this);
        }

        log.info(ChatColor.GREEN + "DustCraftChatFilter has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        if (discordLogger != null) {
            discordLogger.shutdown();
        }
        log.info(ChatColor.RED + "DustCraftChatFilter has been disabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("dccf")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("dustcraftchatfilter.reload")) {
                    sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                    return true;
                }

                reloadConfig();
                config = getConfig();

                if (chatListener != null) chatListener.reload(config);
                if (discordLogger != null) discordLogger.reload(config);
                if (staffActionListener != null) staffActionListener.reload(config);

                sender.sendMessage(ChatColor.GREEN + "DustCraftChatFilter configuration reloaded successfully!");
                log.info(sender.getName() + " reloaded DustCraftChatFilter.");
                return true;
            }

            sender.sendMessage(ChatColor.YELLOW + "Usage: /dccf reload");
            return true;
        }
        return false;
    }

    public static DustCraftChatFilter getInstance() {
        return instance;
    }

    public FileConfiguration getPluginConfig() {
        return config;
    }
}
