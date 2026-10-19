package dev.loki.lorep.command;

import dev.loki.lorep.LorepPlugin;
import dev.loki.lorep.database.Report;
import dev.loki.lorep.gui.AnticheatGui;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ReportCommand implements CommandExecutor, TabCompleter {

    private static final int MAX_REASON_LENGTH = 500;

    private final LorepPlugin plugin;

    public ReportCommand(LorepPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("console-only"));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(plugin.getConfigManager().getMessage("usage"));
            return true;
        }

        if (args[0].equalsIgnoreCase("gui")) {
            return handleGui(player);
        }

        if (args[0].equalsIgnoreCase("stats") && args.length >= 2) {
            return handleStats(player, args[1]);
        }

        if (args[0].equalsIgnoreCase("image") && args.length >= 3) {
            return handleReportImage(player, args[1], args[2]);
        }

        if (args[0].equalsIgnoreCase("ac")) {
            return handleAnticheat(player);
        }

        if (!player.hasPermission("lorep.report")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(plugin.getConfigManager().getMessage("usage"));
            return true;
        }

        String targetName = args[0];
        String reason;
        String imageUrl = null;

        if (args.length >= 3 && args[2].startsWith("http")) {
            reason = args[1];
            imageUrl = args[2];
        } else {
            reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        }

        if (reason.trim().isEmpty()) {
            player.sendMessage(plugin.getConfigManager().getMessage("usage"));
            return true;
        }

        return handleReport(player, targetName, reason, imageUrl);
    }

    private boolean handleReport(Player reporter, String targetName, String reason) {
        return handleReport(reporter, targetName, reason, null);
    }

    private boolean handleReport(Player reporter, String targetName, String reason, String imageUrl) {
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            reporter.sendMessage(plugin.getConfigManager().getMessage("player-not-found"));
            return true;
        }

        if (target.getUniqueId().equals(reporter.getUniqueId())) {
            reporter.sendMessage(plugin.getConfigManager().getMessage("self-report"));
            return true;
        }

        if (plugin.getDatabaseManager().hasReported(reporter.getUniqueId(), target.getUniqueId())) {
            reporter.sendMessage(plugin.getConfigManager().getMessage("already-reported"));
            return true;
        }

        // Validate the final reason (player text plus anticheat summary); the summary
        // is truncated first so the player's own text is never cut.
        String finalReason = appendAnticheatSummary(target.getUniqueId(), reason);
        if (finalReason.length() > MAX_REASON_LENGTH) {
            reporter.sendMessage(plugin.getConfigManager().getMessage("reason-too-long",
                "%max%", String.valueOf(MAX_REASON_LENGTH)));
            return true;
        }

        Report report = new Report(
            reporter.getUniqueId(),
            reporter.getName(),
            target.getUniqueId(),
            target.getName() != null ? target.getName() : targetName,
            finalReason,
            imageUrl,
            plugin.getConfigManager().getServerName()
        );

        plugin.getDatabaseManager().saveReport(report);

        if (plugin.getDiscordWebhook() != null) {
            plugin.getDiscordWebhook().sendReport(report);
        }

        reporter.sendMessage(plugin.getConfigManager().getMessage("report-sent"));
        return true;
    }

    /**
     * Appends the ServGuard anticheat summary to the player's reason, truncating
     * the summary so the combined text stays within the reason length limit.
     */
    private String appendAnticheatSummary(UUID targetUuid, String reason) {
        String summary = plugin.getAnticheatService().buildSummary(targetUuid);
        if (summary.isEmpty()) {
            return reason;
        }

        int budget = MAX_REASON_LENGTH - reason.length();
        if (budget <= 0) {
            return reason;
        }
        if (summary.length() > budget) {
            summary = summary.substring(0, budget);
        }
        return reason + " " + summary;
    }

    private boolean handleReportImage(Player player, String idStr, String imageUrl) {
        if (!player.hasPermission("lorep.admin")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        int reportId;
        try {
            reportId = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getConfigManager().getMessage("invalid-report-id"));
            return true;
        }

        Report report = plugin.getDatabaseManager().getReportById(reportId);
        if (report == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("report-not-found"));
            return true;
        }

        plugin.getDatabaseManager().updateReportImage(reportId, imageUrl);

        Report updated = new Report(
            report.id(),
            report.reporterUuid(),
            report.reporterName(),
            report.targetUuid(),
            report.targetName(),
            report.reason(),
            imageUrl,
            report.status(),
            report.createdAt(),
            report.serverName()
        );

        if (plugin.getDiscordWebhook() != null) {
            plugin.getDiscordWebhook().sendReport(updated);
        }

        player.sendMessage(plugin.getConfigManager().getMessage("report-image-updated", "%id%", String.valueOf(reportId)));
        return true;
    }

    private boolean handleGui(Player player) {
        if (!player.hasPermission("lorep.admin")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        new dev.loki.lorep.gui.ReportGui(plugin, 0).open(player);
        return true;
    }

    private boolean handleAnticheat(Player player) {
        if (!player.hasPermission("lorep.admin")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        new AnticheatGui(plugin, 0).open(player);
        return true;
    }

    private boolean handleStats(Player player, String targetName) {
        new ReportStatsCommand(plugin).execute(player, targetName);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String input = args[0].toLowerCase();

            if (sender.hasPermission("lorep.admin")) {
                if ("gui".startsWith(input)) {
                    completions.add("gui");
                }
                if ("stats".startsWith(input)) {
                    completions.add("stats");
                }
                if ("image".startsWith(input)) {
                    completions.add("image");
                }
                if ("ac".startsWith(input)) {
                    completions.add("ac");
                }
            }

            completions.addAll(
                Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .collect(Collectors.toList())
            );
        } else if (args.length == 2 && args[0].equalsIgnoreCase("stats")) {
            String input = args[1].toLowerCase();
            completions.addAll(
                Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .collect(Collectors.toList())
            );
        }

        return completions;
    }
}
