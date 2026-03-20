package de.derbenson.jobcore;

import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;

public final class PlacedBlockTracker {

    private final NamespacedKey playerPlacedKey;

    public PlacedBlockTracker(final JavaPlugin plugin) {
        this.playerPlacedKey = new NamespacedKey(plugin, "player_placed");
    }

    public void markAsPlayerPlaced(final Block block) {
        final Chunk chunk = block.getChunk();
        final PersistentDataContainer container = chunk.getPersistentDataContainer();
        final Set<Long> keys = readPlacedBlockKeys(container);
        keys.add(block.getBlockKey());
        writePlacedBlockKeys(container, keys);
    }

    public boolean isPlayerPlaced(final Block block) {
        return readPlacedBlockKeys(block.getChunk().getPersistentDataContainer()).contains(block.getBlockKey());
    }

    public void unmarkPlayerPlaced(final Block block) {
        final PersistentDataContainer container = block.getChunk().getPersistentDataContainer();
        final Set<Long> keys = readPlacedBlockKeys(container);
        if (!keys.remove(block.getBlockKey())) {
            return;
        }
        writePlacedBlockKeys(container, keys);
    }

    private Set<Long> readPlacedBlockKeys(final PersistentDataContainer container) {
        final long[] stored = container.get(playerPlacedKey, PersistentDataType.LONG_ARRAY);
        final Set<Long> keys = new HashSet<>();
        if (stored == null) {
            return keys;
        }

        for (final long value : stored) {
            keys.add(value);
        }
        return keys;
    }

    private void writePlacedBlockKeys(final PersistentDataContainer container, final Set<Long> keys) {
        if (keys.isEmpty()) {
            container.remove(playerPlacedKey);
            return;
        }

        final long[] values = new long[keys.size()];
        int index = 0;
        for (final long value : keys) {
            values[index++] = value;
        }
        container.set(playerPlacedKey, PersistentDataType.LONG_ARRAY, values);
    }
}

