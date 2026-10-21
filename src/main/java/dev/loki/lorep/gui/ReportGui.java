package dev.loki.lorep.gui;

import dev.loki.lorep.LorepPlugin;
import dev.loki.lorep.config.GuiItemConfig;
import dev.loki.lorep.database.Report;
import dev.loki.lorep.gui.item.EntrySlots;
import dev.loki.lorep.gui.item.ReportItems;
import dev.loki.lorep.util.PaginationUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Report list GUI: paginated list of reports, the detail view of a single
 * report and the navigation into the anticheat tab.
 */
public class ReportGui implements ActionInventory {

    public static final int INVENTORY_SIZE = 54;

    /** Slot of the report item in the detail view. */
    private static final int INFO_SLOT = 22;

    /** Navigation slots of the list view. */
    private static final int SLOT_PREV_PAGE = 45;
    private static final int SLOT_ANTICHEAT_TAB = 46;
    private static final int SLOT_CLOSE = 47;
    private static final int SLOT_PAGE_INFO = 49;
    private static final int SLOT_NEXT_PAGE = 53;

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

    /** Opens the list of reports. */
    public void open(Player player) {
        detailView = false;
        currentReport = null;
        slotActions.clear();

        Component title = plugin.getConfigManager().getMessage("gui-title",
            "%page%", String.valueOf(page + 1));
        this.inventory = Bukkit.createInventory(this, INVENTORY_SIZE, title);

        int pageSize = plugin.getConfig().getInt("gui.reports-per-page", 45);
        List<Report> reports = plugin.getDatabaseManager().getReportsPaginated(page, pageSize);
        int totalReports = plugin.getDatabaseManager().getTotalReportCount();
        int totalPages = PaginationUtil.calculateTotalPages(totalReports, pageSize);

        GuiItemConfig entryConfig = plugin.getConfigManager().getGuiItem("report-entry", Material.PLAYER_HEAD);
        List<Integer> slots = EntrySlots.resolve(entryConfig, INVENTORY_SIZE);
        ReportItems items = new ReportItems(plugin.getConfigManager());

        for (int i = 0; i < reports.size() && i < slots.size(); i++) {
            Report report = reports.get(i);
            int targetReportCount = plugin.getDatabaseManager().getReportCount(report.targetUuid());
            int slot = slots.get(i);
            inventory.setItem(slot, items.entry(entryConfig, report, targetReportCount));
            slotActions.put(slot, "report_detail:" + report.id());
        }

        addNavigation(totalReports, totalPages);
        player.openInventory(inventory);
    }

    /**
     * Opens the detail view of one report.
     *
     * @param player the viewer
     * @param report the report to show
     */
    public void openDetail(Player player, Report report) {
        detailView = true;
        currentReport = report;
        slotActions.clear();

        Component title = plugin.getConfigManager().getMessage("gui-title-detail", "%id%", String.valueOf(report.id()));
        this.inventory = Bukkit.createInventory(this, INVENTORY_SIZE, title);

        int targetReportCount = plugin.getDatabaseManager().getReportCount(report.targetUuid());
        GuiItemConfig entryConfig = plugin.getConfigManager().getGuiItem("report-entry", Material.PLAYER_HEAD);
        ReportItems items = new ReportItems(plugin.getConfigManager());
        inventory.setItem(INFO_SLOT, items.detail(entryConfig, report, targetReportCount));

        placeConfigItem("teleport", "teleport", 20);
        if (report.imageUrl() != null && !report.imageUrl().isEmpty()) {
            placeConfigItem("screenshot", "screenshot", 24);
        }

        String currentStatus = report.status() != null ? report.status() : "PENDING";
        placeStatusButton("status-pending", "status_set:PENDING", "PENDING".equals(currentStatus), 29);
        placeStatusButton("status-reviewing", "status_set:REVIEWING", "REVIEWING".equals(currentStatus), 30);
        placeStatusButton("status-resolved", "status_set:RESOLVED", "RESOLVED".equals(currentStatus), 31);
        placeStatusButton("status-dismissed", "status_set:DISMISSED", "DISMISSED".equals(currentStatus), 32);

        placeConfigItem("close", "close", SLOT_CLOSE);
        player.openInventory(inventory);
    }

    /**
     * Places a config driven button.
     *
     * @param configPath the item path inside {@code gui.items}
     * @param action the click action of the button
     * @param defaultSlot the slot used when the config declares none
     */
    private void placeConfigItem(String configPath, String action, int defaultSlot) {
        GuiItemConfig config = plugin.getConfigManager().getGuiItem(configPath);
        if (config == null) {
            return;
        }
        placeItem(config.slots(), defaultSlot, config.createItem(), action);
    }

    /**
     * Places a status button of the detail view.
     *
     * @param configPath the item path inside {@code gui.items}
     * @param action the click action of the button
     * @param active whether the status is the current one
     * @param defaultSlot the slot used when the config declares none
     */
    private void placeStatusButton(String configPath, String action, boolean active, int defaultSlot) {
        GuiItemConfig config = plugin.getConfigManager().getGuiItem(configPath);
        if (config == null) {
            return;
        }

        ItemStack item = config.createItem();
        if (active) {
            item.addUnsafeEnchantment(Enchantment.POWER, 1);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }
        }
        placeItem(config.slots(), defaultSlot, item, action);
    }

    /** Places an item into the configured slots or into the default slot. */
    private void placeItem(List<Integer> slots, int defaultSlot, ItemStack item, String action) {
        List<Integer> targets = slots.isEmpty() ? List.of(defaultSlot) : slots;
        for (int slot : targets) {
            inventory.setItem(slot, item);
            if (action != null) {
                slotActions.put(slot, action);
            }
        }
    }

    /** Places the navigation buttons of the list view. */
    private void addNavigation(int totalReports, int totalPages) {
        if (page > 0) {
            placeConfigItem("prev-page", "prev_page", SLOT_PREV_PAGE);
        }
        placeConfigItem("anticheat-tab", "anticheat_open", SLOT_ANTICHEAT_TAB);
        placeConfigItem("close", "close", SLOT_CLOSE);
        placePageInfo(totalReports, totalPages);
        if (page < totalPages - 1) {
            placeConfigItem("next-page", "next_page", SLOT_NEXT_PAGE);
        }
    }

    /** Places the page counter of the list view. */
    private void placePageInfo(int totalReports, int totalPages) {
        GuiItemConfig config = plugin.getConfigManager().getGuiItem("page-info");
        if (config == null) {
            return;
        }

        String name = replacePagePlaceholders(config.displayName(), totalReports, totalPages);
        List<String> lore = new ArrayList<>();
        for (String line : config.lore()) {
            lore.add(replacePagePlaceholders(line, totalReports, totalPages));
        }
        placeItem(config.slots(), SLOT_PAGE_INFO, config.createItem(name, lore), null);
    }

    /** Expands the page placeholders of the page counter. */
    private String replacePagePlaceholders(String text, int totalReports, int totalPages) {
        if (text == null) {
            return null;
        }
        return text
            .replace("%page%", String.valueOf(page + 1))
            .replace("%total%", String.valueOf(Math.max(1, totalPages)))
            .replace("%total_reports%", String.valueOf(totalReports));
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
