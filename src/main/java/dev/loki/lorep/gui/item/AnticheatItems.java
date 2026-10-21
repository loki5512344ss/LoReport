package dev.loki.lorep.gui.item;

import dev.loki.lorep.config.ConfigManager;
import dev.loki.lorep.config.GuiItemConfig;
import dev.loki.lorep.database.DatabaseManager.FlaggedPlayer;
import dev.loki.lorep.database.DatabaseManager.ViolationEntry;
import dev.loki.lorep.util.TimeUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds the items of the anticheat tab: one head per flagged player in the
 * list view and the detailed head with the recent flags.
 *
 * <p>Every text comes from the config. When the config section is missing the
 * built-in fallback template is used, so a half configured server still shows
 * a readable head instead of an anonymous block.</p>
 */
public final class AnticheatItems {

    private final ConfigManager configManager;

    public AnticheatItems(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Builds the list entry of one flagged player.
     *
     * @param config the item template from the config, may be null
     * @param flagged the flagged player
     * @param reportCount the number of reports against the player
     * @return the head item
     */
    public ItemStack entry(GuiItemConfig config, FlaggedPlayer flagged, int reportCount) {
        GuiItemConfig template = config != null ? config : fallbackEntryTemplate();
        String name = applyEntryPlaceholders(template.displayName(), flagged, reportCount);
        List<String> lore = applyEntryPlaceholders(template.lore(), flagged, reportCount);

        ItemStack item = template.createItem(name, lore);
        PlayerHeads.applyProfile(item, flagged.playerUuid(), flagged.playerName());
        return item;
    }

    /**
     * Builds the detail item of one flagged player.
     *
     * @param config the item template from the config, may be null
     * @param playerUuid the player uuid
     * @param playerName the player name
     * @param violations the recent flags
     * @param reportCount the number of reports against the player
     * @return the head item
     */
    public ItemStack detail(GuiItemConfig config, UUID playerUuid, String playerName,
                            List<ViolationEntry> violations, int reportCount) {
        GuiItemConfig template = config != null ? config : fallbackDetailTemplate();
        String name = applyDetailPlaceholders(template.displayName(), playerName, reportCount);
        List<String> lore = buildDetailLore(template.lore(), violations, playerName, reportCount);

        ItemStack item = template.createItem(name, lore);
        PlayerHeads.applyProfile(item, playerUuid, playerName);
        return item;
    }

    /** Fallback template of the list entry. */
    private GuiItemConfig fallbackEntryTemplate() {
        return new GuiItemConfig(
            Material.PLAYER_HEAD,
            GuiItemConfig.NO_CUSTOM_MODEL,
            configManager.getMessageText("anticheat-entry-name"),
            List.of(
                configManager.getMessageText("anticheat-entry-flags"),
                configManager.getMessageText("anticheat-entry-last-flag"),
                configManager.getMessageText("anticheat-entry-reports")
            ),
            List.of(),
            false
        );
    }

    /** Fallback template of the detail item. */
    private GuiItemConfig fallbackDetailTemplate() {
        return new GuiItemConfig(
            Material.PLAYER_HEAD,
            GuiItemConfig.NO_CUSTOM_MODEL,
            configManager.getMessageText("anticheat-detail-name"),
            List.of(
                configManager.getMessageText("anticheat-detail-reports"),
                configManager.getMessageText("anticheat-detail-flag")
            ),
            List.of(),
            false
        );
    }

    /** Expands the placeholders of one list entry. */
    private String applyEntryPlaceholders(String text, FlaggedPlayer flagged, int reportCount) {
        if (text == null) {
            return null;
        }
        String lastFlag = flagged.lastFlagAt() != null ? TimeUtil.formatTimeAgo(flagged.lastFlagAt()) : "";
        return text
            .replace("%player_name%", nullSafe(flagged.playerName()))
            .replace("%flag_count%", String.valueOf(flagged.flagCount()))
            .replace("%last_flag%", lastFlag)
            .replace("%hours%", String.valueOf(configManager.getAnticheatLookbackHours()))
            .replace("%report_count%", String.valueOf(reportCount));
    }

    /** Expands the placeholders of the list entry lore. */
    private List<String> applyEntryPlaceholders(List<String> lore, FlaggedPlayer flagged, int reportCount) {
        List<String> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(applyEntryPlaceholders(line, flagged, reportCount));
        }
        return lines;
    }

    /** Expands the placeholders of the detail item. */
    private String applyDetailPlaceholders(String text, String playerName, int reportCount) {
        if (text == null) {
            return null;
        }
        return text
            .replace("%player_name%", nullSafe(playerName))
            .replace("%report_count%", String.valueOf(reportCount))
            .replace("%hours%", String.valueOf(configManager.getAnticheatLookbackHours()));
    }

    /**
     * Expands the detail lore: a line with the {@code %check%} placeholder is
     * repeated once per flag.
     */
    private List<String> buildDetailLore(List<String> configLore, List<ViolationEntry> violations,
                                         String playerName, int reportCount) {
        List<String> lore = new ArrayList<>();
        for (String line : configLore) {
            if (line.contains("%check%")) {
                for (ViolationEntry violation : violations) {
                    lore.add(applyViolationPlaceholders(line, violation, playerName, reportCount));
                }
            } else {
                lore.add(applyDetailPlaceholders(line, playerName, reportCount));
            }
        }
        return lore;
    }

    /** Expands the placeholders of a single flag line. */
    private String applyViolationPlaceholders(String line, ViolationEntry violation,
                                              String playerName, int reportCount) {
        String time = violation.createdAt() != null ? TimeUtil.formatTimeAgo(violation.createdAt()) : "";
        return applyDetailPlaceholders(line, playerName, reportCount)
            .replace("%check%", nullSafe(violation.checkKey()))
            .replace("%detail%", nullSafe(violation.detail()))
            .replace("%server%", nullSafe(violation.server()))
            .replace("%time%", time);
    }

    /** Null safe text. */
    private static String nullSafe(String value) {
        return value != null ? value : "";
    }
}
