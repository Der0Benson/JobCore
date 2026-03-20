package de.derbenson.jobcore;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class InactivityTask extends BukkitRunnable {

    private final JavaPlugin plugin;
    private final BossBarManager bossBarManager;
    private final ConfigManager configManager;

    
    public InactivityTask(
            final JavaPlugin plugin,
            final BossBarManager bossBarManager,
            final ConfigManager configManager
    ) {
        this.plugin = plugin;
        this.bossBarManager = bossBarManager;
        this.configManager = configManager;
    }

    @Override
    public void run() {
        if (!plugin.isEnabled()) {
            cancel();
            return;
        }

        bossBarManager.hideInactiveBars(configManager.getBossBarInactivityMillis());
    }
}

