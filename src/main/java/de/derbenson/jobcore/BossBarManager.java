package de.derbenson.jobcore;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BossBarManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private final Map<UUID, Long> lastActivity = new HashMap<>();

    
    public BossBarManager(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    
    public void showOrUpdate(
            final Player player,
            final Job job,
            final int level,
            final long currentXp,
            final long neededXp,
            final int xpGained
    ) {
        final UUID playerUuid = player.getUniqueId();
        final BossBar bossBar = bossBars.computeIfAbsent(playerUuid, ignored ->
                BossBar.bossBar(Component.empty(), 0.0F, configManager.getJobBossBarColor(job), BossBar.Overlay.PROGRESS));

        bossBar.color(configManager.getJobBossBarColor(job));
        bossBar.name(buildTitle(job, level, currentXp, neededXp, xpGained));
        bossBar.progress(calculateProgress(currentXp, neededXp));
        bossBar.overlay(BossBar.Overlay.PROGRESS);

        player.showBossBar(bossBar);
        lastActivity.put(playerUuid, System.currentTimeMillis());
    }

    
    public void hide(final Player player) {
        final UUID playerUuid = player.getUniqueId();
        final BossBar bossBar = bossBars.remove(playerUuid);
        lastActivity.remove(playerUuid);

        if (bossBar == null) {
            return;
        }

        player.hideBossBar(bossBar);
    }

    
    public void hideInactiveBars(final long inactivityMillis) {
        final long now = System.currentTimeMillis();

        for (final UUID playerUuid : lastActivity.keySet().toArray(UUID[]::new)) {
            final long lastSeen = lastActivity.getOrDefault(playerUuid, 0L);
            if (now - lastSeen < inactivityMillis) {
                continue;
            }

            final Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                hide(player);
                continue;
            }

            bossBars.remove(playerUuid);
            lastActivity.remove(playerUuid);
        }
    }

    
    public void hideAll() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            hide(player);
        }
        bossBars.clear();
        lastActivity.clear();
    }

    
    public void handleLevelUp(final Player player, final Job job) {
        final BossBar bossBar = bossBars.get(player.getUniqueId());
        if (bossBar != null) {
            bossBar.color(BossBar.Color.YELLOW);
            new BukkitRunnable() {
                @Override
                public void run() {
                    final BossBar currentBar = bossBars.get(player.getUniqueId());
                    if (currentBar != null) {
                        currentBar.color(configManager.getJobBossBarColor(job));
                    }
                }
            }.runTaskLater(plugin, 20L);
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
    }

    private Component buildTitle(
            final Job job,
            final int level,
            final long currentXp,
            final long neededXp,
            final int xpGained
    ) {
        final String xpText = neededXp <= 0L ? "MAX" : String.valueOf(currentXp);
        final String neededText = neededXp <= 0L ? "MAX" : String.valueOf(neededXp);
        return configManager.deserialize(
                configManager.getBossBarTitleTemplate(),
                Map.of(
                        "job", configManager.getJobDisplayName(job),
                        "level", String.valueOf(level),
                        "xp", xpText,
                        "needed", neededText,
                        "xpGained", String.valueOf(xpGained)
                )
        );
    }

    private float calculateProgress(final long currentXp, final long neededXp) {
        if (neededXp <= 0L) {
            return 1.0F;
        }

        final float progress = (float) currentXp / (float) neededXp;
        return Math.max(0.0F, Math.min(1.0F, progress));
    }
}

