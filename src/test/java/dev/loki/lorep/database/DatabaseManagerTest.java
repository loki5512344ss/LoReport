package dev.loki.lorep.database;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for DatabaseManager
 * **Feature: lorep-report-plugin**
 * **Validates: Requirements 1.1, 1.2, 2.1, 2.2, 2.3, 5.1**
 */
public class DatabaseManagerTest {
    
    private SQLiteDatabaseManager dbManager;
    private File tempDir;
    private static final Logger logger = Logger.getLogger("TestLogger");
    
    @BeforeEach
    @BeforeProperty
    void setUp() {
        tempDir = new File(System.getProperty("java.io.tmpdir"), "lorep-test-" + System.currentTimeMillis());
        tempDir.mkdirs();
        dbManager = new SQLiteDatabaseManager(tempDir, "test.db", logger);
        dbManager.initialize();
    }
    
    @AfterEach
    @AfterProperty
    void tearDown() {
        if (dbManager != null) {
            dbManager.close();
        }
        if (tempDir != null && tempDir.exists()) {
            for (File file : tempDir.listFiles()) {
                file.delete();
            }
            tempDir.delete();
        }
    }
    
    /**
     * **Property 1: Report Creation Integrity**
     * For any valid reporter UUID, target UUID, and reason string, creating a report
     * and then querying reports for that target SHALL return a list containing the
     * created report with matching fields.
     * **Validates: Requirements 1.1, 2.1**
     */
    @Property(tries = 100)
    void reportCreationIntegrity(
            @ForAll("uuids") UUID reporterUuid,
            @ForAll("uuids") UUID targetUuid,
            @ForAll("reasons") String reason
    ) {
        Assume.that(!reporterUuid.equals(targetUuid));
        
        Report report = new Report(
            reporterUuid, "Reporter", targetUuid, "Target", reason
        );
        
        dbManager.saveReport(report);
        List<Report> reports = dbManager.getReportsForTarget(targetUuid);
        
        assertTrue(reports.stream().anyMatch(r ->
            r.reporterUuid().equals(reporterUuid) &&
            r.targetUuid().equals(targetUuid) &&
            r.reason().equals(reason)
        ), "Created report should be retrievable by target UUID");
    }
    
    /**
     * **Property 2: Duplicate Report Prevention**
     * For any reporter-target pair, if a report already exists from that reporter
     * to that target, hasReported SHALL return true.
     * **Validates: Requirements 1.2**
     */
    @Property(tries = 100)
    void duplicateReportPrevention(
            @ForAll("uuids") UUID reporterUuid,
            @ForAll("uuids") UUID targetUuid
    ) {
        Assume.that(!reporterUuid.equals(targetUuid));
        
        assertFalse(dbManager.hasReported(reporterUuid, targetUuid),
            "Should not have reported before creating report");
        
        Report report = new Report(
            reporterUuid, "Reporter", targetUuid, "Target", "Test reason"
        );
        dbManager.saveReport(report);
        
        assertTrue(dbManager.hasReported(reporterUuid, targetUuid),
            "Should detect existing report from same reporter to same target");
    }

    /**
     * **Property 3: Report Persistence Round-Trip**
     * For any set of reports saved to the database, reloading the database
     * SHALL return the same reports with identical data.
     * **Validates: Requirements 2.2**
     */
    @Property(tries = 50)
    void reportPersistenceRoundTrip(
            @ForAll("uuids") UUID reporterUuid,
            @ForAll("uuids") UUID targetUuid,
            @ForAll("reasons") String reason
    ) {
        Assume.that(!reporterUuid.equals(targetUuid));
        
        String reporterName = "Reporter_" + reporterUuid.toString().substring(0, 8);
        String targetName = "Target_" + targetUuid.toString().substring(0, 8);
        Instant createdAt = Instant.now();
        
        Report original = new Report(0, reporterUuid, reporterName, targetUuid, targetName, reason, null, "PENDING", createdAt,
            Report.UNKNOWN_SERVER);
        dbManager.saveReport(original);
        
        // Close and reopen database
        dbManager.close();
        dbManager = new SQLiteDatabaseManager(tempDir, "test.db", logger);
        dbManager.initialize();
        
        List<Report> loaded = dbManager.getReportsForTarget(targetUuid);
        
        assertTrue(loaded.stream().anyMatch(r ->
            r.reporterUuid().equals(reporterUuid) &&
            r.reporterName().equals(reporterName) &&
            r.targetUuid().equals(targetUuid) &&
            r.targetName().equals(targetName) &&
            r.reason().equals(reason)
        ), "Report should persist across database reload");
    }
    
