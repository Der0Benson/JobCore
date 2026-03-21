package de.derbenson.jobcore;

import java.util.Map;
import java.util.UUID;

public interface PlayerDataStorage {

    void initialize() throws Exception;

    PlayerJobData load(UUID playerUuid);

    Map<UUID, PlayerJobData> loadAll();

    void save(UUID playerUuid, PlayerJobData data);

    void close();
}

