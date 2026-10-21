package dev.loki.lorep.command;

import dev.loki.lorep.LorepPlugin;
import dev.loki.lorep.database.Report;
import dev.loki.lorep.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;

public class ReportStatsCommand {

    private static final int MAX_RECENT_REPORTS = 5;

    private final LorepPlugin plugin;
    
    public ReportStatsCommand(LorepPlugin plugin) {
        this.plugin = plugin;
    }
    
    public void execute(Player player, String targetName) {
        if (!player.hasPermission("lorep.admin")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            player.sendMessage(plugin.getConfigManager().getMessage("player-not-found"));
            return;
        }
        
        List<Report> reports = plugin.getDatabaseManager().getReportsForTarget(target.getUniqueId());
        int reportCount = reports.size();
        
        if (reportCount == 0) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-reports"));
            return;
        }
        
        // Header
        player.sendMessage(plugin.getConfigManager().getMessage("stats-header", 
            "%player%", target.getName() != null ? target.getName() : targetName));
        
        // Report count
        player.sendMessage(plugin.getConfigManager().getMessage("stats-count",
            "%count%", String.valueOf(reportCount)));
        
        String lastOnline = target.isOnline()
            ? plugin.getConfigManager().getMessageText("online-status")
            : TimeUtil.formatTimeAgo(target.getLastPlayed());
        player.sendMessage(plugin.getConfigManager().getMessage("stats-last-online",
            "%time%", lastOnline));

        player.sendMessage(plugin.getConfigManager().getMessage("stats-recent-header"));
        int shown = Math.min(MAX_RECENT_REPORTS, reports.size());
        for (int i = 0; i < shown; i++) {
            Report report = reports.get(i);
            String timeAgo = TimeUtil.formatTimeAgo(report.createdAt().toEpochMilli());
            player.sendMessage(plugin.getConfigManager().getMessage("stats-report-entry",
                "%reason%", report.reason(),
                "%time%", timeAgo));
        }

        if (reports.size() > MAX_RECENT_REPORTS) {
            player.sendMessage(plugin.getConfigManager().getMessage("stats-more-reports",
                "%count%", String.valueOf(reports.size() - MAX_RECENT_REPORTS)));
        }
    }
}
