package dev.loki.lorep;

import dev.loki.lorep.anticheat.AnticheatService;
import dev.loki.lorep.command.ReportCommand;
import dev.loki.lorep.config.ConfigManager;
import dev.loki.lorep.database.DatabaseManager;
import dev.loki.lorep.database.PostgreSQLDatabaseManager;
import dev.loki.lorep.database.SQLiteDatabaseManager;
import dev.loki.lorep.gui.GuiClickListener;
import dev.loki.lorep.webhook.DiscordWebhook;
import org.bukkit.plugin.java.JavaPlugin;

public final class LorepPlugin extends JavaPlugin {
    
    private static LorepPlugin instance;
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private DiscordWebhook discordWebhook;
    private AnticheatService anticheatService;
    
    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("lorep plugin enabling...");
        
        // Initialize config
        configManager = new ConfigManager(this);
        
        // Initialize database
        initializeDatabase();
        
        // Initialize ServGuard anticheat integration
        anticheatService = new AnticheatService(databaseManager, configManager, getLogger());
        
        // Initialize webhook
        String webhookUrl = configManager.getWebhookUrl();
        if (webhookUrl != null && !webhookUrl.isEmpty()) {
            discordWebhook = new DiscordWebhook(webhookUrl, getLogger());
            getLogger().info("Discord webhook configured");
        } else {
            getLogger().warning("Discord webhook URL not configured");
        }
        
        // Register commands
        ReportCommand reportCommand = new ReportCommand(this);
        getCommand("report").setExecutor(reportCommand);
        getCommand("report").setTabCompleter(reportCommand);
        
        // Register listeners
        getServer().getPluginManager().registerEvents(new GuiClickListener(this), this);
        
        getLogger().info("lorep plugin enabled!");
    }
    
    private void initializeDatabase() {
        String dbType = configManager.getDatabaseType();
        
        if ("postgresql".equalsIgnoreCase(dbType)) {
            databaseManager = new PostgreSQLDatabaseManager(
                configManager.getPostgresHost(),
                configManager.getPostgresPort(),
                configManager.getPostgresDatabase(),
                configManager.getPostgresUsername(),
                configManager.getPostgresPassword(),
                configManager.getPostgresPoolSize(),
                getLogger()
            );
            getLogger().info("Using PostgreSQL database");
        } else {
            databaseManager = new SQLiteDatabaseManager(
                getDataFolder(),
                configManager.getSqliteFile(),
                getLogger()
            );
            getLogger().info("Using SQLite database");
        }
        
        databaseManager.initialize();
    }
    
    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("lorep plugin disabled!");
    }
    
    public static LorepPlugin getInstance() {
        return instance;
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    public DiscordWebhook getDiscordWebhook() {
        return discordWebhook;
    }
    
    public AnticheatService getAnticheatService() {
        return anticheatService;
    }
    
    public void setConfigManager(ConfigManager configManager) {
        this.configManager = configManager;
    }
    
    public void setDatabaseManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }
    
    public void setDiscordWebhook(DiscordWebhook discordWebhook) {
        this.discordWebhook = discordWebhook;
    }
}
