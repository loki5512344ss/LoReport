package dev.loki.lorep.gui;

import dev.loki.lorep.LorepPlugin;
import dev.loki.lorep.database.Report;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.UUID;

public class GuiClickListener implements Listener {

    private final LorepPlugin plugin;

    public GuiClickListener(LorepPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ActionInventory gui)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        String action = gui.slotActions().get(event.getRawSlot());

        if (action == null) {
            return;
        }

        switch (action) {
            case "prev_page" -> handlePrevPage(player, gui);
            case "next_page" -> handleNextPage(player, gui);
            case "close" -> handleClose(player, gui);
            case "back" -> handleBack(player, gui);
            case "teleport" -> handleTeleport(player, gui);
            case "screenshot" -> handleScreenshot(player, gui);
            default -> handleDefaultAction(player, gui, action);
        }
    }

    private void handlePrevPage(Player player, ActionInventory gui) {
        if (gui.getPage() > 0) {
            gui.openPage(player, gui.getPage() - 1);
        }
    }

    private void handleNextPage(Player player, ActionInventory gui) {
        if (gui.getPage() < gui.getTotalPages() - 1) {
            gui.openPage(player, gui.getPage() + 1);
        }
    }

    private void handleClose(Player player, ActionInventory gui) {
        if (gui instanceof ReportGui && gui.isDetailView()) {
            gui.openPage(player, gui.getPage());
        } else {
            player.closeInventory();
        }
    }

    private void handleBack(Player player, ActionInventory gui) {
        if (!(gui instanceof AnticheatGui anticheatGui)) {
            return;
        }
        if (anticheatGui.isDetailView()) {
            anticheatGui.openPage(player, anticheatGui.getPage());
        } else {
            new ReportGui(plugin, 0).open(player);
        }
    }

    private void handleTeleport(Player player, ActionInventory gui) {
        if (!(gui instanceof ReportGui reportGui)) {
            return;
        }
        Report report = reportGui.getCurrentReport();
        if (report == null) {
            return;
        }
        Player target = Bukkit.getPlayer(report.targetUuid());
        if (target != null && target.isOnline()) {
            // Folia-safe: cross-region teleports must go through the async scheduler
            player.teleportAsync(target.getLocation());
            player.sendMessage(plugin.getConfigManager().getMessage("teleported-to", "%player%", report.targetName()));
        } else {
            player.sendMessage(plugin.getConfigManager().getMessage("player-offline", "%player%", report.targetName()));
        }
    }

    private void handleScreenshot(Player player, ActionInventory gui) {
        if (!(gui instanceof ReportGui reportGui)) {
            return;
        }
        Report report = reportGui.getCurrentReport();
        if (report != null && report.imageUrl() != null && !report.imageUrl().isEmpty()) {
            player.sendMessage(plugin.getConfigManager().getMessage("screenshot-link", "%url%", report.imageUrl()));
        }
    }

    private void handleDefaultAction(Player player, ActionInventory gui, String action) {
        if (gui instanceof AnticheatGui anticheatGui) {
            handleAnticheatAction(player, anticheatGui, action);
        } else if (gui instanceof ReportGui reportGui) {
            handleReportAction(player, reportGui, action);
        }
    }

    private void handleReportAction(Player player, ReportGui gui, String action) {
        if (action.startsWith("status_set:")) {
            handleStatusSet(player, gui, action.substring("status_set:".length()));
        } else if (action.startsWith("report_detail:")) {
            handleReportDetail(player, gui, action.substring("report_detail:".length()));
        } else if ("anticheat_open".equals(action)) {
            new AnticheatGui(plugin, 0).open(player);
        }
    }

    private void handleAnticheatAction(Player player, AnticheatGui gui, String action) {
        if (action.startsWith("ac_detail:")) {
            handleAnticheatDetail(player, gui, action.substring("ac_detail:".length()));
        } else if ("open-reports".equals(action)) {
            new ReportGui(plugin, 0).open(player);
        }
    }

    private void handleStatusSet(Player player, ReportGui gui, String status) {
        Report report = gui.currentReport;
        if (report == null) {
            return;
        }
        plugin.getDatabaseManager().updateReportStatus(report.id(), status);
        Report updated = plugin.getDatabaseManager().getReportById(report.id());
        if (updated != null && plugin.getDiscordWebhook() != null) {
            plugin.getDiscordWebhook().sendStatusUpdate(updated);
        }
        if (updated != null) {
            new ReportGui(plugin, gui.page).openDetail(player, updated);
        }
    }

    private void handleReportDetail(Player player, ReportGui gui, String idStr) {
        try {
            int reportId = Integer.parseInt(idStr);
            Report report = plugin.getDatabaseManager().getReportById(reportId);
            if (report != null) {
                new ReportGui(plugin, gui.page).openDetail(player, report);
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private void handleAnticheatDetail(Player player, AnticheatGui gui, String uuidStr) {
        try {
            UUID playerUuid = UUID.fromString(uuidStr);
            new AnticheatGui(plugin, gui.getPage()).openDetail(player, playerUuid, gui.getPlayerName(playerUuid));
        } catch (IllegalArgumentException ignored) {
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ActionInventory) {
            event.setCancelled(true);
        }
    }
}
