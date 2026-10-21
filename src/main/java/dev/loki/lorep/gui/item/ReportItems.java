package dev.loki.lorep.gui.item;

import dev.loki.lorep.config.ConfigManager;
import dev.loki.lorep.config.GuiItemConfig;
import dev.loki.lorep.database.Report;
import dev.loki.lorep.util.TimeUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the items of the report tab: one head per report in the list view
 * and the detailed head of a single report.
 *
 * <p>Every text comes from the config. When the config section is missing the
 * built-in fallback template is used, so a half configured server still shows
 * a readable head instead of an anonymous block.</p>
 */
public final class ReportItems {

    private final ConfigManager configManager;

    public ReportItems(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Builds the list entry of one report.
     *
     * @param config the item template from the config, may be null
     * @param report the report
     * @param reportCount the number of reports against the reported player
     * @return the head item
     */
    public ItemStack entry(GuiItemConfig config, Report report, int reportCount) {
        GuiItemConfig template = config != null ? config : fallbackTemplate();
        String name = applyPlaceholders(template.displayName(), report, reportCount);
        List<String> lore = applyPlaceholders(template.lore(), report, reportCount);

        ItemStack item = template.createItem(name, lore);
        PlayerHeads.applyProfile(item, report.targetUuid(), report.targetName());
        return item;
    }

    /**
     * Builds the detail item of one report.
     *
     * @param config the item template from the config, may be null
     * @param report the report
     * @param reportCount the number of reports against the reported player
     * @return the head item
     */
    public ItemStack detail(GuiItemConfig config, Report report, int reportCount) {
        return entry(config, report, reportCount);
    }

    /** Fallback template of a report entry. */
    private GuiItemConfig fallbackTemplate() {
        return new GuiItemConfig(
            Material.PLAYER_HEAD,
            GuiItemConfig.NO_CUSTOM_MODEL,
            configManager.getMessageText("report-entry-name"),
            List.of(
                configManager.getMessageText("report-entry-reporter"),
                configManager.getMessageText("report-entry-reason"),
                configManager.getMessageText("report-entry-time"),
                configManager.getMessageText("report-entry-status"),
                configManager.getMessageText("report-entry-server"),
                configManager.getMessageText("report-entry-reports")
            ),
            List.of(),
            false
        );
    }

    /** Expands the placeholders of one report entry. */
    private String applyPlaceholders(String text, Report report, int reportCount) {
        if (text == null) {
            return null;
        }
        return text
            .replace("%target_name%", nullSafe(report.targetName()))
            .replace("%reporter_name%", nullSafe(report.reporterName()))
            .replace("%reason%", nullSafe(report.reason()))
            .replace("%time%", report.createdAt() != null ? TimeUtil.formatTimeAgo(report.createdAt()) : "")
            .replace("%status%", statusDisplay(report))
            .replace("%server%", report.serverName() != null ? report.serverName() : Report.UNKNOWN_SERVER)
            .replace("%report_count%", String.valueOf(reportCount));
    }

    /** Expands the placeholders of the report entry lore. */
    private List<String> applyPlaceholders(List<String> lore, Report report, int reportCount) {
        List<String> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(applyPlaceholders(line, report, reportCount));
        }
        return lines;
    }

    /** Coloured status label of a report. */
    private String statusDisplay(Report report) {
        String status = report.status() != null ? report.status() : "PENDING";
        return configManager.getMessageText("status-" + status.toLowerCase());
    }

    /** Null safe text. */
    private static String nullSafe(String value) {
        return value != null ? value : "";
    }
}
