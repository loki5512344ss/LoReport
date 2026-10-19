package dev.loki.lorep.database;

import java.time.Instant;
import java.util.UUID;

public record Report(
    int id,
    UUID reporterUuid,
    String reporterName,
    UUID targetUuid,
    String targetName,
    String reason,
    String imageUrl,
    String status,
    Instant createdAt,
    String serverName
) {
    /** Server attribution used when the origin network node is unknown. */
    public static final String UNKNOWN_SERVER = "unknown";

    public Report(UUID reporterUuid, String reporterName, UUID targetUuid, String targetName, String reason) {
        this(0, reporterUuid, reporterName, targetUuid, targetName, reason, null, "PENDING", Instant.now(), UNKNOWN_SERVER);
    }

    public Report(UUID reporterUuid, String reporterName, UUID targetUuid, String targetName, String reason, String imageUrl) {
        this(0, reporterUuid, reporterName, targetUuid, targetName, reason, imageUrl, "PENDING", Instant.now(), UNKNOWN_SERVER);
    }

    /** Convenience constructor for reports created on a known network node. */
    public Report(UUID reporterUuid, String reporterName, UUID targetUuid, String targetName, String reason,
                  String imageUrl, String serverName) {
        this(0, reporterUuid, reporterName, targetUuid, targetName, reason, imageUrl, "PENDING", Instant.now(), serverName);
    }
}
