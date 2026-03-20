package de.derbenson.jobcore;

import java.util.UUID;

public interface PlayerDataStorage {

    void initialize() throws Exception;

    PlayerJobData load(UUID playerUuid);

    void save(UUID playerUuid, PlayerJobData data);

    void close();
}

