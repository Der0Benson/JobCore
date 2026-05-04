package de.derbenson.jobcore;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
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
    private final String settingsTableName;
    private final String questsTableName;

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
        this.settingsTableName = normalizeTableName(configManager.getMySqlTablePrefix()) + "player_settings";
        this.questsTableName = normalizeTableName(configManager.getMySqlTablePrefix()) + "player_quests";
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
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS `" + settingsTableName + "` ("
                            + "`uuid` CHAR(36) NOT NULL,"
                            + "`bossbar_enabled` BOOLEAN NOT NULL DEFAULT TRUE,"
                            + "`player_name` VARCHAR(32) NOT NULL DEFAULT '',"
                            + "`quest_claims_total` INT NOT NULL DEFAULT 0,"
                            + "PRIMARY KEY (`uuid`)"
                            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS `" + questsTableName + "` ("
                            + "`uuid` CHAR(36) NOT NULL,"
                            + "`quest_id` VARCHAR(128) NOT NULL,"
                            + "`progress` INT NOT NULL DEFAULT 0,"
                            + "`accepted` BOOLEAN NOT NULL DEFAULT FALSE,"
                            + "`completed` BOOLEAN NOT NULL DEFAULT FALSE,"
                            + "`claimed` BOOLEAN NOT NULL DEFAULT FALSE,"
                            + "`cycle_key` VARCHAR(64) NOT NULL DEFAULT '',"
                            + "PRIMARY KEY (`uuid`, `quest_id`)"
                            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            ensureColumnExists(connection, questsTableName, "accepted", "BOOLEAN NOT NULL DEFAULT FALSE");
            ensureColumnExists(connection, questsTableName, "claimed", "BOOLEAN NOT NULL DEFAULT FALSE");
            ensureColumnExists(connection, questsTableName, "cycle_key", "VARCHAR(64) NOT NULL DEFAULT ''");
            ensureColumnExists(connection, settingsTableName, "player_name", "VARCHAR(32) NOT NULL DEFAULT ''");
            ensureColumnExists(connection, settingsTableName, "quest_claims_total", "INT NOT NULL DEFAULT 0");
        }
    }

    @Override
    public PlayerJobData load(final UUID playerUuid) {
        final PlayerJobData data = new PlayerJobData();

        try (
                Connection connection = openConnection();
                PreparedStatement settingsStatement = connection.prepareStatement(
                        "SELECT `bossbar_enabled`, `player_name`, `quest_claims_total` "
                                + "FROM `" + settingsTableName + "` WHERE `uuid` = ?"
                );
                PreparedStatement questStatement = connection.prepareStatement(
                        "SELECT `quest_id`, `progress`, `accepted`, `completed`, `claimed`, `cycle_key` "
                                + "FROM `" + questsTableName + "` WHERE `uuid` = ?"
                );
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT `job_id`, `xp`, `level`, `fractional_xp` FROM `" + tableName + "` WHERE `uuid` = ?"
                )
        ) {
            settingsStatement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = settingsStatement.executeQuery()) {
                if (resultSet.next()) {
                    data.setBossBarEnabled(resultSet.getBoolean("bossbar_enabled"));
                    data.setLastKnownName(resultSet.getString("player_name"));
                    data.setTotalQuestClaims(Math.max(0, resultSet.getInt("quest_claims_total")));
                }
            }

            questStatement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = questStatement.executeQuery()) {
                while (resultSet.next()) {
                    final String questId = resultSet.getString("quest_id");
                    final int progress = Math.max(0, resultSet.getInt("progress"));
                    final boolean accepted = resultSet.getBoolean("accepted");
                    final boolean completed = resultSet.getBoolean("completed");
                    final boolean claimed = resultSet.getBoolean("claimed");
                    final String cycleKey = resultSet.getString("cycle_key");
                    if (questId != null && !questId.isBlank()) {
                        data.setQuestProgress(
                                questId.toLowerCase(Locale.ROOT),
                                new PlayerQuestProgress(progress, accepted, completed, claimed, cycleKey)
                        );
                    }
                }
            }

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
            plugin.getLogger().severe("Fehler beim Laden der MySQL-Daten für " + playerUuid + ": " + exception.getMessage());
            throw new IllegalStateException("MySQL player data could not be loaded: " + playerUuid, exception);
        }

        return data;
    }

    @Override
    public Map<UUID, PlayerJobData> loadAll() {
        final Map<UUID, PlayerJobData> entries = new HashMap<>();

        try (
                Connection connection = openConnection();
                PreparedStatement settingsStatement = connection.prepareStatement(
                        "SELECT `uuid`, `bossbar_enabled`, `player_name`, `quest_claims_total` FROM `" + settingsTableName + "`"
                );
                PreparedStatement questStatement = connection.prepareStatement(
                        "SELECT `uuid`, `quest_id`, `progress`, `accepted`, `completed`, `claimed`, `cycle_key` FROM `" + questsTableName + "`"
                );
                PreparedStatement jobStatement = connection.prepareStatement(
                        "SELECT `uuid`, `job_id`, `xp`, `level`, `fractional_xp` FROM `" + tableName + "`"
                )
        ) {
            try (ResultSet resultSet = settingsStatement.executeQuery()) {
                while (resultSet.next()) {
                    final UUID playerUuid = UUID.fromString(resultSet.getString("uuid"));
                    final PlayerJobData data = entries.computeIfAbsent(playerUuid, ignored -> new PlayerJobData());
                    data.setBossBarEnabled(resultSet.getBoolean("bossbar_enabled"));
                    data.setLastKnownName(resultSet.getString("player_name"));
                    data.setTotalQuestClaims(Math.max(0, resultSet.getInt("quest_claims_total")));
                }
            }

            try (ResultSet resultSet = questStatement.executeQuery()) {
                while (resultSet.next()) {
                    final UUID playerUuid = UUID.fromString(resultSet.getString("uuid"));
                    final PlayerJobData data = entries.computeIfAbsent(playerUuid, ignored -> new PlayerJobData());
                    final String questId = resultSet.getString("quest_id");
                    if (questId == null || questId.isBlank()) {
                        continue;
                    }

                    data.setQuestProgress(
                            questId.toLowerCase(Locale.ROOT),
                            new PlayerQuestProgress(
                                    Math.max(0, resultSet.getInt("progress")),
                                    resultSet.getBoolean("accepted"),
                                    resultSet.getBoolean("completed"),
                                    resultSet.getBoolean("claimed"),
                                    resultSet.getString("cycle_key")
                            )
                    );
                }
            }

            try (ResultSet resultSet = jobStatement.executeQuery()) {
                while (resultSet.next()) {
                    final UUID playerUuid = UUID.fromString(resultSet.getString("uuid"));
                    final PlayerJobData data = entries.computeIfAbsent(playerUuid, ignored -> new PlayerJobData());
                    final String jobId = normalizeJobId(resultSet.getString("job_id"));
                    final long xp = Math.max(0L, resultSet.getLong("xp"));
                    final int level = Math.max(0, resultSet.getInt("level"));
                    final double fractionalXp = Math.max(0.0D, resultSet.getDouble("fractional_xp"));
                    data.setProgress(jobId, new JobProgress(xp, level, fractionalXp));
                }
            }
        } catch (final Exception exception) {
            plugin.getLogger().severe("Fehler beim Laden aller MySQL-Daten: " + exception.getMessage());
            throw new IllegalStateException("MySQL player data snapshot could not be loaded.", exception);
        }

        return entries;
    }

    @Override
    public void save(final UUID playerUuid, final PlayerJobData data) {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);

            try (
                    PreparedStatement settingsStatement = connection.prepareStatement(
                            "INSERT INTO `" + settingsTableName + "` (`uuid`, `bossbar_enabled`, `player_name`, `quest_claims_total`) "
                                    + "VALUES (?, ?, ?, ?) "
                                    + "ON DUPLICATE KEY UPDATE "
                                    + "`bossbar_enabled` = VALUES(`bossbar_enabled`), "
                                    + "`player_name` = VALUES(`player_name`), "
                                    + "`quest_claims_total` = VALUES(`quest_claims_total`)"
                    );
                    PreparedStatement deleteQuestStatement = connection.prepareStatement(
                            "DELETE FROM `" + questsTableName + "` WHERE `uuid` = ?"
                    );
                    PreparedStatement insertQuestStatement = connection.prepareStatement(
                            "INSERT INTO `" + questsTableName + "` "
                                    + "(`uuid`, `quest_id`, `progress`, `accepted`, `completed`, `claimed`, `cycle_key`) "
                                    + "VALUES (?, ?, ?, ?, ?, ?, ?)"
                    );
                    PreparedStatement deleteStatement = connection.prepareStatement(
                            "DELETE FROM `" + tableName + "` WHERE `uuid` = ?"
                    );
                    PreparedStatement insertStatement = connection.prepareStatement(
                            "INSERT INTO `" + tableName + "` (`uuid`, `job_id`, `xp`, `level`, `fractional_xp`) VALUES (?, ?, ?, ?, ?)"
                    )
            ) {
                settingsStatement.setString(1, playerUuid.toString());
                settingsStatement.setBoolean(2, data.isBossBarEnabled());
                settingsStatement.setString(3, data.getLastKnownName());
                settingsStatement.setInt(4, Math.max(0, data.getTotalQuestClaims()));
                settingsStatement.executeUpdate();

                deleteQuestStatement.setString(1, playerUuid.toString());
                deleteQuestStatement.executeUpdate();

                for (final Map.Entry<String, PlayerQuestProgress> entry : data.getQuestProgressById().entrySet()) {
                    final PlayerQuestProgress progress = entry.getValue();
                    insertQuestStatement.setString(1, playerUuid.toString());
                    insertQuestStatement.setString(2, entry.getKey().toLowerCase(Locale.ROOT));
                    insertQuestStatement.setInt(3, Math.max(0, progress.getProgress()));
                    insertQuestStatement.setBoolean(4, progress.isAccepted());
                    insertQuestStatement.setBoolean(5, progress.isCompleted());
                    insertQuestStatement.setBoolean(6, progress.isClaimed());
                    insertQuestStatement.setString(7, progress.getCycleKey());
                    insertQuestStatement.addBatch();
                }

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

                insertQuestStatement.executeBatch();
                insertStatement.executeBatch();
                connection.commit();
            } catch (final SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (final SQLException exception) {
            plugin.getLogger().severe("Fehler beim Speichern der MySQL-Daten für " + playerUuid + ": " + exception.getMessage());
            throw new IllegalStateException("MySQL player data could not be saved: " + playerUuid, exception);
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
                plugin.getLogger().warning("MySQL-Host enth\u00e4lt bereits einen Port. Verwende Host '" + hostPart + "' und Port " + parsedPort + ".");
                return new ConnectionTarget(hostPart, parsedPort);
            }
        }

        return new ConnectionTarget(trimmed, configuredPort);
    }

    private void ensureColumnExists(
            final Connection connection,
            final String tableName,
            final String columnName,
            final String definition
    ) throws SQLException {
        final DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getColumns(connection.getCatalog(), null, tableName, columnName)) {
            if (resultSet.next()) {
                return;
            }
        }

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE `" + tableName + "` ADD COLUMN `" + columnName + "` " + definition);
        }
    }

    private record ConnectionTarget(String host, int port) {
    }
}
