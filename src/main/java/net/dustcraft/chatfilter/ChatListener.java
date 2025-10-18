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

    private final long cooldownMs;

    public ChatListener(DustCraftChatFilter plugin, FileConfiguration config, DiscordLogger discordLogger) {
        this.plugin = plugin;
        this.config = config;
        this.discordLogger = discordLogger;
        this.cooldownMs = config.getLong("filter.cooldown-seconds", 5) * 1000L;
    }

    public void reload(FileConfiguration config) {
        this.config = config;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // Bypass check
        if (player.hasPermission("dustcraftchatfilter.bypass")) return;

        String message = event.getMessage().toLowerCase(Locale.ROOT);
        UUID uuid = player.getUniqueId();

        // Cooldown to prevent repeated triggers
        if (System.currentTimeMillis() - lastViolation.getOrDefault(uuid, 0L) < cooldownMs) return;

        // Check for banned words
        List<String> bannedWords = config.getStringList("filter.banned-words");
        for (String word : bannedWords) {
            if (message.contains(word.toLowerCase(Locale.ROOT))) {
                handleInfraction(player, "Word: " + word, "profanity");
                event.setCancelled(true);
                return;
            }
        }

        // Unicode check
        if (config.getBoolean("filter.unicode-block.enabled", true) && containsUnicode(message)) {
            handleInfraction(player, "Unicode characters", "unicode");
            event.setCancelled(true);
        }
    }

    private void handleInfraction(Player player, String reason, String type) {
        UUID uuid = player.getUniqueId();
        offenseCount.put(uuid, offenseCount.getOrDefault(uuid, 0) + 1);
        lastViolation.put(uuid, System.currentTimeMillis());

        int count = offenseCount.get(uuid);
        int threshold = config.getInt("filter.thresholds." + type, 3);

        // Log to console
        Bukkit.getLogger().info("[DustCraftChatFilter] " + player.getName() +
                " triggered chat filter (" + reason + ") - offense #" + count);

        // Send Discord log
        if (config.getBoolean("discord-webhook.chat-violations.enabled", true)) {
            discordLogger.logChatViolation(player, reason, count);
        }

        // Run LiteBans command if threshold met
        if (count >= threshold) {
            String action = config.getString("filter.actions." + type, "warn");
            String cmd = "/" + action + " " + player.getName() + " " +
                    config.getString("filter.duration." + type, "14d") +
                    " Chat violation: " + reason;
            Bukkit.getScheduler().runTask(plugin, () ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
            offenseCount.put(uuid, 0); // reset after punishment
        }
    }

    private boolean containsUnicode(String message) {
        for (char c : message.toCharArray()) {
            if (c > 127 || Character.isISOControl(c)) return true;
        }
        return false;
    }
}
