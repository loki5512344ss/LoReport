package dev.loki.lorep.config;

import dev.loki.lorep.LorepPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ConfigManager {
    
    private final LorepPlugin plugin;
    private FileConfiguration config;
    private FileConfiguration messages;
    
    public ConfigManager(LorepPlugin plugin) {
        this.plugin = plugin;
        reload();
    }
    
    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        loadMessages();
    }
    
    private void loadMessages() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.messages = YamlConfiguration.loadConfiguration(messagesFile);
    }
    
    public String getWebhookUrl() {
        return config.getString("webhook-url", "");
    }
    
    public String getDatabaseType() {
        return config.getString("database.type", "sqlite");
    }
    
    public String getSqliteFile() {
        return config.getString("database.sqlite.file", "reports.db");
    }
    
    public String getPostgresHost() {
        return config.getString("database.postgresql.host", "localhost");
    }
    
    public int getPostgresPort() {
        return config.getInt("database.postgresql.port", 5432);
    }
    
    public String getPostgresDatabase() {
        return config.getString("database.postgresql.database", "lorep");
    }
    
    public String getPostgresUsername() {
        return config.getString("database.postgresql.username", "lorep");
    }
    
    /** PG password: empty {@code password} falls back to one line of {@code password-file} (network convention). */
    public String getPostgresPassword() {
        String direct = config.getString("database.postgresql.password", "password");
        if (direct != null && !direct.isEmpty()) {
            return direct;
        }
        String path = config.getString("database.postgresql.password-file", "");
        if (path.isEmpty()) {
            return "";
        }
        try {
            List<String> lines = Files.readAllLines(new File(path).toPath(), StandardCharsets.UTF_8);
            return lines.isEmpty() ? "" : lines.get(0).trim();
        } catch (Exception e) {
            plugin.getLogger().warning("Cannot read password-file " + path + ": " + e.getMessage());
            return "";
        }
    }
    
    public int getPostgresPoolSize() {
        return config.getInt("database.postgresql.pool-size", 10);
    }
    
    public String getServerName() {
        return config.getString("server-name", "unknown");
    }
    
    public boolean isAnticheatEnabled() {
        return config.getBoolean("anticheat.enabled", true);
    }
    
    public int getAnticheatLookbackHours() {
        return config.getInt("anticheat.lookback-hours", 72);
    }
    
    public int getAnticheatSummaryMaxChecks() {
        return config.getInt("anticheat.summary-max-checks", 5);
    }
    
    public int getAnticheatSummaryMaxFlags() {
        return config.getInt("anticheat.summary-max-flags", 50);
    }
    
    public String getMessage(String key) {
        String message = messages.getString(key);
        if (message == null) {
            message = config.getString("messages." + key);
        }
        if (message == null) {
            message = "&cMessage not found: " + key;
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }
    
    public GuiItemConfig getGuiItem(String path) {
        ConfigurationSection section = config.getConfigurationSection("gui.items." + path);
        if (section == null) {
            return null;
        }

        Material material = Material.getMaterial(section.getString("material", "STONE").toUpperCase());
        if (material == null) {
            material = Material.STONE;
        }

        int customModelData = section.contains("custom-model-data") ? section.getInt("custom-model-data") : GuiItemConfig.NO_CUSTOM_MODEL;

        String displayName = section.contains("name") ? ChatColor.translateAlternateColorCodes('&', section.getString("name")) : null;

        List<String> lore = section.contains("lore")
            ? section.getStringList("lore").stream()
                .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                .collect(Collectors.toList())
            : Collections.emptyList();

        List<Integer> slots = section.contains("slots") ? section.getIntegerList("slots") : Collections.emptyList();

        boolean glowing = section.getBoolean("glowing", false);

        return new GuiItemConfig(material, customModelData, displayName, lore, slots, glowing);
    }

    public List<Integer> getItemSlots(String path) {
        GuiItemConfig config = getGuiItem(path);
        if (config != null && !config.slots().isEmpty()) {
            return config.slots();
        }
        return Collections.emptyList();
    }

    public String getMessage(String key, String... replacements) {
        String message = getMessage(key);
        if (replacements == null || replacements.length == 0) {
            return message;
        }
        for (int i = 0; i < replacements.length - 1; i += 2) {
            if (replacements[i] != null && replacements[i + 1] != null) {
                message = message.replace(replacements[i], replacements[i + 1]);
            }
        }
        return message;
    }
}
