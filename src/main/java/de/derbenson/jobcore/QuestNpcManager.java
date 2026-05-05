package de.derbenson.jobcore;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.trait.LookClose;
import net.citizensnpcs.trait.SkinTrait;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class QuestNpcManager implements Listener {

    private static final int UNKNOWN_NPC_ID = -1;
    private static final double REMOVE_RADIUS_BLOCKS = 16.0D;
    private static final double LOOK_CLOSE_RANGE_BLOCKS = 24.0D;
    private static final String DEFAULT_DISPLAY_NAME = "<gold><bold>Questmeister</bold></gold>";
    private static final String DEFAULT_SKIN_NAME = "Amifog";

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final QuestMenuManager questMenuManager;
    private final File npcFile;
    private final Map<Integer, QuestNpcEntry> entriesByNpcId = new HashMap<>();
    private YamlConfiguration npcConfiguration;

    public QuestNpcManager(
            final JavaPlugin plugin,
            final ConfigManager configManager,
            final QuestMenuManager questMenuManager
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.questMenuManager = questMenuManager;
        this.npcFile = new File(plugin.getDataFolder(), "quest-npcs.yml");
        load();
    }

    public void load() {
        npcConfiguration = YamlConfiguration.loadConfiguration(npcFile);
        entriesByNpcId.clear();
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

            final Optional<QuestNpcEntry> restored = spawnOrRestore(entry);
            if (restored.isEmpty()) {
                continue;
            }

            entriesByNpcId.put(restored.get().npcId(), restored.get());
            writeEntry(restored.get());
            changed = true;
        }

        if (changed) {
            save();
        }
    }

    public void save() {
        try {
            if (npcConfiguration != null) {
                npcConfiguration.save(npcFile);
            }
            CitizensAPI.getNPCRegistry().saveToStore();
        } catch (final Exception exception) {
            plugin.getLogger().severe("Quest NPC data could not be saved: " + exception.getMessage());
        }
    }

    public boolean spawnQuestNpc(final Player player, final String rawName) {
        final String name = rawName == null || rawName.isBlank()
                ? getConfiguredDisplayName()
                : rawName;
        final Location location = player.getLocation().getBlock().getLocation().add(0.5D, 0.0D, 0.5D);
        final QuestNpcEntry entry = new QuestNpcEntry(
                UUID.randomUUID().toString(),
                UNKNOWN_NPC_ID,
                null,
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch(),
                name,
                getConfiguredSkinName()
        );

        final Optional<QuestNpcEntry> spawned = spawnOrRestore(entry);
        if (spawned.isEmpty()) {
            return false;
        }

        writeEntry(spawned.get());
        save();
        entriesByNpcId.put(spawned.get().npcId(), spawned.get());
        return true;
    }

    public boolean removeNearestQuestNpc(final Player player) {
        QuestNpcEntry nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (final QuestNpcEntry entry : entriesByNpcId.values()) {
            final NPC npc = getNpc(entry);
            final Location location = getNpcLocation(npc, entry);
            if (location == null || !location.getWorld().equals(player.getWorld())) {
                continue;
            }

            final double distanceSquared = location.distanceSquared(player.getLocation());
            if (distanceSquared < nearestDistance) {
                nearestDistance = distanceSquared;
                nearest = entry;
            }
        }

        if (nearest == null || nearestDistance > (REMOVE_RADIUS_BLOCKS * REMOVE_RADIUS_BLOCKS)) {
            return false;
        }

        final NPC npc = getNpc(nearest);
        if (npc != null) {
            npc.destroy();
        }

        entriesByNpcId.remove(nearest.npcId());
        npcConfiguration.set("npcs." + nearest.entryId(), null);
        save();
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onNpcRightClick(final NPCRightClickEvent event) {
        if (!isQuestNpc(event.getNPC())) {
            return;
        }

        event.setCancelled(true);
        questMenuManager.openMenu(event.getClicker(), 0);
    }

    private Optional<QuestNpcEntry> spawnOrRestore(final QuestNpcEntry entry) {
        final World world = Bukkit.getWorld(entry.worldName());
        if (world == null) {
            plugin.getLogger().warning("Quest NPC world not found: " + entry.worldName());
            return Optional.empty();
        }

        removeLegacyVillager(entry.legacyEntityUuid());

        final Location location = new Location(world, entry.x(), entry.y(), entry.z(), entry.yaw(), entry.pitch());
        final NPCRegistry registry = CitizensAPI.getNPCRegistry();
        final String displayName = getEffectiveDisplayName(entry);
        NPC npc = entry.npcId() == UNKNOWN_NPC_ID ? null : registry.getById(entry.npcId());
        if (npc == null) {
            npc = registry.createNPC(EntityType.PLAYER, toCitizensName(displayName));
        } else {
            npc.setName(toCitizensName(displayName));
        }

        npc.setProtected(true);
        npc.setUseMinecraftAI(false);
        applySkin(npc, getEffectiveSkinName(entry));
        applyLookClose(npc);

        if (npc.isSpawned()) {
            npc.teleport(location, PlayerTeleportEvent.TeleportCause.PLUGIN);
        } else if (!npc.spawn(location)) {
            plugin.getLogger().warning("Quest NPC could not be spawned at " + entry.worldName()
                    + " " + entry.x() + "/" + entry.y() + "/" + entry.z() + ".");
            return Optional.empty();
        }

        return Optional.of(new QuestNpcEntry(
                entry.entryId(),
                npc.getId(),
                null,
                entry.worldName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch(),
                displayName,
                getEffectiveSkinName(entry)
        ));
    }

    private void applySkin(final NPC npc, final String skinName) {
        if (skinName.isBlank()) {
            return;
        }

        final SkinTrait skinTrait = npc.getOrAddTrait(SkinTrait.class);
        skinTrait.setFetchDefaultSkin(false);
        skinTrait.setShouldUpdateSkins(true);
        skinTrait.setSkinName(skinName, true);
    }

    private void applyLookClose(final NPC npc) {
        final LookClose lookClose = npc.getOrAddTrait(LookClose.class);
        lookClose.lookClose(true);
        lookClose.setRange(LOOK_CLOSE_RANGE_BLOCKS);
        lookClose.setHeadOnly(false);
        lookClose.setLinkedBody(true);
        lookClose.setPerPlayer(false);
        lookClose.setRandomLook(false);
        lookClose.setRandomlySwitchTargets(false);
        lookClose.setRealisticLooking(false);
        lookClose.setTargetNPCs(false);
    }

    private boolean isQuestNpc(final NPC npc) {
        return npc != null && entriesByNpcId.containsKey(npc.getId());
    }

    private NPC getNpc(final QuestNpcEntry entry) {
        if (entry == null || entry.npcId() == UNKNOWN_NPC_ID) {
            return null;
        }
        return CitizensAPI.getNPCRegistry().getById(entry.npcId());
    }

    private Location getNpcLocation(final NPC npc, final QuestNpcEntry entry) {
        if (npc != null && npc.isSpawned() && npc.getEntity() != null) {
            return npc.getEntity().getLocation();
        }
        if (npc != null && npc.getStoredLocation() != null) {
            return npc.getStoredLocation();
        }
        final World world = Bukkit.getWorld(entry.worldName());
        return world == null ? null : new Location(world, entry.x(), entry.y(), entry.z(), entry.yaw(), entry.pitch());
    }

    private void removeLegacyVillager(final UUID legacyEntityUuid) {
        if (legacyEntityUuid == null) {
            return;
        }

        final Entity entity = Bukkit.getEntity(legacyEntityUuid);
        if (entity != null) {
            entity.remove();
        }
    }

    private QuestNpcEntry readEntry(final String entryId, final ConfigurationSection section) {
        final String worldName = section.getString("world", "");
        if (worldName.isBlank()) {
            return null;
        }

        return new QuestNpcEntry(
                entryId,
                section.getInt("npc-id", UNKNOWN_NPC_ID),
                readLegacyEntityUuid(section.getString("entity-uuid", "")),
                worldName,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw", 0.0D),
                (float) section.getDouble("pitch", 0.0D),
                section.getString("name", DEFAULT_DISPLAY_NAME),
                section.getString("skin-name", "")
        );
    }

    private UUID readLegacyEntityUuid(final String rawUuid) {
        if (rawUuid == null || rawUuid.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(rawUuid);
        } catch (final IllegalArgumentException exception) {
            return null;
        }
    }

    private void writeEntry(final QuestNpcEntry entry) {
        final String basePath = "npcs." + entry.entryId();
        npcConfiguration.set(basePath + ".npc-id", entry.npcId());
        npcConfiguration.set(basePath + ".entity-uuid", null);
        npcConfiguration.set(basePath + ".world", entry.worldName());
        npcConfiguration.set(basePath + ".x", entry.x());
        npcConfiguration.set(basePath + ".y", entry.y());
        npcConfiguration.set(basePath + ".z", entry.z());
        npcConfiguration.set(basePath + ".yaw", entry.yaw());
        npcConfiguration.set(basePath + ".pitch", entry.pitch());
        npcConfiguration.set(basePath + ".name", entry.displayName());
        npcConfiguration.set(basePath + ".skin-name", entry.skinName());
    }

    private String getConfiguredSkinName() {
        final String skinName = configManager.getQuestConfiguration().getString("npc.skin-name", DEFAULT_SKIN_NAME).trim();
        return skinName.isBlank() || isBuiltInDefaultSkinName(skinName) ? DEFAULT_SKIN_NAME : skinName;
    }

    private String getConfiguredDisplayName() {
        final String displayName = configManager.getQuestConfiguration().getString("npc.default-name", DEFAULT_DISPLAY_NAME).trim();
        return displayName.isBlank() || isBuiltInDefaultDisplayName(displayName) ? DEFAULT_DISPLAY_NAME : displayName;
    }

    private String getEffectiveDisplayName(final QuestNpcEntry entry) {
        final String displayName = entry.displayName().trim();
        return displayName.isBlank() || isBuiltInDefaultDisplayName(displayName) ? getConfiguredDisplayName() : entry.displayName();
    }

    private boolean isBuiltInDefaultDisplayName(final String displayName) {
        return displayName.equals("Missionsmeister")
                || displayName.equals("<gold>Missionsmeister")
                || displayName.equals("Questmeister")
                || displayName.equals("<gold>Questmeister");
    }

    private String getEffectiveSkinName(final QuestNpcEntry entry) {
        final String skinName = entry.skinName().trim();
        return skinName.isBlank() || isBuiltInDefaultSkinName(skinName) ? getConfiguredSkinName() : entry.skinName();
    }

    private boolean isBuiltInDefaultSkinName(final String skinName) {
        return skinName.equalsIgnoreCase("Flooper69")
                || skinName.equalsIgnoreCase(DEFAULT_SKIN_NAME);
    }

    private String toCitizensName(final String displayName) {
        final Component component = configManager.deserialize(displayName);
        final String plain = PlainTextComponentSerializer.plainText().serialize(component);
        if (plain.isBlank()) {
            return LegacyComponentSerializer.legacySection().serialize(configManager.deserialize(DEFAULT_DISPLAY_NAME));
        }
        return LegacyComponentSerializer.legacySection().serialize(component);
    }

    private record QuestNpcEntry(
            String entryId,
            int npcId,
            UUID legacyEntityUuid,
            String worldName,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            String displayName,
            String skinName
    ) {
    }
}