    /**
     * **Property 4: Report Ordering**
     * For any collection of reports with different timestamps, querying all reports
     * SHALL return them sorted by creation timestamp in descending order (newest first).
     * **Validates: Requirements 2.3**
     */
    @Property(tries = 50)
    void reportOrdering(@ForAll("reportCounts") int count) {
        UUID targetUuid = UUID.randomUUID();
        
        for (int i = 0; i < count; i++) {
            Instant reportTime = Instant.now().minusSeconds(count - i);
            Report report = new Report(
                0, UUID.randomUUID(), "Reporter" + i, targetUuid, "Target",
                "Reason " + i, null, "PENDING", reportTime, Report.UNKNOWN_SERVER
            );
            dbManager.saveReport(report);
        }
        
        List<Report> reports = dbManager.getAllReports();
        
        for (int i = 0; i < reports.size() - 1; i++) {
            assertTrue(
                reports.get(i).createdAt().compareTo(reports.get(i + 1).createdAt()) >= 0,
                "Reports should be sorted by timestamp descending"
            );
        }
    }
    
    /**
     * **Property 7: Report Count Accuracy**
     * For any target UUID, the report count returned by getReportCount SHALL equal
     * the actual number of reports in the database for that target.
     * **Validates: Requirements 5.1**
     */
    @Property(tries = 100)
    void reportCountAccuracy(@ForAll("reportCounts") int count) {
        UUID targetUuid = UUID.randomUUID();
        
        for (int i = 0; i < count; i++) {
            Report report = new Report(
                UUID.randomUUID(), "Reporter" + i, targetUuid, "Target",
                "Reason " + i
            );
            dbManager.saveReport(report);
        }
        
        int reportedCount = dbManager.getReportCount(targetUuid);
        List<Report> actualReports = dbManager.getReportsForTarget(targetUuid);
        
        assertEquals(count, reportedCount, "getReportCount should match actual count");
        assertEquals(count, actualReports.size(), "List size should match count");
    }
    
    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }
    
    @Provide
    Arbitrary<String> reasons() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .ofMinLength(1)
            .ofMaxLength(100);
    }
    
    @Provide
    Arbitrary<Integer> reportCounts() {
        return Arbitraries.integers().between(1, 10);
    }
    
    @Test
    void testEmptyDatabase() {
        UUID randomUuid = UUID.randomUUID();
        
        assertEquals(0, dbManager.getReportCount(randomUuid));
        assertTrue(dbManager.getReportsForTarget(randomUuid).isEmpty());
        assertFalse(dbManager.hasReported(randomUuid, UUID.randomUUID()));
    }

    /**
     * **Property: Server Attribution Persistence**
     * A report saved with a known server name SHALL be retrievable with the same
     * server name; reports created without one SHALL default to "unknown".
     */
    @Test
    void serverNameRoundTrip() {
        UUID targetUuid = UUID.randomUUID();
        Report report = new Report(UUID.randomUUID(), "Reporter", targetUuid, "Target", "Test reason", null, "games");
        dbManager.saveReport(report);

        List<Report> loaded = dbManager.getReportsForTarget(targetUuid);
        assertEquals(1, loaded.size());
        assertEquals("games", loaded.get(0).serverName());

        UUID defaultTarget = UUID.randomUUID();
        dbManager.saveReport(new Report(UUID.randomUUID(), "Reporter", defaultTarget, "Target", "Test reason"));
        assertEquals(Report.UNKNOWN_SERVER, dbManager.getReportsForTarget(defaultTarget).get(0).serverName());
    }

    /**
     * **Property: Anticheat Probe Safety**
     * A plain SQLite database has no guard.violations table, so the anticheat
     * availability probe SHALL report false instead of throwing.
     */
    @Test
    void anticheatTableUnavailableOnSqlite() {
        assertFalse(dbManager.isAnticheatAvailable(),
            "guard.violations should not exist in a plain SQLite database");
    }
}
