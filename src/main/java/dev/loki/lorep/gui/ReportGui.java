package dev.loki.lorep.gui;

import dev.loki.lorep.LorepPlugin;
import dev.loki.lorep.config.GuiItemConfig;
import dev.loki.lorep.database.Report;
import dev.loki.lorep.util.PaginationUtil;
import dev.loki.lorep.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportGui implements ActionInventory {

    public static final int INVENTORY_SIZE = 54;

    final int page;
    boolean detailView;
    Report currentReport;
    final Map<Integer, String> slotActions = new HashMap<>();

    private final LorepPlugin plugin;
    private Inventory inventory;

    public ReportGui(LorepPlugin plugin, int page) {
        this.plugin = plugin;
        this.page = page;
        this.detailView = false;
        this.currentReport = null;
    }

    public void open(Player player) {
        detailView = false;
        currentReport = null;
        slotActions.clear();

        String title = plugin.getConfigManager().getMessage("gui-title",
            "%page%", String.valueOf(page + 1));
        this.inventory = Bukkit.createInventory(this, INVENTORY_SIZE, title);

        int pageSize = plugin.getConfig().getInt("gui.reports-per-page", 45);
        List<Report> reports = plugin.getDatabaseManager().getReportsPaginated(page, pageSize);
        int totalReports = plugin.getDatabaseManager().getTotalReportCount();
        int totalPages = PaginationUtil.calculateTotalPages(totalReports, pageSize);

        GuiItemConfig entryConfig = plugin.getConfigManager().getGuiItem("report-entry");

        for (int i = 0; i < reports.size(); i++) {
            Report report = reports.get(i);
            int targetReportCount = plugin.getDatabaseManager().getReportCount(report.targetUuid());

            ItemStack item;
            if (entryConfig != null) {
                item = entryConfig.createItem();
                if (item.getItemMeta() instanceof SkullMeta skullMeta) {
                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(report.targetUuid());
                    skullMeta.setOwningPlayer(offlinePlayer);
                    item.setItemMeta(skullMeta);
                }
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(replacePlaceholders(meta.getDisplayName(), report, targetReportCount));
                    if (meta.getLore() != null) {
                        List<String> lore = meta.getLore();
                        List<String> newLore = new ArrayList<>();
                        for (String line : lore) {
                            newLore.add(replacePlaceholders(line, report, targetReportCount));
                        }
                        meta.setLore(newLore);
                    }
                    item.setItemMeta(meta);
                }
            } else {
                item = createFallbackReportItem(report, targetReportCount);
            }

            List<Integer> slots = entryConfig != null ? entryConfig.slots() : List.of();
            if (!slots.isEmpty() && i < slots.size()) {
                int slot = slots.get(i);
                inventory.setItem(slot, item);
                slotActions.put(slot, "report_detail:" + report.id());
            } else {
                inventory.setItem(i, item);
                slotActions.put(i, "report_detail:" + report.id());
            }
        }

        addNavigation(totalReports, totalPages);
        player.openInventory(inventory);
    }

    public void openDetail(Player player, Report report) {
        detailView = true;
        currentReport = report;
        slotActions.clear();

        String title = plugin.getConfigManager().getMessage("gui-title-detail", "%id%", String.valueOf(report.id()));
        this.inventory = Bukkit.createInventory(this, INVENTORY_SIZE, title);

        int targetReportCount = plugin.getDatabaseManager().getReportCount(report.targetUuid());

        GuiItemConfig entryConfig = plugin.getConfigManager().getGuiItem("report-entry");
        ItemStack item;
        if (entryConfig != null) {
            item = entryConfig.createItem();
            if (item.getItemMeta() instanceof SkullMeta skullMeta) {
                skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(report.targetUuid()));
                item.setItemMeta(skullMeta);
            }
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(replacePlaceholders(meta.getDisplayName(), report, targetReportCount));
                if (meta.getLore() != null) {
                    List<String> lore = meta.getLore();
                    List<String> newLore = new ArrayList<>();
                    for (String line : lore) {
                        newLore.add(replacePlaceholders(line, report, targetReportCount));
                    }
                    meta.setLore(newLore);
                }
                item.setItemMeta(meta);
            }
        } else {
            item = createFallbackReportItem(report, targetReportCount);
        }
        inventory.setItem(22, item);

        placeDetailButton("teleport", "teleport");
        if (report.imageUrl() != null && !report.imageUrl().isEmpty()) {
            placeDetailButton("screenshot", "screenshot");
        }

        String currentStatus = report.status() != null ? report.status() : "PENDING";
        placeStatusButton("status-pending", "status_set:PENDING", "PENDING".equals(currentStatus));
        placeStatusButton("status-reviewing", "status_set:REVIEWING", "REVIEWING".equals(currentStatus));
        placeStatusButton("status-resolved", "status_set:RESOLVED", "RESOLVED".equals(currentStatus));
        placeStatusButton("status-dismissed", "status_set:DISMISSED", "DISMISSED".equals(currentStatus));

        GuiItemConfig closeConfig = plugin.getConfigManager().getGuiItem("close");
        if (closeConfig != null) {
            List<Integer> slots = closeConfig.slots();
            ItemStack closeItem = closeConfig.createItem();
            if (slots.isEmpty()) {
                inventory.setItem(49, closeItem);
                slotActions.put(49, "close");
            } else {
                for (int slot : slots) {
                    inventory.setItem(slot, closeItem);
                    slotActions.put(slot, "close");
                }
            }
        }

        player.openInventory(inventory);
    }

    private void placeDetailButton(String configPath, String action) {
        GuiItemConfig config = plugin.getConfigManager().getGuiItem(configPath);
        if (config == null) {
            return;
        }

        List<Integer> slots = config.slots();
        ItemStack button = config.createItem();
        if (slots.isEmpty()) {
            int slot = switch (action) {
                case "teleport" -> 20;
                case "screenshot" -> 24;
                default -> 22;
            };
            inventory.setItem(slot, button);
            slotActions.put(slot, action);
        } else {
            for (int slot : slots) {
                inventory.setItem(slot, button);
                slotActions.put(slot, action);
            }
        }
    }

    private void placeStatusButton(String configPath, String action, boolean active) {
        GuiItemConfig config = plugin.getConfigManager().getGuiItem(configPath);
        if (config == null) {
            return;
        }

        List<Integer> slots = config.slots();
        ItemStack button = config.createItem();
        if (active) {
            button.addUnsafeEnchantment(Enchantment.ARROW_DAMAGE, 1);
            ItemMeta meta = button.getItemMeta();
            if (meta != null) {
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                button.setItemMeta(meta);
            }
        }
        if (slots.isEmpty()) {
            int slot = switch (action) {
                case "status_set:PENDING" -> 29;
                case "status_set:REVIEWING" -> 30;
                case "status_set:RESOLVED" -> 31;
                case "status_set:DISMISSED" -> 32;
                default -> 29;
            };
            inventory.setItem(slot, button);
            slotActions.put(slot, action);
        } else {
            for (int slot : slots) {
                inventory.setItem(slot, button);
                slotActions.put(slot, action);
            }
        }
    }

    private void addNavigation(int totalReports, int totalPages) {
        if (page > 0) {
            GuiItemConfig prevConfig = plugin.getConfigManager().getGuiItem("prev-page");
            if (prevConfig != null) {
                List<Integer> slots = prevConfig.slots();
                ItemStack item = prevConfig.createItem();
                if (slots.isEmpty()) {
                    inventory.setItem(45, item);
                    slotActions.put(45, "prev_page");
                } else {
                    for (int slot : slots) {
                        inventory.setItem(slot, item);
                        slotActions.put(slot, "prev_page");
                    }
                }
            }
        }

        GuiItemConfig pageInfoConfig = plugin.getConfigManager().getGuiItem("page-info");
        if (pageInfoConfig != null) {
            List<Integer> slots = pageInfoConfig.slots();
            ItemStack item = pageInfoConfig.createItem();
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(meta.getDisplayName()
                    .replace("%page%", String.valueOf(page + 1))
                    .replace("%total%", String.valueOf(Math.max(1, totalPages)))
                    .replace("%total_reports%", String.valueOf(totalReports)));
                if (meta.getLore() != null) {
                    List<String> lore = meta.getLore();
                    List<String> newLore = new ArrayList<>();
                    for (String line : lore) {
                        newLore.add(line
                            .replace("%page%", String.valueOf(page + 1))
                            .replace("%total%", String.valueOf(Math.max(1, totalPages)))
                            .replace("%total_reports%", String.valueOf(totalReports)));
                    }
                    meta.setLore(newLore);
                }
                item.setItemMeta(meta);
            }
            if (slots.isEmpty()) {
                inventory.setItem(49, item);
            } else {
                for (int slot : slots) {
                    inventory.setItem(slot, item);
                }
            }
        }

        if (page < totalPages - 1) {
            GuiItemConfig nextConfig = plugin.getConfigManager().getGuiItem("next-page");
            if (nextConfig != null) {
                List<Integer> slots = nextConfig.slots();
                ItemStack item = nextConfig.createItem();
                if (slots.isEmpty()) {
                    inventory.setItem(53, item);
                    slotActions.put(53, "next_page");
                } else {
                    for (int slot : slots) {
                        inventory.setItem(slot, item);
                        slotActions.put(slot, "next_page");
                    }
                }
            }
        }

        GuiItemConfig closeConfig = plugin.getConfigManager().getGuiItem("close");
        if (closeConfig != null) {
            List<Integer> slots = closeConfig.slots();
            ItemStack item = closeConfig.createItem();
            if (slots.isEmpty()) {
                inventory.setItem(47, item);
                slotActions.put(47, "close");
            } else {
                for (int slot : slots) {
                    inventory.setItem(slot, item);
                    slotActions.put(slot, "close");
                }
            }
        }
    }

    private String replacePlaceholders(String text, Report report, int reportCount) {
        String statusDisplay = plugin.getConfigManager().getMessage("status-" + report.status().toLowerCase());
        return text
            .replace("%target_name%", report.targetName() != null ? report.targetName() : "")
            .replace("%reporter_name%", report.reporterName() != null ? report.reporterName() : "")
            .replace("%reason%", report.reason() != null ? report.reason() : "")
            .replace("%time%", TimeUtil.formatTimeAgo(report.createdAt()))
            .replace("%status%", statusDisplay)
            .replace("%server%", report.serverName() != null ? report.serverName() : Report.UNKNOWN_SERVER)
            .replace("%report_count%", String.valueOf(reportCount));
    }

    private ItemStack createFallbackReportItem(Report report, int totalReports) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(report.targetUuid()));
            meta.setDisplayName("§c" + report.targetName());
            List<String> lore = new ArrayList<>();
            lore.add("§7");
            lore.add("§eBy: §f" + report.reporterName());
            lore.add("§eReason: §f" + report.reason());
            lore.add("§eTime: §f" + TimeUtil.formatTimeAgo(report.createdAt()));
            lore.add("§7");
            lore.add("§eReports: §c" + totalReports);
            meta.setLore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    public int getPage() {
        return page;
    }

    public boolean isDetailView() {
        return detailView;
    }

    public Report getCurrentReport() {
        return currentReport;
    }

    @Override
    public Map<Integer, String> slotActions() {
        return slotActions;
    }

    @Override
    public void openPage(Player player, int page) {
        new ReportGui(plugin, page).open(player);
    }

    @Override
    public int getTotalPages() {
        int pageSize = plugin.getConfig().getInt("gui.reports-per-page", 45);
        int totalReports = plugin.getDatabaseManager().getTotalReportCount();
        return PaginationUtil.calculateTotalPages(totalReports, pageSize);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
