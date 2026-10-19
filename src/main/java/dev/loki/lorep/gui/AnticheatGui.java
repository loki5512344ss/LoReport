package dev.loki.lorep.gui;

import dev.loki.lorep.LorepPlugin;
import dev.loki.lorep.anticheat.AnticheatService;
import dev.loki.lorep.config.GuiItemConfig;
import dev.loki.lorep.database.DatabaseManager.FlaggedPlayer;
import dev.loki.lorep.database.DatabaseManager.ViolationEntry;
import dev.loki.lorep.util.PaginationUtil;
import dev.loki.lorep.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

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
    private static final int INFO_SLOT = 22;

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

    public void open(Player player) {
        detailView = false;
        playerNames.clear();
        slotActions.clear();

        String title = plugin.getConfigManager().getMessage("anticheat-list-title",
            "%page%", String.valueOf(page + 1));
        this.inventory = Bukkit.createInventory(this, INVENTORY_SIZE, title);

        AnticheatService service = plugin.getAnticheatService();
        int pageSize = plugin.getConfig().getInt("gui.reports-per-page", 45);
        List<FlaggedPlayer> flagged = service.getFlaggedPlayers(page, pageSize);
        if (flagged == null) {
            placeInfoItem(plugin.getConfigManager().getMessage("anticheat-unavailable"));
            player.openInventory(inventory);
            return;
        }

        int totalPlayers = service.getFlaggedPlayerCount();
        GuiItemConfig entryConfig = plugin.getConfigManager().getGuiItem("anticheat-entry");

        for (int i = 0; i < flagged.size(); i++) {
            FlaggedPlayer entry = flagged.get(i);
            if (entry.playerUuid() != null) {
                playerNames.put(entry.playerUuid(), entry.playerName());
            }
            List<Integer> slots = entryConfig != null ? entryConfig.slots() : List.of();
            int slot = !slots.isEmpty() && i < slots.size() ? slots.get(i) : i;
            inventory.setItem(slot, createEntryItem(entry, entryConfig));
            slotActions.put(slot, "ac_detail:" + entry.playerUuid());
        }

        addNavigation(PaginationUtil.calculateTotalPages(totalPlayers, pageSize), totalPlayers);
        player.openInventory(inventory);
    }

    public void openDetail(Player player, UUID playerUuid, String playerName) {
        detailView = true;
        slotActions.clear();

        String name = playerName != null ? playerName : playerUuid.toString();
        String title = plugin.getConfigManager().getMessage("anticheat-detail-title", "%player_name%", name);
        this.inventory = Bukkit.createInventory(this, INVENTORY_SIZE, title);

        AnticheatService service = plugin.getAnticheatService();
        if (!service.isAvailable()) {
            placeInfoItem(plugin.getConfigManager().getMessage("anticheat-unavailable"));
            player.openInventory(inventory);
            return;
        }

        List<ViolationEntry> violations = service.getRecentViolations(playerUuid);
        if (violations.isEmpty()) {
            placeInfoItem(plugin.getConfigManager().getMessage("anticheat-detail-empty"));
        } else {
            placeDetailItem(playerUuid, name, violations);
        }

        placeConfigItem("anticheat-tab", 45, "back");
        placeConfigItem("close", 47, "close");
        placeConfigItem("anticheat-reports", 49, "open-reports");
        player.openInventory(inventory);
    }

    private void addNavigation(int totalPages, int totalPlayers) {
        if (page > 0) {
            placeConfigItem("prev-page", 45, "prev_page");
        }
        placeConfigItem("anticheat-tab", 46, "back");
        placeConfigItem("close", 47, "close");
        placePageInfo(totalPages, totalPlayers);
        if (page < totalPages - 1) {
            placeConfigItem("next-page", 53, "next_page");
        }
    }

    private void placeConfigItem(String configPath, int defaultSlot, String action) {
        GuiItemConfig config = plugin.getConfigManager().getGuiItem(configPath);
        if (config == null) {
            return;
        }

        List<Integer> slots = config.slots();
        ItemStack item = config.createItem();
        if (slots.isEmpty()) {
            inventory.setItem(defaultSlot, item);
            slotActions.put(defaultSlot, action);
        } else {
            for (int slot : slots) {
                inventory.setItem(slot, item);
                slotActions.put(slot, action);
            }
        }
    }

    private void placePageInfo(int totalPages, int totalPlayers) {
        GuiItemConfig config = plugin.getConfigManager().getGuiItem("anticheat-page-info");
        if (config == null) {
            return;
        }

        ItemStack item = config.createItem();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(replacePagePlaceholders(meta.getDisplayName(), totalPages, totalPlayers));
            if (meta.getLore() != null) {
                List<String> lore = new ArrayList<>();
                for (String line : meta.getLore()) {
                    lore.add(replacePagePlaceholders(line, totalPages, totalPlayers));
                }
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }

        List<Integer> slots = config.slots();
        if (slots.isEmpty()) {
            inventory.setItem(49, item);
        } else {
            for (int slot : slots) {
                inventory.setItem(slot, item);
            }
        }
    }

    private void placeDetailItem(UUID playerUuid, String name, List<ViolationEntry> violations) {
        GuiItemConfig config = plugin.getConfigManager().getGuiItem("anticheat-detail");
        int reportCount = plugin.getDatabaseManager().getReportCount(playerUuid);

        ItemStack item;
        if (config == null) {
            item = createFallbackHead(playerUuid, name, buildFallbackDetailLore(violations, reportCount));
        } else {
            item = config.createItem();
            if (item.getItemMeta() instanceof SkullMeta skullMeta) {
                skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(playerUuid));
                item.setItemMeta(skullMeta);
            }
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(replaceStatic(meta.getDisplayName(), name, reportCount));
                if (meta.getLore() != null) {
                    meta.setLore(buildDetailLore(meta.getLore(), violations, name, reportCount));
                }
                item.setItemMeta(meta);
            }
        }

        List<Integer> slots = config != null ? config.slots() : List.of();
        inventory.setItem(slots.isEmpty() ? INFO_SLOT : slots.get(0), item);
    }

    /** Expands config lore lines containing %check% into one line per violation. */
    private List<String> buildDetailLore(List<String> configLore, List<ViolationEntry> violations,
                                         String name, int reportCount) {
        List<String> lore = new ArrayList<>();
        for (String line : configLore) {
            if (line.contains("%check%")) {
                for (ViolationEntry violation : violations) {
                    lore.add(replaceViolation(line, violation, name, reportCount));
                }
            } else {
                lore.add(replaceStatic(line, name, reportCount));
            }
        }
        return lore;
    }

    private ItemStack createEntryItem(FlaggedPlayer entry, GuiItemConfig config) {
        int reportCount = entry.playerUuid() != null ? plugin.getDatabaseManager().getReportCount(entry.playerUuid()) : 0;
        if (config == null) {
            return createFallbackHead(entry.playerUuid(), entry.playerName(), buildFallbackEntryLore(entry, reportCount));
        }

        ItemStack item = config.createItem();
        if (item.getItemMeta() instanceof SkullMeta skullMeta && entry.playerUuid() != null) {
            skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(entry.playerUuid()));
            item.setItemMeta(skullMeta);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(replaceEntryPlaceholders(meta.getDisplayName(), entry, reportCount));
            if (meta.getLore() != null) {
                List<String> lore = new ArrayList<>();
                for (String line : meta.getLore()) {
                    lore.add(replaceEntryPlaceholders(line, entry, reportCount));
                }
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private String replaceEntryPlaceholders(String text, FlaggedPlayer entry, int reportCount) {
        String lastFlag = entry.lastFlagAt() != null ? TimeUtil.formatTimeAgo(entry.lastFlagAt()) : "";
        return text
            .replace("%player_name%", entry.playerName() != null ? entry.playerName() : "")
            .replace("%flag_count%", String.valueOf(entry.flagCount()))
            .replace("%last_flag%", lastFlag)
            .replace("%hours%", String.valueOf(plugin.getAnticheatService().getLookbackHours()))
            .replace("%report_count%", String.valueOf(reportCount));
    }

    private String replaceViolation(String line, ViolationEntry violation, String name, int reportCount) {
        String time = violation.createdAt() != null ? TimeUtil.formatTimeAgo(violation.createdAt()) : "";
        return replaceStatic(line, name, reportCount)
            .replace("%check%", nullSafe(violation.checkKey()))
            .replace("%detail%", nullSafe(violation.detail()))
            .replace("%server%", nullSafe(violation.server()))
            .replace("%time%", time);
    }

    private String replaceStatic(String text, String name, int reportCount) {
        return text
            .replace("%player_name%", name)
            .replace("%report_count%", String.valueOf(reportCount))
            .replace("%hours%", String.valueOf(plugin.getAnticheatService().getLookbackHours()));
    }

    private String replacePagePlaceholders(String text, int totalPages, int totalPlayers) {
        return text
            .replace("%page%", String.valueOf(page + 1))
            .replace("%total%", String.valueOf(Math.max(1, totalPages)))
            .replace("%total_players%", String.valueOf(totalPlayers));
    }

    private void placeInfoItem(String text) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(text);
            item.setItemMeta(meta);
        }
        inventory.setItem(INFO_SLOT, item);
    }

    private ItemStack createFallbackHead(UUID owner, String name, List<String> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            if (owner != null) {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
            }
            meta.setDisplayName("§c" + name);
            meta.setLore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    private List<String> buildFallbackEntryLore(FlaggedPlayer entry, int reportCount) {
        String lastFlag = entry.lastFlagAt() != null ? TimeUtil.formatTimeAgo(entry.lastFlagAt()) : "";
        List<String> lore = new ArrayList<>();
        lore.add("§7");
        lore.add("§eFlags: §c" + entry.flagCount());
        lore.add("§eLast flag: §f" + lastFlag);
        lore.add("§eReports: §c" + reportCount);
        return lore;
    }

    private List<String> buildFallbackDetailLore(List<ViolationEntry> violations, int reportCount) {
        List<String> lore = new ArrayList<>();
        lore.add("§7");
        lore.add("§eReports: §c" + reportCount);
        for (ViolationEntry violation : violations) {
            String time = violation.createdAt() != null ? TimeUtil.formatTimeAgo(violation.createdAt()) : "";
            lore.add("§8- §f" + nullSafe(violation.checkKey()) + " §7(" + nullSafe(violation.server()) + ", " + time + ")");
        }
        return lore;
    }

    private static String nullSafe(String value) {
        return value != null ? value : "";
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
