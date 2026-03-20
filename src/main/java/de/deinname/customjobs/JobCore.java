package de.deinname.customjobs;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class JobCore extends JavaPlugin implements CommandExecutor, TabCompleter {

    private ConfigManager configManager;
    private PlayerDataStorage playerDataStorage;
    private PlayerDataManager playerDataManager;
    private BossBarManager bossBarManager;
    private JobManager jobManager;
    private LevelMenuManager levelMenuManager;
    private InactivityTask inactivityTask;
    private BukkitTask autosaveTask;

    @Override
    public void onEnable() {
        try {
            this.configManager = new ConfigManager(this);
            this.playerDataStorage = createPlayerDataStorage();
            playerDataStorage.initialize();
            this.playerDataManager = new PlayerDataManager(playerDataStorage);
            this.bossBarManager = new BossBarManager(this, configManager);
            this.jobManager = new JobManager(this, configManager, playerDataManager, bossBarManager);
            this.levelMenuManager = new LevelMenuManager(configManager, jobManager);

            registerCommands();
            registerListeners();
            startTasks();
            loadAlreadyOnlinePlayers();

            getLogger().info("JobCore wurde erfolgreich aktiviert.");
        } catch (final Exception exception) {
            getLogger().severe("JobCore konnte nicht aktiviert werden: " + exception.getMessage());
            exception.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }

        if (inactivityTask != null) {
            inactivityTask.cancel();
        }

        if (bossBarManager != null) {
            bossBarManager.hideAll();
        }

        if (playerDataManager != null) {
            playerDataManager.saveAll();
            playerDataManager.close();
        }

        getLogger().info("JobCore wurde deaktiviert.");
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (command.getName().equalsIgnoreCase("level")) {
            return handleLevelCommand(sender);
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            return handleInfoCommand(sender);
        }

        if (args[0].equalsIgnoreCase("level")) {
            return handleLevelCommand(sender);
        }

        if (args[0].equalsIgnoreCase("reload")) {
            return handleReloadCommand(sender);
        }

        sender.sendMessage(configManager.getMessage("messages.unknown-subcommand"));
        return true;
    }

    @Override
    public List<String> onTabComplete(
            final CommandSender sender,
            final Command command,
            final String alias,
            final String[] args
    ) {
        if (command.getName().equalsIgnoreCase("level")) {
            return List.of();
        }

        if (args.length != 1) {
            return List.of();
        }

        final List<String> completions = new ArrayList<>();
        final List<String> options = new ArrayList<>();

        if (sender.hasPermission("customjobs.info")) {
            options.add("info");
        }
        if (sender.hasPermission("customjobs.level")) {
            options.add("level");
        }
        if (sender.hasPermission("customjobs.admin")) {
            options.add("reload");
        }

        StringUtil.copyPartialMatches(args[0], options, completions);
        return completions;
    }

    private boolean handleInfoCommand(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage(
                    "messages.console-info",
                    Map.of("version", getPluginMeta().getVersion())
            ));
            return true;
        }

        if (!player.hasPermission("customjobs.info")) {
            player.sendMessage(configManager.getMessage("messages.no-permission"));
            return true;
        }

        player.sendMessage(configManager.getMessage("messages.info-header"));
        for (final Job job : jobManager.getJobs()) {
            final JobProgress progress = jobManager.getProgress(player.getUniqueId(), job);
            final long neededXp = jobManager.getXpForNextLevel(progress.getLevel());
            player.sendMessage(configManager.getMessage(
                    "messages.info-entry",
                    Map.of(
                            "job", configManager.getJobDisplayName(job),
                            "level", String.valueOf(progress.getLevel()),
                            "xp", String.valueOf(progress.getXp()),
                            "needed", String.valueOf(neededXp)
                    )
            ));
        }
        return true;
    }

    private boolean handleLevelCommand(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("messages.player-only"));
            return true;
        }

        if (!player.hasPermission("customjobs.level")) {
            player.sendMessage(configManager.getMessage("messages.no-permission"));
            return true;
        }

        levelMenuManager.openOverview(player);
        return true;
    }

    private boolean handleReloadCommand(final CommandSender sender) {
        if (!sender.hasPermission("customjobs.admin")) {
            sender.sendMessage(configManager.getMessage("messages.no-permission"));
            return true;
        }

        try {
            final String previousStorageSignature = configManager.getStorageSignature();
            configManager.reload();
            jobManager.reload();
            sender.sendMessage(configManager.getMessage("messages.reloaded"));
            if (!previousStorageSignature.equals(configManager.getStorageSignature())) {
                sender.sendMessage(configManager.getMessage("messages.storage-restart-required"));
            }
        } catch (final Exception exception) {
            getLogger().severe("Fehler beim Reload von JobCore: " + exception.getMessage());
            exception.printStackTrace();
            sender.sendMessage(configManager.deserialize("<red>Reload fehlgeschlagen. Details stehen in der Konsole.</red>"));
        }
        return true;
    }

    private void registerCommands() {
        registerCommand("customjobs");
        registerCommand("level");
    }

    private void registerCommand(final String name) {
        final PluginCommand command = getCommand(name);
        if (command == null) {
            throw new IllegalStateException("Befehl /" + name + " ist nicht in plugin.yml registriert.");
        }

        command.setExecutor(this);
        command.setTabCompleter(this);
    }

    private void registerListeners() {
        final PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(playerDataManager, this);
        pluginManager.registerEvents(levelMenuManager, this);
        pluginManager.registerEvents(new NaturalLogListener(this, jobManager, configManager), this);
    }

    private void startTasks() {
        this.inactivityTask = new InactivityTask(this, bossBarManager, configManager);
        inactivityTask.runTaskTimer(this, 20L, 20L);

        this.autosaveTask = new BukkitRunnable() {
            @Override
            public void run() {
                playerDataManager.saveAll();
            }
        }.runTaskTimer(this, 20L * 300L, 20L * 300L);
    }

    private void loadAlreadyOnlinePlayers() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            playerDataManager.loadPlayerData(player.getUniqueId());
        }
    }

    private PlayerDataStorage createPlayerDataStorage() {
        final String storageType = configManager.getStorageType();
        if (storageType.equals("mysql")) {
            getLogger().info("Speicher-Backend: MySQL");
            return new MySqlPlayerDataStorage(this, Job.WOODCUTTER.getId(), configManager);
        }

        if (!storageType.equals("yaml")) {
            getLogger().warning("Unbekanntes Speicher-Backend '" + storageType + "'. Es wird YAML verwendet.");
        } else {
            getLogger().info("Speicher-Backend: YAML");
        }

        return new YamlPlayerDataStorage(this, Job.WOODCUTTER.getId());
    }
}
