package de.derbenson.jobcore;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class DebugManager {

    private final ConfigManager configManager;
    private final Set<UUID> playerDebuggers = new HashSet<>();
    private boolean consoleDebugEnabled;

    public DebugManager(final ConfigManager configManager) {
        this.configManager = configManager;
    }

    public boolean setDebugEnabled(final CommandSender sender, final boolean enabled) {
        if (sender instanceof Player player) {
            if (enabled) {
                playerDebuggers.add(player.getUniqueId());
            } else {
                playerDebuggers.remove(player.getUniqueId());
            }
            return enabled;
        }

        consoleDebugEnabled = enabled;
        return consoleDebugEnabled;
    }

    public void sendXpDebug(final String message) {
        final Component component = configManager.deserialize("<dark_gray>[<gold>XP-Debug<dark_gray>] <gray>" + message);

        if (consoleDebugEnabled) {
            Bukkit.getConsoleSender().sendMessage(component);
        }

        for (final UUID playerUuid : Set.copyOf(playerDebuggers)) {
            final Player player = Bukkit.getPlayer(playerUuid);
            if (player == null || !player.isOnline()) {
                playerDebuggers.remove(playerUuid);
                continue;
            }
            player.sendMessage(component);
        }
    }
}
