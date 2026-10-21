package dev.loki.lorep.gui;

import dev.loki.lorep.LorepPlugin;
import dev.loki.lorep.anticheat.AnticheatService;
import dev.loki.lorep.config.GuiItemConfig;
import dev.loki.lorep.database.DatabaseManager.FlaggedPlayer;
import dev.loki.lorep.database.DatabaseManager.ViolationEntry;
import dev.loki.lorep.gui.item.AnticheatItems;
import dev.loki.lorep.gui.item.EntrySlots;
import dev.loki.lorep.util.PaginationUtil;
import dev.loki.lorep.util.TextFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ServGuard anticheat overview GUI: paginated list of flagged players and a
 * per-player view of recent flags. Actions: ac_detail, prev/next page, back to
 * the report list, open-reports, close.
 */
public class AnticheatGui implements ActionInventory {

    public static final int INVENTORY_SIZE = 54;

    /** Slot of the single informational item. */
    private static final int INFO_SLOT = 22;

    /** Navigation slots of the list view. */
    private static final int SLOT_PREV_PAGE = 45;
    private static final int SLOT_BACK = 46;
    private static final int SLOT_CLOSE = 47;
    private static final int SLOT_PAGE_INFO = 49;
    private static final int SLOT_NEXT_PAGE = 53;

    private final LorepPlugin plugin;
    private final int page;
    private boolean detailView;
    private final Map<UUID, String> playerNames = new HashMap<>();
    private final Map<Integer, String> slotActions = new HashMap<>();
    private Inventory inventory;

    public AnticheatGui(LorepPlugin plugin, int page) {
        this.plugin = plugin;
        this.page = page;
    }

    /** Opens the list of flagged players. */
    public void open(Player player) {
        detailView = false;
        playerNames.clear();
        slotActions.clear();

        Component title = plugin.getConfigManager().getMessage("anticheat-list-title",
            "%page%", String.valueOf(page + 1));
        this.inventory = Bukkit.createInventory(this, INVENTORY_SIZE, title);

        AnticheatService service = plugin.getAnticheatService();
        int pageSize = plugin.getConfig().getInt("gui.reports-per-page", 45);
        List<FlaggedPlayer> flagged = service.getFlaggedPlayers(page, pageSize);
        if (flagged == null) {
            placeInfoItem(plugin.getConfigManager().getMessageText("anticheat-unavailable"));
            player.openInventory(inventory);
            return;
        }

        int totalPlayers = service.getFlaggedPlayerCount();
        GuiItemConfig entryConfig = plugin.getConfigManager().getGuiItem("anticheat-entry", Material.PLAYER_HEAD);
        List<Integer> slots = EntrySlots.resolve(entryConfig, INVENTORY_SIZE);
        AnticheatItems items = new AnticheatItems(plugin.getConfigManager());

        for (int i = 0; i < flagged.size() && i < slots.size(); i++) {
            FlaggedPlayer entry = flagged.get(i);
            if (entry.playerUuid() != null) {
                playerNames.put(entry.playerUuid(), entry.playerName());
            }
            int reportCount = entry.playerUuid() != null
                ? plugin.getDatabaseManager().getReportCount(entry.playerUuid()) : 0;
            int slot = slots.get(i);
            inventory.setItem(slot, items.entry(entryConfig, entry, reportCount));
            slotActions.put(slot, "ac_detail:" + entry.playerUuid());
        }

        addNavigation(PaginationUtil.calculateTotalPages(totalPlayers, pageSize), totalPlayers);
        player.openInventory(inventory);
    }

    /**
     * Opens the detail view of one flagged player.
     *
     * @param player the viewer
     * @param playerUuid the flagged player
     * @param playerName the flagged player name
     */
    public void openDetail(Player player, UUID playerUuid, String playerName) {
        detailView = true;
        slotActions.clear();

        String name = playerName != null ? playerName : playerUuid.toString();
        Component title = plugin.getConfigManager().getMessage("anticheat-detail-title", "%player_name%", name);
        this.inventory = Bukkit.createInventory(this, INVENTORY_SIZE, title);

        AnticheatService service = plugin.getAnticheatService();
        if (!service.isAvailable()) {
            placeInfoItem(plugin.getConfigManager().getMessageText("anticheat-unavailable"));
            player.openInventory(inventory);
            return;
        }

        List<ViolationEntry> violations = service.getRecentViolations(playerUuid);
        if (violations.isEmpty()) {
            placeInfoItem(plugin.getConfigManager().getMessageText("anticheat-detail-empty"));
        } else {
            placeDetailItem(playerUuid, name, violations);
        }

        placeConfigItem("anticheat-tab", SLOT_BACK, "back");
        placeConfigItem("close", SLOT_CLOSE, "close");
        placeConfigItem("anticheat-reports", SLOT_PAGE_INFO, "open-reports");
        player.openInventory(inventory);
    }

