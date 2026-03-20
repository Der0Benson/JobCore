package de.deinname.customjobs;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class MySqlPlayerDataStorage implements PlayerDataStorage {

    private final JavaPlugin plugin;
    private final String legacyDefaultJobId;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final boolean useSsl;
    private final String tableName;

    public MySqlPlayerDataStorage(final JavaPlugin plugin, final String legacyDefaultJobId, final ConfigManager configManager) {
        this.plugin = plugin;
        this.legacyDefaultJobId = legacyDefaultJobId.toLowerCase(Locale.ROOT);
        final ConnectionTarget connectionTarget = parseConnectionTarget(configManager.getMySqlHost(), configManager.getMySqlPort());
        this.host = connectionTarget.host();
        this.port = connectionTarget.port();
        this.database = configManager.getMySqlDatabase();
        this.username = configManager.getMySqlUsername();
        this.password = configManager.getMySqlPassword();
        this.useSsl = configManager.isMySqlUseSsl();
        this.tableName = normalizeTableName(configManager.getMySqlTablePrefix()) + "player_jobs";
    }

    @Override
    public void initialize() throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS `" + tableName + "` ("
                            + "`uuid` CHAR(36) NOT NULL,"
                            + "`job_id` VARCHAR(64) NOT NULL,"
                            + "`xp` BIGINT NOT NULL DEFAULT 0,"
                            + "`level` INT NOT NULL DEFAULT 0,"
                            + "`fractional_xp` DOUBLE NOT NULL DEFAULT 0,"
                            + "PRIMARY KEY (`uuid`, `job_id`)"
                            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
        }
    }

    @Override
    public PlayerJobData load(final UUID playerUuid) {
        final PlayerJobData data = new PlayerJobData();

        try (
                Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT `job_id`, `xp`, `level`, `fractional_xp` FROM `" + tableName + "` WHERE `uuid` = ?"
                )
        ) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    final String jobId = normalizeJobId(resultSet.getString("job_id"));
                    final long xp = Math.max(0L, resultSet.getLong("xp"));
                    final int level = Math.max(0, resultSet.getInt("level"));
                    final double fractionalXp = Math.max(0.0D, resultSet.getDouble("fractional_xp"));
                    data.setProgress(jobId, new JobProgress(xp, level, fractionalXp));
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().severe("Fehler beim Laden der MySQL-Daten fuer " + playerUuid + ": " + exception.getMessage());
        }

        return data;
    }

    @Override
    public void save(final UUID playerUuid, final PlayerJobData data) {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);

            try (
                    PreparedStatement deleteStatement = connection.prepareStatement(
                            "DELETE FROM `" + tableName + "` WHERE `uuid` = ?"
                    );
                    PreparedStatement insertStatement = connection.prepareStatement(
                            "INSERT INTO `" + tableName + "` (`uuid`, `job_id`, `xp`, `level`, `fractional_xp`) VALUES (?, ?, ?, ?, ?)"
                    )
            ) {
                deleteStatement.setString(1, playerUuid.toString());
                deleteStatement.executeUpdate();

                for (final Map.Entry<String, JobProgress> entry : data.getProgressByJob().entrySet()) {
                    final JobProgress progress = entry.getValue();
                    insertStatement.setString(1, playerUuid.toString());
                    insertStatement.setString(2, normalizeJobId(entry.getKey()));
                    insertStatement.setLong(3, Math.max(0L, progress.getXp()));
                    insertStatement.setInt(4, Math.max(0, progress.getLevel()));
                    insertStatement.setDouble(5, Math.max(0.0D, progress.getFractionalXp()));
                    insertStatement.addBatch();
                }

                insertStatement.executeBatch();
                connection.commit();
            } catch (final SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (final SQLException exception) {
            plugin.getLogger().severe("Fehler beim Speichern der MySQL-Daten fuer " + playerUuid + ": " + exception.getMessage());
        }
    }

    @Override
    public void close() {
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(buildJdbcUrl(), username, password);
    }

    private String buildJdbcUrl() {
        return "jdbc:mysql://"
                + host + ":" + port + "/" + database
                + "?useSSL=" + useSsl
                + "&allowPublicKeyRetrieval=true"
                + "&characterEncoding=utf8"
                + "&useUnicode=true"
                + "&serverTimezone=UTC";
    }

    private String normalizeJobId(final String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return legacyDefaultJobId;
        }
        return jobId.toLowerCase(Locale.ROOT);
    }

    private String normalizeTableName(final String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "jobcore_";
        }
        final String normalized = prefix.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        return normalized.isBlank() ? "jobcore_" : normalized;
    }

    private ConnectionTarget parseConnectionTarget(final String rawHost, final int configuredPort) {
        final String fallbackHost = "127.0.0.1";
        final String trimmed = rawHost == null ? "" : rawHost.trim();
        if (trimmed.isBlank()) {
            return new ConnectionTarget(fallbackHost, configuredPort);
        }

        final int firstColon = trimmed.indexOf(':');
        final int lastColon = trimmed.lastIndexOf(':');
        if (firstColon > 0 && firstColon == lastColon) {
            final String hostPart = trimmed.substring(0, firstColon).trim();
            final String portPart = trimmed.substring(firstColon + 1).trim();
            if (!hostPart.isBlank() && portPart.matches("\\d+")) {
                final int parsedPort = Math.max(1, Integer.parseInt(portPart));
                plugin.getLogger().warning("MySQL-Host enthält bereits einen Port. Verwende Host '" + hostPart + "' und Port " + parsedPort + ".");
                return new ConnectionTarget(hostPart, parsedPort);
            }
        }

        return new ConnectionTarget(trimmed, configuredPort);
    }

    private record ConnectionTarget(String host, int port) {
    }
}
