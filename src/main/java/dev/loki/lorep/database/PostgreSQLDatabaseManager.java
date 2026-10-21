package dev.loki.lorep.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
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

public class PostgreSQLDatabaseManager implements DatabaseManager {
    
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final int poolSize;
    private final Logger logger;
    private HikariDataSource dataSource;
    
    public PostgreSQLDatabaseManager(String host, int port, String database, 
                                      String username, String password, int poolSize, Logger logger) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.poolSize = poolSize;
        this.logger = logger;
    }
    
    @Override
    public void initialize() {
        try {
            // Explicitly load PostgreSQL driver
            Class.forName("org.postgresql.Driver");
            
            HikariConfig config = new HikariConfig();
            config.setDriverClassName("org.postgresql.Driver");
            config.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + database);
            config.setUsername(username);
            config.setPassword(password);
            config.setMaximumPoolSize(poolSize);
            config.setMinimumIdle(2);
            config.setIdleTimeout(30000);
            config.setConnectionTimeout(10000);
            config.setPoolName("lorep-pool");
            
            dataSource = new HikariDataSource(config);
            createTables();
            logger.info("PostgreSQL database initialized: " + host + ":" + port + "/" + database);
        } catch (ClassNotFoundException e) {
            logger.log(Level.SEVERE, "PostgreSQL driver not found", e);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize PostgreSQL database", e);
        }
    }
    
    private void createTables() throws SQLException {
        String createTable = """
            CREATE TABLE IF NOT EXISTS reports (
                id SERIAL PRIMARY KEY,
                reporter_uuid TEXT NOT NULL,
                reporter_name TEXT NOT NULL,
                target_uuid TEXT NOT NULL,
                target_name TEXT NOT NULL,
                reason TEXT NOT NULL,
                image_url TEXT,
                status TEXT NOT NULL DEFAULT 'PENDING',
                created_at BIGINT NOT NULL
            )
            """;
        
        String createTargetIndex = "CREATE INDEX IF NOT EXISTS idx_target_uuid ON reports(target_uuid)";
        String createReporterTargetIndex = "CREATE INDEX IF NOT EXISTS idx_reporter_target ON reports(reporter_uuid, target_uuid)";
        // Server attribution column for reports created on this network node
        String addServerName = "ALTER TABLE reports ADD COLUMN IF NOT EXISTS server_name TEXT";
        
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTable);
            stmt.execute(createTargetIndex);
            stmt.execute(createReporterTargetIndex);
            stmt.execute(addServerName);
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("PostgreSQL connection pool closed");
        }
    }
    
    @Override
    public void saveReport(Report report) {
        String sql = "INSERT INTO reports (reporter_uuid, reporter_name, target_uuid, "
            + "target_name, reason, image_url, status, created_at, server_name) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
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
    public List<Report> getReportsForTarget(UUID targetUuid) {
        String sql = "SELECT * FROM reports WHERE target_uuid = ? ORDER BY created_at DESC";
        List<Report> reports = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
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
    public List<Report> getAllReports() {
        String sql = "SELECT * FROM reports ORDER BY created_at DESC";
        List<Report> reports = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
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
    public int getReportCount(UUID targetUuid) {
        String sql = "SELECT COUNT(*) FROM reports WHERE target_uuid = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
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
    public boolean hasReported(UUID reporterUuid, UUID targetUuid) {
        String sql = "SELECT COUNT(*) FROM reports WHERE reporter_uuid = ? AND target_uuid = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
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
    public List<Report> getReportsPaginated(int page, int pageSize) {
        String sql = "SELECT * FROM reports ORDER BY created_at DESC LIMIT ? OFFSET ?";
        List<Report> reports = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
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
    public int getTotalReportCount() {
        String sql = "SELECT COUNT(*) FROM reports";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
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
    public void updateReportStatus(int reportId, String status) {
        String sql = "UPDATE reports SET status = ? WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setInt(2, reportId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to update report status", e);
        }
    }

    @Override
    public void updateReportImage(int reportId, String imageUrl) {
        String sql = "UPDATE reports SET image_url = ? WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
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
    public Report getReportById(int id) {
        String sql = "SELECT * FROM reports WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
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
    public boolean isAnticheatAvailable() {
        String sql = "SELECT to_regclass('guard.violations')";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() && rs.getString(1) != null;
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to probe anticheat table", e);
            return false;
        }
    }

    @Override
    public List<CheckCount> getTopCheckCounts(UUID playerUuid, int lookbackHours, int limit) {
        String sql = "SELECT check_key, COUNT(*) AS cnt FROM guard.violations "
            + "WHERE player_uuid = ? AND created_at > NOW() - (? * INTERVAL '1 hour') "
            + "GROUP BY check_key ORDER BY cnt DESC LIMIT ?";
        List<CheckCount> counts = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, playerUuid);
            stmt.setInt(2, lookbackHours);
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
    public int getFlaggedPlayerCount(int lookbackHours) {
        String sql = "SELECT COUNT(*) FROM (SELECT player_uuid FROM guard.violations "
            + "WHERE created_at > NOW() - (? * INTERVAL '1 hour') GROUP BY player_uuid) AS flagged";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, lookbackHours);
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
    public List<FlaggedPlayer> getFlaggedPlayers(int lookbackHours, int limit, int offset) {
        String sql = "SELECT player_uuid, player_name, COUNT(*) AS cnt, MAX(created_at) AS last_at "
            + "FROM guard.violations WHERE created_at > NOW() - (? * INTERVAL '1 hour') "
            + "GROUP BY player_uuid, player_name ORDER BY cnt DESC, last_at DESC LIMIT ? OFFSET ?";
        List<FlaggedPlayer> players = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, lookbackHours);
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
    public List<ViolationEntry> getRecentViolations(UUID playerUuid, int lookbackHours, int limit) {
        String sql = "SELECT check_key, detail, server, weight, created_at FROM guard.violations "
            + "WHERE player_uuid = ? AND created_at > NOW() - (? * INTERVAL '1 hour') "
            + "ORDER BY created_at DESC LIMIT ?";
        List<ViolationEntry> violations = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, playerUuid);
            stmt.setInt(2, lookbackHours);
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
