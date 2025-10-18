package net.dustcraft.chatfilter;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatListener implements Listener {

    private final DustCraftChatFilter plugin;
    private FileConfiguration config;
    private final DiscordLogger discordLogger;

    private final Map<UUID, Integer> offenseCount = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastViolation = new ConcurrentHashMap<>();
    private long cooldownMs; // reloadable

    public ChatListener(DustCraftChatFilter plugin, FileConfiguration config, DiscordLogger discordLogger) {
        this.plugin = plugin;
        this.config = config;
        this.discordLogger = discordLogger;
        this.cooldownMs = config.getLong("filter.cooldown-seconds", 5) * 1000L;
    }

    public void reload(FileConfiguration config) {
        this.config = config;
        this.cooldownMs = config.getLong("filter.cooldown-seconds", 5) * 1000L;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // bypass
        if (player.hasPermission("dustcraftchatfilter.bypass")) return;

        String original = event.getMessage();
        String lower = original.toLowerCase(Locale.ROOT);
        UUID uuid = player.getUniqueId();

        // cooldown
        if (System.currentTimeMillis() - lastViolation.getOrDefault(uuid, 0L) < cooldownMs) return;

        // profanity
        List<String> bannedWords = config.getStringList("filter.banned-words");
        for (String word : bannedWords) {
            if (lower.contains(word.toLowerCase(Locale.ROOT))) {
                handleInfraction(player, original, "Word: " + word, "profanity", true, false);
                event.setCancelled(true);
                return;
            }
        }

        // unicode
        boolean unicodeEnabled = config.getBoolean("filter.unicode-block.enabled", true);
        if (unicodeEnabled && containsUnicode(original)) {
            handleInfraction(player, original, "Unicode characters", "unicode", false, true);
            event.setCancelled(true);
        }
    }

    private void handleInfraction(Player player, String message, String reason, String type,
                                  boolean profanity, boolean unicode) {

        UUID uuid = player.getUniqueId();
        offenseCount.put(uuid, offenseCount.getOrDefault(uuid, 0) + 1);
        lastViolation.put(uuid, System.currentTimeMillis());
        int count = offenseCount.get(uuid);

        // player feedback
        player.sendMessage(ChatColor.RED + "⚠ Your message was blocked by the DustCraft Chat Filter.");
        player.sendMessage(ChatColor.GRAY + "Please keep chat friendly and appropriate.");

        // console log
        Bukkit.getLogger().warning("[DustCraftChatFilter] Blocked message from " + player.getName() + ": " + message);

        // discord log (use the keys that DiscordLogger expects)
        if (config.getBoolean("discord-webhook.chat.enabled", false)) {
            discordLogger.logFilteredMessage(player.getName(), message, unicode, profanity);
        }

        // threshold → punish
        int threshold = config.getInt("filter.max_offenses_before_warn", 3);
        if (count >= threshold && config.getBoolean("litebans.integration", false)) {
            String warnCommand = config.getString("litebans.warn_command", "")
                    .replace("%player%", player.getName());

            if (warnCommand.isEmpty()) {
                Bukkit.getLogger().warning("[DustCraftChatFilter] litebans.warn_command is missing or empty in config.yml!");
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> 
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), warnCommand)
            );

            offenseCount.put(uuid, 0); // reset after warn
            player.sendMessage(ChatColor.YELLOW + "⚠ You have been automatically warned for repeated inappropriate messages.");
        }
    }

    private boolean containsUnicode(String s) {
        for (char c : s.toCharArray()) {
            if (c > 127 || Character.isISOControl(c)) return true;
        }
        return false;
    }
}
