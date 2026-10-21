package dev.loki.lorep.config;

import dev.loki.lorep.LorepPlugin;
import dev.loki.lorep.util.TextFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConfigManager {

    /** Root of the GUI item definitions inside config.yml. */
    private static final String GUI_ITEM_ROOT = "gui.items.";

    private final LorepPlugin plugin;
    private final Set<String> warned = new HashSet<>();
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
        this.warned.clear();
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

    /**
     * Message text of the key, the ampersand colour codes are kept.
     * @param key the message key
     * @return the raw text
     */
    private String rawMessage(String key) {
        String message = messages.getString(key);
        if (message == null) {
            message = config.getString("messages." + key);
        }
        if (message == null) {
            message = "&cMessage not found: " + key;
        }
        return message;
    }

    /**
     * Message text with the placeholders applied, still uncoloured.
     * @param key the message key
     * @param replacements the placeholder pairs
     * @return the raw text
     */
    public String getMessageText(String key, String... replacements) {
        String message = rawMessage(key);
        if (replacements == null) {
            return message;
        }
        for (int i = 0; i < replacements.length - 1; i += 2) {
            if (replacements[i] != null && replacements[i + 1] != null) {
                message = message.replace(replacements[i], replacements[i + 1]);
            }
        }
        return message;
    }

    /**
     * Coloured message ready to be sent to a player.
     * @param key the message key
     * @param replacements the placeholder pairs
     * @return the component
     */
    public Component getMessage(String key, String... replacements) {
        return TextFormat.component(getMessageText(key, replacements));
    }

    /**
     * Reads a GUI item definition from the config.
     * @param path the item path inside {@code gui.items}
     * @return the item definition or null when the section is missing
     */
    public GuiItemConfig getGuiItem(String path) {
        return getGuiItem(path, Material.PAPER);
    }

    /**
     * Reads a GUI item definition from the config.
     * @param path the item path inside {@code gui.items}
     * @param defaultMaterial the material used when the config is unusable
     * @return the item definition or null when the section is missing
     */
    public GuiItemConfig getGuiItem(String path, Material defaultMaterial) {
        ConfigurationSection section = config.getConfigurationSection(GUI_ITEM_ROOT + path);
        if (section == null) {
            warnOnce("missing:" + path, "GUI item '" + GUI_ITEM_ROOT + path
                + "' is not configured, the built-in fallback is used");
            return null;
        }

        Material material = parseMaterial(path, section.getString("material"), defaultMaterial);

        int customModelData = section.contains("custom-model-data")
            ? section.getInt("custom-model-data") : GuiItemConfig.NO_CUSTOM_MODEL;

        String displayName = section.contains("name") ? section.getString("name") : null;

        List<String> lore = section.contains("lore")
            ? section.getStringList("lore")
            : Collections.emptyList();

        List<Integer> slots = section.contains("slots") ? section.getIntegerList("slots") : Collections.emptyList();

        boolean glowing = section.getBoolean("glowing", false);

        return new GuiItemConfig(material, customModelData, displayName, lore, slots, glowing);
    }

    /**
     * Resolves the material of a GUI item.
     *
     * <p>An unknown material never falls back to a plain stone block silently:
     * the problem is logged and the caller supplied material is used
     * instead.</p>
     * @param path the item path inside {@code gui.items}
     * @param raw the configured material name
     * @param defaultMaterial the material used when the config is unusable
     * @return the resolved material
     */
    private Material parseMaterial(String path, String raw, Material defaultMaterial) {
        Material material = resolveMaterial(raw);
        if (material == null) {
            warnOnce("bad-material:" + path + ":" + raw, "Unknown material '" + raw + "' for GUI item '"
                + GUI_ITEM_ROOT + path + "', " + defaultMaterial + " is used instead");
            return defaultMaterial;
        }
        return material;
    }

    /**
     * Resolves a material name of the config.
     *
     * <p>Both the plain ({@code PLAYER_HEAD}) and the namespaced
     * ({@code minecraft:player_head}) spellings are accepted, the lookup is
     * case insensitive. An unknown name never resolves to a plain stone
     * block: the caller decides what to use instead.</p>
     * @param raw the configured material name
     * @return the material or null when the name is empty or unknown
     */
    static Material resolveMaterial(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Material.matchMaterial(raw.trim());
    }

    /** Logs a configuration problem once per session. */
    private void warnOnce(String key, String message) {
        if (warned.add(key)) {
            plugin.getLogger().warning(message);
        }
    }
}