package dev.loki.lorep.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite-backed database. All methods are synchronized on this instance because
 * Folia region threads may invoke them concurrently.
 */
public class SQLiteDatabaseManager implements DatabaseManager {
    
    private final File dataFolder;
    private final String fileName;
    private final Logger logger;
    private Connection connection;
    
    public SQLiteDatabaseManager(File dataFolder, String fileName, Logger logger) {
        this.dataFolder = dataFolder;
        this.fileName = fileName;
        this.logger = logger;
    }
    
    @Override
    public synchronized void initialize() {
        try {
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            
            File dbFile = new File(dataFolder, fileName);
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            connection = DriverManager.getConnection(url);
            
            createTables();
            logger.info("SQLite database initialized: " + dbFile.getAbsolutePath());
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to initialize SQLite database", e);
        }
    }
    
    private void createTables() throws SQLException {
        String createTable = """
            CREATE TABLE IF NOT EXISTS reports (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                reporter_uuid TEXT NOT NULL,
                reporter_name TEXT NOT NULL,
                target_uuid TEXT NOT NULL,
                target_name TEXT NOT NULL,
                reason TEXT NOT NULL,
                image_url TEXT,
                status TEXT NOT NULL DEFAULT 'PENDING',
                created_at INTEGER NOT NULL
            )
            """;
        
        String createTargetIndex = "CREATE INDEX IF NOT EXISTS idx_target_uuid ON reports(target_uuid)";
        String createReporterTargetIndex = "CREATE INDEX IF NOT EXISTS idx_reporter_target ON reports(reporter_uuid, target_uuid)";
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTable);
            stmt.execute(createTargetIndex);
            stmt.execute(createReporterTargetIndex);
        }
        
        addServerNameColumn();
    }

    /**
     * SQLite has no ALTER TABLE ... ADD COLUMN IF NOT EXISTS, so the column
     * presence is checked via PRAGMA before altering the table.
     */
    private void addServerNameColumn() throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(reports)")) {
            while (rs.next()) {
                if ("server_name".equalsIgnoreCase(rs.getString("name"))) {
                    return;
                }
            }
        }

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("ALTER TABLE reports ADD COLUMN server_name TEXT");
        }
    }

    @Override
    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                logger.info("SQLite database connection closed");
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to close SQLite connection", e);
        }
    }
    
    @Override
    public synchronized void saveReport(Report report) {
        String sql = "INSERT INTO reports (reporter_uuid, reporter_name, target_uuid, "
            + "target_name, reason, image_url, status, created_at, server_name) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, report.reporterUuid().toString());
            stmt.setString(2, report.reporterName());
            stmt.setString(3, report.targetUuid().toString());
            stmt.setString(4, report.targetName());
            stmt.setString(5, report.reason());
            if (report.imageUrl() != null) {
                stmt.setString(6, report.imageUrl());
            } else {
                stmt.setNull(6, java.sql.Types.VARCHAR);
            }
            stmt.setString(7, report.status());
            stmt.setLong(8, report.createdAt().toEpochMilli());
            stmt.setString(9, report.serverName() != null ? report.serverName() : Report.UNKNOWN_SERVER);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to save report", e);
        }
    }
    
    @Override
    public synchronized List<Report> getReportsForTarget(UUID targetUuid) {
        String sql = "SELECT * FROM reports WHERE target_uuid = ? ORDER BY created_at DESC";
        List<Report> reports = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql);) {
            stmt.setString(1, targetUuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    reports.add(mapReport(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get reports for target", e);
        }

        return reports;
    }
    
    @Override
    public synchronized List<Report> getAllReports() {
        String sql = "SELECT * FROM reports ORDER BY created_at DESC";
        List<Report> reports = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                reports.add(mapReport(rs));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get all reports", e);
        }

        return reports;
    }
    
    @Override
    public synchronized int getReportCount(UUID targetUuid) {
        String sql = "SELECT COUNT(*) FROM reports WHERE target_uuid = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, targetUuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get report count", e);
        }

        return 0;
    }
    
    @Override
    public synchronized boolean hasReported(UUID reporterUuid, UUID targetUuid) {
        String sql = "SELECT COUNT(*) FROM reports WHERE reporter_uuid = ? AND target_uuid = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, reporterUuid.toString());
            stmt.setString(2, targetUuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to check if reported", e);
        }

        return false;
    }
    
    @Override
    public synchronized List<Report> getReportsPaginated(int page, int pageSize) {
        String sql = "SELECT * FROM reports ORDER BY created_at DESC LIMIT ? OFFSET ?";
        List<Report> reports = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, pageSize);
            stmt.setInt(2, page * pageSize);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    reports.add(mapReport(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get paginated reports", e);
        }

        return reports;
    }
    
    @Override
    public synchronized int getTotalReportCount() {
        String sql = "SELECT COUNT(*) FROM reports";

        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get total report count", e);
        }

        return 0;
    }
    
    private Report mapReport(ResultSet rs) throws SQLException {
        String imageUrl = rs.getString("image_url");
        if (rs.wasNull()) {
            imageUrl = null;
        }
        String serverName = rs.getString("server_name");
        if (rs.wasNull()) {
            serverName = Report.UNKNOWN_SERVER;
        }
        return new Report(
            rs.getInt("id"),
            UUID.fromString(rs.getString("reporter_uuid")),
            rs.getString("reporter_name"),
            UUID.fromString(rs.getString("target_uuid")),
            rs.getString("target_name"),
            rs.getString("reason"),
            imageUrl,
            rs.getString("status"),
            Instant.ofEpochMilli(rs.getLong("created_at")),
            serverName
        );
    }

    @Override
    public synchronized void updateReportStatus(int reportId, String status) {
        String sql = "UPDATE reports SET status = ? WHERE id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setInt(2, reportId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to update report status", e);
        }
    }

    @Override
    public synchronized void updateReportImage(int reportId, String imageUrl) {
        String sql = "UPDATE reports SET image_url = ? WHERE id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            if (imageUrl != null) {
                stmt.setString(1, imageUrl);
            } else {
                stmt.setNull(1, java.sql.Types.VARCHAR);
            }
            stmt.setInt(2, reportId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to update report image", e);
        }
    }

    @Override
    public synchronized Report getReportById(int id) {
        String sql = "SELECT * FROM reports WHERE id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapReport(rs);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get report by id", e);
        }

        return null;
    }

    @Override
    public synchronized boolean isAnticheatAvailable() {
        // ServGuard persists violations to PostgreSQL; guard.violations normally
        // does not exist here, the probe fails and the feature disables itself.
        String sql = "SELECT 1 FROM guard.violations LIMIT 1";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next();
        } catch (SQLException e) {
            logger.log(Level.FINE, "Anticheat table not available in SQLite database", e);
            return false;
        }
    }

    @Override
    public synchronized List<CheckCount> getTopCheckCounts(UUID playerUuid, int lookbackHours, int limit) {
        String sql = "SELECT check_key, COUNT(*) AS cnt FROM guard.violations "
            + "WHERE player_uuid = ? AND created_at > datetime('now', ?) "
            + "GROUP BY check_key ORDER BY cnt DESC LIMIT ?";
        List<CheckCount> counts = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, hoursClause(lookbackHours));
            stmt.setInt(3, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    counts.add(new CheckCount(rs.getString("check_key"), rs.getInt("cnt")));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get anticheat check counts", e);
        }

        return counts;
    }

    @Override
    public synchronized int getFlaggedPlayerCount(int lookbackHours) {
        String sql = "SELECT COUNT(*) FROM (SELECT player_uuid FROM guard.violations "
            + "WHERE created_at > datetime('now', ?) GROUP BY player_uuid) AS flagged";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, hoursClause(lookbackHours));
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to count flagged players", e);
        }

        return 0;
    }

    @Override
    public synchronized List<FlaggedPlayer> getFlaggedPlayers(int lookbackHours, int limit, int offset) {
        String sql = "SELECT player_uuid, player_name, COUNT(*) AS cnt, MAX(created_at) AS last_at "
            + "FROM guard.violations WHERE created_at > datetime('now', ?) "
            + "GROUP BY player_uuid, player_name ORDER BY cnt DESC, last_at DESC LIMIT ? OFFSET ?";
        List<FlaggedPlayer> players = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, hoursClause(lookbackHours));
            stmt.setInt(2, limit);
            stmt.setInt(3, offset);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID playerUuid = parsePlayerUuid(rs.getString("player_uuid"));
                    if (playerUuid == null) {
                        continue;
                    }
                    players.add(new FlaggedPlayer(
                        playerUuid,
                        rs.getString("player_name"),
                        rs.getInt("cnt"),
                        toInstant(rs.getTimestamp("last_at"))
                    ));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get flagged players", e);
        }

        return players;
    }

    @Override
    public synchronized List<ViolationEntry> getRecentViolations(UUID playerUuid, int lookbackHours, int limit) {
        String sql = "SELECT check_key, detail, server, weight, created_at FROM guard.violations "
            + "WHERE player_uuid = ? AND created_at > datetime('now', ?) "
            + "ORDER BY created_at DESC LIMIT ?";
        List<ViolationEntry> violations = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, hoursClause(lookbackHours));
            stmt.setInt(3, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    violations.add(new ViolationEntry(
                        rs.getString("check_key"),
                        rs.getString("detail"),
                        rs.getString("server"),
                        rs.getDouble("weight"),
                        toInstant(rs.getTimestamp("created_at"))
                    ));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get recent anticheat violations", e);
        }

        return violations;
    }

    private static String hoursClause(int lookbackHours) {
        return "-" + lookbackHours + " hours";
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : Instant.EPOCH;
    }

    /**
     * Parses the uuid of a guard row.
     *
     * <p>A malformed value must not break the whole anticheat list, such a row
     * is skipped and reported in the log.</p>
     *
     * @param raw the raw uuid
     * @return the uuid or null when the value is malformed
     */
    private UUID parsePlayerUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            logger.warning("Skipping anticheat row with malformed player uuid: " + raw);
            return null;
        }
    }
}
