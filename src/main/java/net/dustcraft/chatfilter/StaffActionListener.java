package net.dustcraft.chatfilter;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Listens for staff moderation commands and reports them to Discord via DiscordLogger.
 * Does not alter the behavior of the commands — purely passive logging.
 */
public class StaffActionListener implements Listener {

    private final DiscordLogger discordLogger;
    private boolean enabled;
    private Set<String> trackedCommands;

    public StaffActionListener(FileConfiguration config, DiscordLogger discordLogger) {
        this.discordLogger = discordLogger;
        loadConfig(config);
    }

    public void reload(FileConfiguration config) {
        loadConfig(config);
    }

    private void loadConfig(FileConfiguration config) {
        enabled = config.getBoolean("discord-webhook.staff-actions.enabled", false);
        trackedCommands = new HashSet<>(config.getStringList("discord-webhook.staff-actions.tracked-commands"));

        if (trackedCommands.isEmpty()) {
            // Provide sane defaults if config list is missing
            trackedCommands = new HashSet<>(Arrays.asList(
                    "warn", "tempwarn", "ban", "tempban", "mute", "tempmute", "kick", "unban", "unmute", "unwarn"
            ));
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();
        String msg = event.getMessage().toLowerCase();

        // Get base command without slash
        String commandLabel = msg.split(" ")[0].replace("/", "");

        if (trackedCommands.contains(commandLabel)) {
            if (!player.hasPermission("dustcraftchatfilter.stafflog")) return;

            // Sanitize long reasons or sensitive inputs
            String safeCommand = sanitize(event.getMessage());

            // Log to Discord
            discordLogger.logStaffAction(player.getName(), safeCommand);

            // Optionally notify staff in-game if desired
            if (event.getPlayer().hasPermission("dustcraftchatfilter.notify")) {
                player.sendMessage(ChatColor.GRAY + "[StaffLog] Logged command: " + ChatColor.YELLOW + safeCommand);
            }
        }
    }

    private String sanitize(String command) {
        // Remove any accidental sensitive IP-like strings
        command = command.replaceAll(
                "(\\b\\d{1,3}(?:\\.\\d{1,3}){3}\\b)", "[REDACTED_IP]"
        );

        // Trim to a reasonable length
        if (command.length() > 300) {
            command = command.substring(0, 300) + "...";
        }
        return command;
    }
}
