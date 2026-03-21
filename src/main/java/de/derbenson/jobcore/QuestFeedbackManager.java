package de.derbenson.jobcore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

public final class QuestFeedbackManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final Map<QuestPeriod, NamespacedKey> toastKeys = new EnumMap<>(QuestPeriod.class);

    public QuestFeedbackManager(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        initializeAdvancements();
    }

    public void handleQuestCompleted(final Player player, final Quest quest) {
        showCompletionTitle(player, quest);
        showCompletionToast(player, quest);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.75F, 1.1F);
    }

    public void playClaimAnimation(final Player player, final Quest quest, final int grantedXp) {
        final Component actionBar = withoutItalic(configManager.deserialize(
                quest.period().getColorPrefix() + quest.period().getDisplayName()
                        + " <gray>·</gray> <white>" + quest.displayName() + "</white> <gray>(+"
                        + grantedXp + " " + configManager.getJobDisplayName(quest.job()) + "-XP)</gray>"
        ));
        player.sendActionBar(actionBar);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.25F);

        final Particle.DustOptions dustOptions = new Particle.DustOptions(colorForPeriod(quest.period()), 1.4F);
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (!player.isOnline() || tick >= 16) {
                    cancel();
                    return;
                }

                final double radius = 0.45D + (tick * 0.05D);
                final double y = 0.25D + (tick * 0.04D);
                for (int i = 0; i < 12; i++) {
                    final double angle = ((Math.PI * 2.0D) / 12.0D) * i + (tick * 0.22D);
                    final double x = Math.cos(angle) * radius;
                    final double z = Math.sin(angle) * radius;
                    player.getWorld().spawnParticle(
                            Particle.DUST,
                            player.getLocation().add(x, y, z),
                            1,
                            0.0D,
                            0.0D,
                            0.0D,
                            0.0D,
                            dustOptions
                    );
                    player.getWorld().spawnParticle(
                            Particle.END_ROD,
                            player.getLocation().add(x * 0.55D, y + 0.2D, z * 0.55D),
                            1,
                            0.0D,
                            0.0D,
                            0.0D,
                            0.0D
                    );
                }

                if (tick == 4 || tick == 10) {
                    player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.65F, 1.3F + (tick * 0.02F));
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void initializeAdvancements() {
        for (final QuestPeriod period : QuestPeriod.values()) {
            final NamespacedKey key = new NamespacedKey(plugin, "quest_toast_" + period.getId());
            toastKeys.put(period, key);
            if (Bukkit.getAdvancement(key) != null) {
                continue;
            }

            Bukkit.getUnsafe().loadAdvancement(key, buildAdvancementJson(period));
        }
    }

    private void showCompletionTitle(final Player player, final Quest quest) {
        final Component title = withoutItalic(configManager.deserialize(
                quest.period().getColorPrefix() + quest.period().getDisplayName() + " abgeschlossen"
        ));
        final Component subtitle = withoutItalic(configManager.deserialize(
                "<white>" + quest.displayName() + "</white>"
        ));
        player.showTitle(Title.title(
                title,
                subtitle,
                Title.Times.times(Duration.ofMillis(150), Duration.ofSeconds(2), Duration.ofMillis(500))
        ));
    }

    private void showCompletionToast(final Player player, final Quest quest) {
        final NamespacedKey key = toastKeys.get(quest.period());
        if (key == null) {
            return;
        }

        final Advancement advancement = Bukkit.getAdvancement(key);
        if (advancement == null) {
            return;
        }

        final AdvancementProgress progress = player.getAdvancementProgress(advancement);
        progress.revokeCriteria("complete");
        progress.awardCriteria("complete");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            player.getAdvancementProgress(advancement).revokeCriteria("complete");
        }, 40L);
    }

    private String buildAdvancementJson(final QuestPeriod period) {
        final String icon = materialKey(period.getBadgeMaterial());
        final String title = switch (period) {
            case DAILY -> "Daily Mission";
            case WEEKLY -> "Weekly Mission";
            case MONTHLY -> "Monatliche Mission";
        };
        final String description = switch (period) {
            case DAILY -> "Mission abgeschlossen";
            case WEEKLY -> "Mission abgeschlossen";
            case MONTHLY -> "Mission abgeschlossen";
        };
        final String frame = switch (period) {
            case DAILY -> "task";
            case WEEKLY -> "goal";
            case MONTHLY -> "challenge";
        };

        return """
                {
                  "display": {
                    "icon": { "id": "%s" },
                    "title": { "text": "%s" },
                    "description": { "text": "%s" },
                    "frame": "%s",
                    "show_toast": true,
                    "announce_to_chat": false,
                    "hidden": true
                  },
                  "criteria": {
                    "complete": {
                      "trigger": "minecraft:impossible"
                    }
                  }
                }
                """.formatted(icon, title, description, frame);
    }

    private String materialKey(final Material material) {
        return material.getKey().asString();
    }

    private Color colorForPeriod(final QuestPeriod period) {
        return switch (period) {
            case DAILY -> Color.fromRGB(85, 215, 255);
            case WEEKLY -> Color.fromRGB(255, 188, 64);
            case MONTHLY -> Color.fromRGB(194, 112, 255);
        };
    }

    private Component withoutItalic(final Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}

