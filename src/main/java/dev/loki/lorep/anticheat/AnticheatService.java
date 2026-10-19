package dev.loki.lorep.anticheat;

import dev.loki.lorep.config.ConfigManager;
import dev.loki.lorep.database.DatabaseManager;
import dev.loki.lorep.database.DatabaseManager.CheckCount;
import dev.loki.lorep.database.DatabaseManager.FlaggedPlayer;
import dev.loki.lorep.database.DatabaseManager.ViolationEntry;
import dev.loki.lorep.util.PaginationUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * ServGuard anticheat integration: aggregates the guard.violations rows stored in
 * the same database the plugin is connected to. The guard table is probed lazily
 * on first use; when it is missing the feature disables itself for this database
 * and logs a single INFO line.
 */
public class AnticheatService {

    /** The rendered summary appended to a reason is bounded so the 500-char limit stays sane. */
    private static final int MAX_SUMMARY_CHARS = 120;
    private static final int MAX_RECENT_VIOLATIONS = 20;
    private static final String CHECK_SEPARATOR = ", ";
    private static final String COUNT_SEPARATOR = " ×";

    private final DatabaseManager database;
    private final ConfigManager config;
    private final Logger logger;
    private Boolean tableAvailable;

    public AnticheatService(DatabaseManager database, ConfigManager config, Logger logger) {
        this.database = database;
        this.config = config;
        this.logger = logger;
    }

    /** Whether the anticheat integration is enabled in the config. */
    public boolean isEnabled() {
        return config.isAnticheatEnabled();
    }

    /** Lookback window in hours used by every anticheat query. */
    public int getLookbackHours() {
        return config.getAnticheatLookbackHours();
    }

    /**
     * Whether anticheat data can be queried: enabled and the guard table probe
     * succeeded. The probe runs once; a missing table logs one INFO line.
     */
    public synchronized boolean isAvailable() {
        if (!isEnabled()) {
            return false;
        }
        if (tableAvailable == null) {
            tableAvailable = database.isAnticheatAvailable();
            if (!tableAvailable) {
                logger.info("anticheat table not available - feature disabled for this database");
            }
        }
        return tableAvailable;
    }

    /**
     * Builds the summary suffix appended to a report reason, e.g.
     * "[AC] flags 72h: move.timer ×7, combat.reach ×5"; empty when there is nothing to show.
     */
    public String buildSummary(UUID playerUuid) {
        if (!isAvailable()) {
            return "";
        }

        int lookbackHours = getLookbackHours();
        List<CheckCount> topChecks =
            database.getTopCheckCounts(playerUuid, lookbackHours, config.getAnticheatSummaryMaxChecks());
        List<String> entries = collectEntries(topChecks, config.getAnticheatSummaryMaxFlags());
        if (entries.isEmpty()) {
            return "";
        }

        String summary = renderSummary(String.join(CHECK_SEPARATOR, entries), lookbackHours);
        while (summary.length() > MAX_SUMMARY_CHARS && entries.size() > 1) {
            entries.remove(entries.size() - 1);
            summary = renderSummary(String.join(CHECK_SEPARATOR, entries), lookbackHours);
        }
        if (summary.length() > MAX_SUMMARY_CHARS) {
            // A single oversized entry: hard-truncate and mark the cut with an ellipsis.
            summary = summary.substring(0, MAX_SUMMARY_CHARS - 1) + "…";
        }
        return summary;
    }

    /** Formats "check ×count" entries, bounded by the total flag cap from the config. */
    private List<String> collectEntries(List<CheckCount> topChecks, int maxFlags) {
        List<String> entries = new ArrayList<>();
        int cumulative = 0;
        for (CheckCount check : topChecks) {
            // The most frequent check is always included; the flag cap bounds the rest.
            if (!entries.isEmpty() && cumulative + check.count() > maxFlags) {
                break;
            }
            entries.add(check.checkKey() + COUNT_SEPARATOR + check.count());
            cumulative += check.count();
        }
        return entries;
    }

    private String renderSummary(String checks, int lookbackHours) {
        return config.getMessage("anticheat-summary", "%checks%", checks, "%hours%", String.valueOf(lookbackHours));
    }

    /**
     * Aggregated flagged players for one list page.
     *
     * @return the page, or null when anticheat data is unavailable
     */
    public List<FlaggedPlayer> getFlaggedPlayers(int page, int pageSize) {
        if (!isAvailable()) {
            return null;
        }
        int offset = PaginationUtil.calculateStartIndex(page, pageSize);
        return database.getFlaggedPlayers(getLookbackHours(), pageSize, offset);
    }

    /**
     * Distinct flagged player count in the lookback window.
     *
     * @return the count, or 0 when anticheat data is unavailable
     */
    public int getFlaggedPlayerCount() {
        if (!isAvailable()) {
            return 0;
        }
        return database.getFlaggedPlayerCount(getLookbackHours());
    }

    /**
     * Most recent violations for one player in the lookback window.
     *
     * @return the violations, empty when anticheat data is unavailable
     */
    public List<ViolationEntry> getRecentViolations(UUID playerUuid) {
        if (!isAvailable()) {
            return List.of();
        }
        return database.getRecentViolations(playerUuid, getLookbackHours(), MAX_RECENT_VIOLATIONS);
    }
}
