package de.derbenson.jobcore.api;

import org.bukkit.Bukkit;

import java.util.Optional;

public final class JobCoreApiProvider {

    private JobCoreApiProvider() {
    }

    public static Optional<JobCoreApi> get() {
        return Optional.ofNullable(Bukkit.getServicesManager().load(JobCoreApi.class));
    }
}
