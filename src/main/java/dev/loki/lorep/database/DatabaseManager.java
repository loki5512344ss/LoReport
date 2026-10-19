package dev.loki.lorep.database;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DatabaseManager {
    
    void initialize();
    
    void close();
    
    void saveReport(Report report);
    
    List<Report> getReportsForTarget(UUID targetUuid);
    
    List<Report> getAllReports();
    
    int getReportCount(UUID targetUuid);
    
    boolean hasReported(UUID reporterUuid, UUID targetUuid);
    
    List<Report> getReportsPaginated(int page, int pageSize);

    int getTotalReportCount();

    void updateReportStatus(int reportId, String status);

    void updateReportImage(int reportId, String imageUrl);

    Report getReportById(int id);

    /**
     * Probes the ServGuard anticheat violations table in the same database.
     *
     * @return true when the table exists and is queryable
     */
    boolean isAnticheatAvailable();

    /**
     * Top anticheat check counts for one player inside the lookback window.
     *
     * @return most frequent checks first, empty when the player has no flags
     */
    List<CheckCount> getTopCheckCounts(UUID playerUuid, int lookbackHours, int limit);

    /**
     * Distinct flagged player count inside the lookback window.
     */
    int getFlaggedPlayerCount(int lookbackHours);

    /**
     * Aggregated flagged players inside the lookback window, most flags first.
     */
    List<FlaggedPlayer> getFlaggedPlayers(int lookbackHours, int limit, int offset);

    /**
     * Most recent anticheat violations for one player inside the lookback window.
     */
    List<ViolationEntry> getRecentViolations(UUID playerUuid, int lookbackHours, int limit);

    /** One aggregated check of the ServGuard guard.violations table. */
    record CheckCount(String checkKey, int count) {}

    /** One player aggregated over the ServGuard guard.violations table. */
    record FlaggedPlayer(UUID playerUuid, String playerName, int flagCount, Instant lastFlagAt) {}

    /** One raw ServGuard guard.violations row. */
    record ViolationEntry(String checkKey, String detail, String server, double weight, Instant createdAt) {}
}