    /** Places the navigation buttons of the list view. */
    private void addNavigation(int totalPages, int totalPlayers) {
        if (page > 0) {
            placeConfigItem("prev-page", SLOT_PREV_PAGE, "prev_page");
        }
        placeConfigItem("anticheat-tab", SLOT_BACK, "back");
        placeConfigItem("close", SLOT_CLOSE, "close");
        placePageInfo(totalPages, totalPlayers);
        if (page < totalPages - 1) {
            placeConfigItem("next-page", SLOT_NEXT_PAGE, "next_page");
        }
    }

    /** Places a config driven button, using the config slots when there are any. */
    private void placeConfigItem(String configPath, int defaultSlot, String action) {
        GuiItemConfig config = plugin.getConfigManager().getGuiItem(configPath);
        if (config == null) {
            return;
        }
        placeItem(config.slots(), defaultSlot, config.createItem(), action);
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

    /** Places the page counter of the list view. */
    private void placePageInfo(int totalPages, int totalPlayers) {
        GuiItemConfig config = plugin.getConfigManager().getGuiItem("anticheat-page-info");
        if (config == null) {
            return;
        }

        String name = replacePagePlaceholders(config.displayName(), totalPages, totalPlayers);
        List<String> lore = new ArrayList<>();
        for (String line : config.lore()) {
            lore.add(replacePagePlaceholders(line, totalPages, totalPlayers));
        }
        placeItem(config.slots(), SLOT_PAGE_INFO, config.createItem(name, lore), null);
    }

    /** Places the detail item of one flagged player. */
    private void placeDetailItem(UUID playerUuid, String name, List<ViolationEntry> violations) {
        GuiItemConfig config = plugin.getConfigManager().getGuiItem("anticheat-detail", Material.PLAYER_HEAD);
        int reportCount = plugin.getDatabaseManager().getReportCount(playerUuid);

        AnticheatItems items = new AnticheatItems(plugin.getConfigManager());
        ItemStack item = items.detail(config, playerUuid, name, violations, reportCount);

        List<Integer> slots = config != null ? config.slots() : List.of();
        inventory.setItem(slots.isEmpty() ? INFO_SLOT : slots.get(0), item);
    }

    /** Expands the page placeholders of the page counter. */
    private String replacePagePlaceholders(String text, int totalPages, int totalPlayers) {
        if (text == null) {
            return null;
        }
        return text
            .replace("%page%", String.valueOf(page + 1))
            .replace("%total%", String.valueOf(Math.max(1, totalPages)))
            .replace("%total_players%", String.valueOf(totalPlayers));
    }

    /** Places the informational item used when there is nothing to show. */
    private void placeInfoItem(String text) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextFormat.component(text));
            item.setItemMeta(meta);
        }
        inventory.setItem(INFO_SLOT, item);
    }

    /** @return the name recorded for the player when the list view was rendered. */
    public String getPlayerName(UUID playerUuid) {
        return playerNames.get(playerUuid);
    }

    @Override
    public Map<Integer, String> slotActions() {
        return slotActions;
    }

    @Override
    public int getPage() {
        return page;
    }

    @Override
    public boolean isDetailView() {
        return detailView;
    }

    @Override
    public void openPage(Player player, int page) {
        new AnticheatGui(plugin, page).open(player);
    }

    @Override
    public int getTotalPages() {
        int pageSize = plugin.getConfig().getInt("gui.reports-per-page", 45);
        int totalPlayers = plugin.getAnticheatService().getFlaggedPlayerCount();
        return PaginationUtil.calculateTotalPages(totalPlayers, pageSize);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
