package net.dustcraft.chatfilter;

import org.bukkit.configuration.file.FileConfiguration;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

/**
 * Handles sending logs to Discord webhooks.
 * Supports both chat filter events and staff moderation logs.
 */
public class DiscordLogger {

    private String chatWebhookUrl;
    private String staffWebhookUrl;
    private boolean chatEnabled;
    private boolean staffEnabled;
    private final Logger log;
    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public DiscordLogger(FileConfiguration config, Logger log) {
        this.log = log;
        loadConfig(config);
    }

    public void reload(FileConfiguration config) {
        loadConfig(config);
    }

    private void loadConfig(FileConfiguration config) {
        chatWebhookUrl = config.getString("discord-webhook.chat.url", "");
        staffWebhookUrl = config.getString("discord-webhook.staff-actions.url", "");
        chatEnabled = config.getBoolean("discord-webhook.chat.enabled", false);
        staffEnabled = config.getBoolean("discord-webhook.staff-actions.enabled", false);
    }

    public void shutdown() {
        // No persistent connections are kept — safe to ignore.
    }

    // -------------------------------------------------
    // 🧱 Chat filter logging
    // -------------------------------------------------
    public void logFilteredMessage(String player, String message, boolean unicode, boolean profanity) {
        if (!chatEnabled || chatWebhookUrl.isEmpty()) return;

        String title = "🚫 Chat Filter Triggered";
        String reason = "";

        if (unicode && profanity) {
            reason = "Contains blacklisted word(s) and disallowed Unicode.";
        } else if (unicode) {
            reason = "Contains disallowed Unicode characters.";
        } else if (profanity) {
            reason = "Contains blacklisted word(s).";
        }

        String jsonPayload = createEmbedJson(title,
                "**Player:** " + player + "\n" +
                "**Reason:** " + reason + "\n" +
                "**Message:** " + message + "\n" +
                "**Time:** " + dtf.format(LocalDateTime.now()),
                0xE74C3C // Red
        );

        sendAsync(chatWebhookUrl, jsonPayload);
    }

    public void logWarningIssued(String player, String command) {
        if (!chatEnabled || chatWebhookUrl.isEmpty()) return;

        String jsonPayload = createEmbedJson(
                "⚠️ Automatic Warning Issued",
                "**Player:** " + player + "\n" +
                "**Action:** `" + command + "`\n" +
                "**Time:** " + dtf.format(LocalDateTime.now()),
                0xF1C40F // Yellow
        );

        sendAsync(chatWebhookUrl, jsonPayload);
    }

    // -------------------------------------------------
    // 🧑‍💼 Staff action logging
    // -------------------------------------------------
    public void logStaffAction(String staff, String command) {
        if (!staffEnabled || staffWebhookUrl.isEmpty()) return;

        String jsonPayload = createEmbedJson(
                "🛡️ Staff Action Executed",
                "**Staff:** " + staff + "\n" +
                "**Command:** `" + command + "`\n" +
                "**Time:** " + dtf.format(LocalDateTime.now()),
                0x3498DB // Blue
        );

        sendAsync(staffWebhookUrl, jsonPayload);
    }

    // -------------------------------------------------
    // 🔧 Helper methods
    // -------------------------------------------------
    private String createEmbedJson(String title, String description, int color) {
        // Proper Discord webhook JSON
        return "{"
                + "\"embeds\": [{"
                + "\"title\": \"" + escapeJson(title) + "\","
                + "\"description\": \"" + escapeJson(description) + "\","
                + "\"color\": " + color
                + "}]"
                + "}";
    }

    private void sendAsync(String webhookUrl, String jsonPayload) {
        new Thread(() -> {
            try {
                URL url = new URL(webhookUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = conn.getResponseCode();
                if (responseCode != 204 && responseCode != 200) {
                    log.warning("[DustCraftChatFilter] Discord webhook response code: " + responseCode);
                }

                conn.disconnect();
            } catch (Exception e) {
                log.warning("[DustCraftChatFilter] Failed to send webhook: " + e.getMessage());
            }
        }).start();
    }

    private String escapeJson(String input) {
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }
}
