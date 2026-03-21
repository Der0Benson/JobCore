package de.derbenson.jobcore;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class QuestNpcManager implements Listener {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final QuestMenuManager questMenuManager;
    private final NamespacedKey questNpcKey;
    private final File npcFile;
    private final Map<UUID, QuestNpcEntry> entriesByEntityUuid = new HashMap<>();
    private YamlConfiguration npcConfiguration;

    public QuestNpcManager(
            final JavaPlugin plugin,
            final ConfigManager configManager,
            final QuestMenuManager questMenuManager
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.questMenuManager = questMenuManager;
        this.questNpcKey = new NamespacedKey(plugin, "quest_npc");
        this.npcFile = new File(plugin.getDataFolder(), "quest-npcs.yml");
        load();
    }

    public void load() {
        npcConfiguration = YamlConfiguration.loadConfiguration(npcFile);
        entriesByEntityUuid.clear();
        boolean changed = false;

        final ConfigurationSection section = npcConfiguration.getConfigurationSection("npcs");
        if (section == null) {
            return;
        }

        for (final String entryId : section.getKeys(false)) {
            final ConfigurationSection npcSection = section.getConfigurationSection(entryId);
            if (npcSection == null) {
                continue;
            }

            final QuestNpcEntry entry = readEntry(entryId, npcSection);
            if (entry == null) {
                continue;
            }

            spawnOrRestore(entry).ifPresent(restored -> {
                entriesByEntityUuid.put(restored.entityUuid(), restored);
                writeEntry(restored);
                npcConfiguration.set("npcs." + entryId + ".entity-uuid", restored.entityUuid().toString());
            });
            changed = true;
        }

        if (changed) {
            save();
        }
    }

    public void save() {
        try {
            npcConfiguration.save(npcFile);
        } catch (final Exception exception) {
            plugin.getLogger().severe("Quest-NPC-Datei konnte nicht gespeichert werden: " + exception.getMessage());
        }
    }

    public boolean spawnQuestNpc(final Player player, final String rawName) {
        final String name = rawName == null || rawName.isBlank()
                ? configManager.getQuestConfiguration().getString("npc.default-name", "<gold>Questmeister")
                : rawName;
        final Location location = player.getLocation().getBlock().getLocation().add(0.5D, 0.0D, 0.5D);
        final QuestNpcEntry entry = new QuestNpcEntry(
                UUID.randomUUID().toString(),
                UUID.randomUUID(),
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch(),
                name
        );

        final Optional<QuestNpcEntry> spawned = spawnOrRestore(entry);
        if (spawned.isEmpty()) {
            return false;
        }

        writeEntry(spawned.get());
        save();
        entriesByEntityUuid.put(spawned.get().entityUuid(), spawned.get());
        return true;
    }

    public boolean removeNearestQuestNpc(final Player player) {
        QuestNpcEntry nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (final QuestNpcEntry entry : entriesByEntityUuid.values()) {
            final Entity entity = Bukkit.getEntity(entry.entityUuid());
            if (entity == null || !entity.isValid() || !entity.getWorld().equals(player.getWorld())) {
                continue;
            }

            final double distanceSquared = entity.getLocation().distanceSquared(player.getLocation());
            if (distanceSquared < nearestDistance) {
                nearestDistance = distanceSquared;
                nearest = entry;
            }
        }

        if (nearest == null || nearestDistance > 16.0D) {
            return false;
        }

        final Entity entity = Bukkit.getEntity(nearest.entityUuid());
        if (entity != null) {
            entity.remove();
        }

        entriesByEntityUuid.remove(nearest.entityUuid());
        npcConfiguration.set("npcs." + nearest.entryId(), null);
        save();
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractEntity(final PlayerInteractEntityEvent event) {
        final Entity entity = event.getRightClicked();
        if (!isQuestNpc(entity)) {
            return;
        }

        event.setCancelled(true);
        questMenuManager.openMenu(event.getPlayer(), 0);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(final EntityDamageEvent event) {
        if (isQuestNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    private Optional<QuestNpcEntry> spawnOrRestore(final QuestNpcEntry entry) {
        final World world = Bukkit.getWorld(entry.worldName());
        if (world == null) {
            plugin.getLogger().warning("Quest-NPC-Welt nicht gefunden: " + entry.worldName());
            return Optional.empty();
        }

        final Entity existing = Bukkit.getEntity(entry.entityUuid());
        Villager villager;
        UUID entityUuid = entry.entityUuid();

        if (existing instanceof Villager existingVillager && existingVillager.isValid()) {
            villager = existingVillager;
        } else {
            final Location location = new Location(world, entry.x(), entry.y(), entry.z(), entry.yaw(), entry.pitch());
            villager = (Villager) world.spawnEntity(location, EntityType.VILLAGER);
            entityUuid = villager.getUniqueId();
        }

        configureVillager(villager, entry.displayName());
        return Optional.of(new QuestNpcEntry(
                entry.entryId(),
                entityUuid,
                entry.worldName(),
                villager.getLocation().getX(),
                villager.getLocation().getY(),
                villager.getLocation().getZ(),
                villager.getLocation().getYaw(),
                villager.getLocation().getPitch(),
                entry.displayName()
        ));
    }

    private void configureVillager(final Villager villager, final String displayName) {
        villager.customName(configManager.deserialize(displayName));
        villager.setCustomNameVisible(true);
        villager.setProfession(Villager.Profession.LIBRARIAN);
        villager.setVillagerType(Villager.Type.PLAINS);
        villager.setVillagerLevel(5);
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setCollidable(false);
        villager.setSilent(true);
        villager.setGravity(false);
        villager.setCanPickupItems(false);
        villager.setRemoveWhenFarAway(false);
        villager.setPersistent(true);
        villager.getPersistentDataContainer().set(questNpcKey, PersistentDataType.BOOLEAN, true);
    }

    private boolean isQuestNpc(final Entity entity) {
        return entity.getPersistentDataContainer().has(questNpcKey, PersistentDataType.BOOLEAN)
                || entriesByEntityUuid.containsKey(entity.getUniqueId());
    }

    private QuestNpcEntry readEntry(final String entryId, final ConfigurationSection section) {
        final String worldName = section.getString("world", "");
        final String displayName = section.getString("name", "<gold>Questmeister");
        final String entityUuidRaw = section.getString("entity-uuid", "");
        if (worldName.isBlank()) {
            return null;
        }

        UUID entityUuid;
        try {
            entityUuid = entityUuidRaw.isBlank() ? UUID.randomUUID() : UUID.fromString(entityUuidRaw);
        } catch (final IllegalArgumentException exception) {
            entityUuid = UUID.randomUUID();
        }

        return new QuestNpcEntry(
                entryId,
                entityUuid,
                worldName,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw", 0.0D),
                (float) section.getDouble("pitch", 0.0D),
                displayName
        );
    }

    private void writeEntry(final QuestNpcEntry entry) {
        final String basePath = "npcs." + entry.entryId();
        npcConfiguration.set(basePath + ".entity-uuid", entry.entityUuid().toString());
        npcConfiguration.set(basePath + ".world", entry.worldName());
        npcConfiguration.set(basePath + ".x", entry.x());
        npcConfiguration.set(basePath + ".y", entry.y());
        npcConfiguration.set(basePath + ".z", entry.z());
        npcConfiguration.set(basePath + ".yaw", entry.yaw());
        npcConfiguration.set(basePath + ".pitch", entry.pitch());
        npcConfiguration.set(basePath + ".name", entry.displayName());
    }

    private record QuestNpcEntry(
            String entryId,
            UUID entityUuid,
            String worldName,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            String displayName
    ) {
    }
}
