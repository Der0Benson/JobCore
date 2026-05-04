package de.derbenson.jobcore;

import de.derbenson.jobcore.api.JobCoreApi;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.StringUtil;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class JobCore extends JavaPlugin implements CommandExecutor, TabCompleter {

    private ConfigManager configManager;
    private DebugManager debugManager;
    private PlayerDataStorage playerDataStorage;
    private PlayerDataManager playerDataManager;
    private BossBarManager bossBarManager;
    private JobManager jobManager;
    private QuestManager questManager;
    private QuestFeedbackManager questFeedbackManager;
    private LeaderboardManager leaderboardManager;
    private PlacedBlockTracker placedBlockTracker;
    private LevelMenuManager levelMenuManager;
    private QuestMenuManager questMenuManager;
    private QuestNpcManager questNpcManager;
    private ExportManager exportManager;
    private JobCoreApi api;
    private InactivityTask inactivityTask;
    private BukkitTask autosaveTask;

    @Override
    public void onEnable() {
        try {
            this.configManager = new ConfigManager(this);
            this.debugManager = new DebugManager(configManager);
            this.playerDataStorage = createPlayerDataStorage();
            playerDataStorage.initialize();
            this.playerDataManager = new PlayerDataManager(this, playerDataStorage);
            this.bossBarManager = new BossBarManager(this, configManager);
            this.jobManager = new JobManager(this, configManager, playerDataManager, bossBarManager);
            this.questFeedbackManager = new QuestFeedbackManager(this, configManager);
            this.questManager = new QuestManager(this, configManager, playerDataManager, jobManager, questFeedbackManager);
            this.leaderboardManager = new LeaderboardManager(this, configManager, playerDataManager);
            this.placedBlockTracker = new PlacedBlockTracker(this);
            this.levelMenuManager = new LevelMenuManager(configManager, jobManager, leaderboardManager);
            this.questMenuManager = new QuestMenuManager(configManager, questManager);
            this.questNpcManager = new QuestNpcManager(this, configManager, questMenuManager);
            this.exportManager = new ExportManager(this, configManager, playerDataManager, questManager);
            this.api = new JobCoreApiImpl(playerDataManager, jobManager, questManager);

            registerApi();
            registerCommands();
            registerListeners();
            registerPlaceholderExpansion();
            startTasks();
            loadAlreadyOnlinePlayers();
            leaderboardManager.warmUp();

            logStartupBanner();
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
            playerDataManager.saveAllSync();
            playerDataManager.close();
        }
        if (questNpcManager != null) {
            questNpcManager.save();
        }
        getServer().getServicesManager().unregisterAll(this);

        getLogger().info("JobCore wurde deaktiviert.");
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (command.getName().equalsIgnoreCase("level")) {
            return handleLevelCommand(sender);
        }

        if (args.length == 0) {
            return handleRootCommand(sender);
        }

        if (args[0].equalsIgnoreCase("config")) {
            return handleConfigCommand(sender);
        }

        if (args[0].equalsIgnoreCase("info")) {
            return handleInfoCommand(sender);
        }

        if (args[0].equalsIgnoreCase("reload")) {
            return handleReloadCommand(sender);
        }
        if (args[0].equalsIgnoreCase("stats")) {
            return handleStatsCommand(sender, args);
        }
        if (args[0].equalsIgnoreCase("addxp")) {
            return handleAddXpCommand(sender, args);
        }
        if (args[0].equalsIgnoreCase("setlevel")) {
            return handleSetLevelCommand(sender, args);
        }
        if (args[0].equalsIgnoreCase("debugxp")) {
            return handleDebugXpCommand(sender, args);
        }
        if (args[0].equalsIgnoreCase("spawnquestnpc")) {
            return handleSpawnQuestNpcCommand(sender, args);
        }
        if (args[0].equalsIgnoreCase("removequestnpc")) {
            return handleRemoveQuestNpcCommand(sender);
        }
        if (args[0].equalsIgnoreCase("export")) {
            return handleExportCommand(sender);
        }
        if (args[0].equalsIgnoreCase("givebooster")) {
            return handleGiveBoosterCommand(sender, args);
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

        if (args.length == 1) {
            final List<String> completions = new ArrayList<>();
            final List<String> options = new ArrayList<>();

            if (sender instanceof Player && hasConfigPermission(sender)) {
                options.add("config");
            }
            if (hasCommandPermission(sender, "info")) {
                options.add("info");
            }
            if (hasCommandPermission(sender, "reload")) {
                options.add("reload");
            }
            if (hasCommandPermission(sender, "stats")) {
                options.add("stats");
            }
            if (hasCommandPermission(sender, "addxp")) {
                options.add("addxp");
            }
            if (hasCommandPermission(sender, "setlevel")) {
                options.add("setlevel");
            }
            if (hasCommandPermission(sender, "debugxp")) {
                options.add("debugxp");
            }
            if (hasCommandPermission(sender, "spawnquestnpc")) {
                options.add("spawnquestnpc");
            }
            if (hasCommandPermission(sender, "removequestnpc")) {
                options.add("removequestnpc");
            }
            if (hasCommandPermission(sender, "export")) {
                options.add("export");
            }
            if (hasCommandPermission(sender, "givebooster")) {
                options.add("givebooster");
            }

            StringUtil.copyPartialMatches(args[0], options, completions);
            return completions;
        }

        if (!canTabCompleteAdminCommand(sender, args[0])) {
            return List.of();
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("debugxp")) {
                final List<String> completions = new ArrayList<>();
                StringUtil.copyPartialMatches(args[1], List.of("on", "off"), completions);
                return completions;
            }

            if (args[0].equalsIgnoreCase("stats")
                    || args[0].equalsIgnoreCase("addxp")
                    || args[0].equalsIgnoreCase("setlevel")
                    || args[0].equalsIgnoreCase("givebooster")) {
                final List<String> completions = new ArrayList<>();
                final List<String> names = playerDataManager.getKnownPlayerNames();
                StringUtil.copyPartialMatches(args[1], names, completions);
                return completions;
            }
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("spawnquestnpc")) {
            return List.of();
        }

        if (args.length == 3 && (args[0].equalsIgnoreCase("addxp") || args[0].equalsIgnoreCase("setlevel") || args[0].equalsIgnoreCase("givebooster"))) {
            final List<String> completions = new ArrayList<>();
            final List<String> jobIds = new ArrayList<>(jobManager.getJobs().stream()
                    .map(Job::getId)
                    .toList());
            if (args[0].equalsIgnoreCase("givebooster")) {
                jobIds.add("all");
            }
            StringUtil.copyPartialMatches(args[2], jobIds, completions);
            return completions;
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("givebooster")) {
            final List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[3], List.of("25", "50", "100"), completions);
            return completions;
        }

        if (args.length == 5 && args[0].equalsIgnoreCase("givebooster")) {
            final List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[4], List.of("15m", "30m", "1h", "2h"), completions);
            return completions;
        }

        return List.of();
    }

    private boolean handleRootCommand(final CommandSender sender) {
        boolean sentMessage = false;
        if (sender instanceof Player && hasConfigPermission(sender)) {
            sender.sendMessage(configManager.deserialize("<gray>Nutze <white>/jobcore config</white><gray>, um deine BossBar an- oder auszuschalten.</gray>"));
            sentMessage = true;
        }
        if (hasAnyCommandPermission(sender)) {
            sender.sendMessage(configManager.deserialize("<gray>Admin: <white>/jobcore info</white><gray>, <white>/jobcore reload</white><gray>, <white>/jobcore stats</white><gray>, <white>/jobcore addxp</white><gray>, <white>/jobcore setlevel</white><gray>, <white>/jobcore debugxp</white><gray>, <white>/jobcore spawnquestnpc</white><gray>, <white>/jobcore removequestnpc</white><gray>, <white>/jobcore export</white><gray>, <white>/jobcore givebooster</white></gray>"));
            sentMessage = true;
        }
        if (!sentMessage) {
            sender.sendMessage(configManager.getMessage("messages.no-permission"));
        }
        return true;
    }

    private boolean handleInfoCommand(final CommandSender sender) {
        if (!hasCommandPermission(sender, "info")) {
            sender.sendMessage(configManager.getMessage("messages.no-permission"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage(
                    "messages.console-info",
                    Map.of("version", getPluginMeta().getVersion())
            ));
            return true;
        }

        player.sendMessage(configManager.getMessage("messages.info-header"));
        for (final Job job : jobManager.getJobs()) {
            final JobProgress progress = jobManager.getProgress(player.getUniqueId(), job);
            final long neededXp = jobManager.getXpForNextLevel(progress.getLevel());
            final String xpText = jobManager.isMaxLevel(progress.getLevel()) ? "MAX" : String.valueOf(progress.getXp());
            final String neededText = jobManager.isMaxLevel(progress.getLevel()) ? "MAX" : String.valueOf(neededXp);
            player.sendMessage(configManager.getMessage(
                    "messages.info-entry",
                    Map.of(
                            "job", configManager.getJobDisplayName(job),
                            "level", String.valueOf(progress.getLevel()),
                            "xp", xpText,
                            "needed", neededText
                    )
            ));
        }
        return true;
    }

    private boolean handleConfigCommand(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("messages.player-only"));
            return true;
        }

        if (!hasConfigPermission(player)) {
            player.sendMessage(configManager.getMessage("messages.no-permission"));
            return true;
        }

        final boolean enabled = playerDataManager.toggleBossBar(player.getUniqueId());
        playerDataManager.savePlayerData(player.getUniqueId()).exceptionally(throwable -> {
            getLogger().severe("BossBar-Einstellung konnte nicht gespeichert werden: " + player.getUniqueId());
            throwable.printStackTrace();
            return null;
        });
        if (!enabled) {
            bossBarManager.hide(player);
        }

        player.sendMessage(configManager.deserialize(
                enabled
                        ? "<green>Deine Job-BossBar wurde aktiviert.</green>"
                        : "<yellow>Deine Job-BossBar wurde deaktiviert.</yellow>"
        ));
        return true;
    }

    private boolean handleLevelCommand(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("messages.player-only"));
            return true;
        }

        if (!hasLevelPermission(player)) {
            player.sendMessage(configManager.getMessage("messages.no-permission"));
            return true;
        }

        levelMenuManager.openOverview(player);
        return true;
    }

    private boolean handleReloadCommand(final CommandSender sender) {
        if (!hasCommandPermission(sender, "reload")) {
            sender.sendMessage(configManager.getMessage("messages.no-permission"));
            return true;
        }

        try {
            final String previousStorageSignature = configManager.getStorageSignature();
            configManager.reload();
            jobManager.reload();
            questManager.reload();
            leaderboardManager.invalidate();
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

    private boolean handleStatsCommand(final CommandSender sender, final String[] args) {
        if (!hasCommandPermission(sender, "stats")) {
            sender.sendMessage(configManager.getMessage("messages.no-permission"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(configManager.deserialize("<red>Nutze /jobcore stats <spieler>.</red>"));
            return true;
        }

        final Player onlineTarget = resolveOnlinePlayer(args[1]);
        if (onlineTarget != null) {
            sendStats(sender, onlineTarget.getName(), playerDataManager.getOrCreateData(onlineTarget.getUniqueId()));
            return true;
        }

        sender.sendMessage(configManager.deserialize("<gray>Lade gespeicherte Daten für <white>%player%</white>...</gray>", Map.of("player", args[1])));
        playerDataManager.findPlayerAsync(args[1]).whenComplete((lookupResult, throwable) -> runSync(() -> {
            if (throwable != null) {
                sender.sendMessage(configManager.deserialize("<red>Spielerdaten konnten nicht geladen werden. Details stehen in der Konsole.</red>"));
                getLogger().severe("Offline-Stats fehlgeschlagen: " + throwable.getMessage());
                throwable.printStackTrace();
                return;
            }

            if (lookupResult.isEmpty()) {
                sender.sendMessage(configManager.deserialize("<red>Spieler <white>%player%</white> wurde in den gespeicherten Daten nicht gefunden.</red>", Map.of("player", args[1])));
                return;
            }

            sendStats(sender, lookupResult.get().playerName(), lookupResult.get().data());
        }));
        return true;
    }

    private boolean handleAddXpCommand(final CommandSender sender, final String[] args) {
        if (!hasCommandPermission(sender, "addxp")) {
            sender.sendMessage(configManager.getMessage("messages.no-permission"));
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage(configManager.deserialize("<red>Nutze /jobcore addxp <spieler> <job> <menge>.</red>"));
            return true;
        }

        final Optional<Job> job = Job.fromId(args[2]);
        if (job.isEmpty()) {
            sender.sendMessage(configManager.deserialize("<red>Unbekannter Job: <white>%job%</white>.</red>", Map.of("job", args[2])));
            return true;
        }

        final Integer amount = parsePositiveInt(args[3]);
        if (amount == null) {
            sender.sendMessage(configManager.deserialize("<red>Die XP-Menge muss eine positive Zahl sein.</red>"));
            return true;
        }

        final Player onlineTarget = resolveOnlinePlayer(args[1]);
        if (onlineTarget != null) {
            final int granted = jobManager.grantDirectExperience(onlineTarget, job.get(), amount);
            playerDataManager.savePlayerData(onlineTarget.getUniqueId());
            leaderboardManager.invalidate();
            sender.sendMessage(configManager.deserialize(
                    "<green>%player%</green><gray> erhielt <white>%xp%</white> direkte XP für <white>%job%</white>.</gray>",
                    Map.of(
                            "player", onlineTarget.getName(),
                            "xp", String.valueOf(granted),
                            "job", configManager.getJobDisplayName(job.get())
                    )
            ));
            return true;
        }

        sender.sendMessage(configManager.deserialize("<gray>Lade gespeicherte Daten für <white>%player%</white>...</gray>", Map.of("player", args[1])));
        playerDataManager.findPlayerAsync(args[1])
                .thenCompose(lookupResult -> {
                    if (lookupResult.isEmpty()) {
                        return CompletableFuture.completedFuture(OfflineMutationResult.notFound(args[1]));
                    }

                    final PlayerDataManager.PlayerDataLookupResult resolved = lookupResult.get();
                    final int granted = jobManager.grantDirectExperience(resolved.playerUuid(), resolved.data(), job.get(), amount);
                    return playerDataManager.saveDetachedData(resolved.playerUuid(), resolved.data())
                            .thenApply(ignored -> OfflineMutationResult.success(resolved.playerName(), granted, job.get()));
                })
                .whenComplete((result, throwable) -> runSync(() -> {
                    if (throwable != null) {
                        sender.sendMessage(configManager.deserialize("<red>Offline-XP konnten nicht gespeichert werden. Details stehen in der Konsole.</red>"));
                        getLogger().severe("Offline-XP fehlgeschlagen: " + throwable.getMessage());
                        throwable.printStackTrace();
                        return;
                    }

                    if (!result.found()) {
                        sender.sendMessage(configManager.deserialize("<red>Spieler <white>%player%</white> wurde in den gespeicherten Daten nicht gefunden.</red>", Map.of("player", result.input())));
                        return;
                    }

                    leaderboardManager.invalidate();
                    sender.sendMessage(configManager.deserialize(
                            "<green>%player%</green><gray> erhielt <white>%xp%</white> direkte XP für <white>%job%</white>.</gray>",
                            Map.of(
                                    "player", result.playerName(),
                                    "xp", String.valueOf(result.value()),
                                    "job", configManager.getJobDisplayName(result.job())
                            )
                    ));
                }));
        return true;
    }

    private boolean handleSetLevelCommand(final CommandSender sender, final String[] args) {
        if (!hasCommandPermission(sender, "setlevel")) {
            sender.sendMessage(configManager.getMessage("messages.no-permission"));
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage(configManager.deserialize("<red>Nutze /jobcore setlevel <spieler> <job> <level>.</red>"));
            return true;
        }

        final Optional<Job> job = Job.fromId(args[2]);
        if (job.isEmpty()) {
            sender.sendMessage(configManager.deserialize("<red>Unbekannter Job: <white>%job%</white>.</red>", Map.of("job", args[2])));
            return true;
        }

        final Integer level = parseNonNegativeInt(args[3]);
        if (level == null) {
            sender.sendMessage(configManager.deserialize("<red>Das Level muss eine Zahl von 0 bis %max% sein.</red>", Map.of("max", String.valueOf(jobManager.getMaxLevel()))));
            return true;
        }

        final int targetLevel = Math.min(jobManager.getMaxLevel(), level);
        final Player onlineTarget = resolveOnlinePlayer(args[1]);
        if (onlineTarget != null) {
            jobManager.setLevel(onlineTarget, job.get(), targetLevel);
            playerDataManager.savePlayerData(onlineTarget.getUniqueId());
            leaderboardManager.invalidate();
            sender.sendMessage(configManager.deserialize(
                    "<green>%player%</green><gray> wurde in <white>%job%</white> auf Level <white>%level%</white> gesetzt.</gray>",
                    Map.of(
                            "player", onlineTarget.getName(),
                            "job", configManager.getJobDisplayName(job.get()),
                            "level", String.valueOf(targetLevel)
                    )
            ));
            return true;
        }

        sender.sendMessage(configManager.deserialize("<gray>Lade gespeicherte Daten für <white>%player%</white>...</gray>", Map.of("player", args[1])));
        playerDataManager.findPlayerAsync(args[1])
                .thenCompose(lookupResult -> {
                    if (lookupResult.isEmpty()) {
                        return CompletableFuture.completedFuture(OfflineMutationResult.notFound(args[1]));
                    }

                    final PlayerDataManager.PlayerDataLookupResult resolved = lookupResult.get();
                    jobManager.setLevel(resolved.data(), job.get(), targetLevel);
                    return playerDataManager.saveDetachedData(resolved.playerUuid(), resolved.data())
                            .thenApply(ignored -> OfflineMutationResult.success(resolved.playerName(), targetLevel, job.get()));
                })
                .whenComplete((result, throwable) -> runSync(() -> {
                    if (throwable != null) {
                        sender.sendMessage(configManager.deserialize("<red>Offline-Level konnte nicht gespeichert werden. Details stehen in der Konsole.</red>"));
                        getLogger().severe("Offline-Level fehlgeschlagen: " + throwable.getMessage());
                        throwable.printStackTrace();
                        return;
                    }

                    if (!result.found()) {
                        sender.sendMessage(configManager.deserialize("<red>Spieler <white>%player%</white> wurde in den gespeicherten Daten nicht gefunden.</red>", Map.of("player", result.input())));
                        return;
                    }

                    leaderboardManager.invalidate();
                    sender.sendMessage(configManager.deserialize(
                            "<green>%player%</green><gray> wurde in <white>%job%</white> auf Level <white>%level%</white> gesetzt.</gray>",
                            Map.of(
                                    "player", result.playerName(),
                                    "job", configManager.getJobDisplayName(result.job()),
                                    "level", String.valueOf(result.value())
                            )
                    ));
                }));
        return true;
    }

    private boolean handleDebugXpCommand(final CommandSender sender, final String[] args) {
        if (!hasCommandPermission(sender, "debugxp")) {
            sender.sendMessage(configManager.getMessage("messages.no-permission"));
            return true;
        }

        if (args.length < 2 || (!args[1].equalsIgnoreCase("on") && !args[1].equalsIgnoreCase("off"))) {
            sender.sendMessage(configManager.deserialize("<red>Nutze /jobcore debugxp <on|off>.</red>"));
            return true;
        }

        final boolean enabled = debugManager.setDebugEnabled(sender, args[1].equalsIgnoreCase("on"));
        sender.sendMessage(configManager.deserialize(
                enabled
                        ? "<green>XP-Debug wurde aktiviert.</green>"
                        : "<yellow>XP-Debug wurde deaktiviert.</yellow>"
        ));
        return true;
    }

    private boolean handleSpawnQuestNpcCommand(final CommandSender sender, final String[] args) {
        if (!hasCommandPermission(sender, "spawnquestnpc")) {
            sender.sendMessage(configManager.getMessage("messages.no-permission"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("messages.player-only"));
            return true;
        }

        final String customName = args.length >= 2
                ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                : "";
        if (!questNpcManager.spawnQuestNpc(player, customName)) {
            player.sendMessage(configManager.deserialize("<red>Quest-NPC konnte nicht gespawnt werden.</red>"));
            return true;
        }

        player.sendMessage(configManager.deserialize("<green>Quest-NPC gespawnt.</green>"));
        return true;
    }

    private boolean handleRemoveQuestNpcCommand(final CommandSender sender) {
        if (!hasCommandPermission(sender, "removequestnpc")) {
            sender.sendMessage(configManager.getMessage("messages.no-permission"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("messages.player-only"));
            return true;
        }

        if (!questNpcManager.removeNearestQuestNpc(player)) {
            player.sendMessage(configManager.deserialize("<red>Kein Quest-NPC in deiner Nähe gefunden.</red>"));
            return true;
        }

        player.sendMessage(configManager.deserialize("<yellow>Quest-NPC entfernt.</yellow>"));
        return true;
    }

    private boolean handleExportCommand(final CommandSender sender) {
        if (!hasCommandPermission(sender, "export")) {
            sender.sendMessage(configManager.getMessage("messages.no-permission"));
            return true;
        }

        sender.sendMessage(configManager.deserialize("<gray>Export wird erstellt...</gray>"));
        CompletableFuture.supplyAsync(() -> {
            try {
                return exportManager.exportSnapshot();
            } catch (final Exception exception) {
                throw new RuntimeException(exception);
            }
        }).whenComplete((result, throwable) -> runSync(() -> {
            if (throwable != null) {
                final Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                getLogger().severe("Export fehlgeschlagen: " + cause.getMessage());
                cause.printStackTrace();
                sender.sendMessage(configManager.deserialize("<red>Export fehlgeschlagen. Details stehen in der Konsole.</red>"));
                return;
            }

            sender.sendMessage(configManager.deserialize(
                    "<green>Export erstellt.</green> <gray>%count% Spieler wurden nach <white>%path%</white><gray> geschrieben.</gray>",
                    Map.of(
                            "count", String.valueOf(result.playerCount()),
                            "path", result.file().getAbsolutePath()
                    )
            ));
        }));
        return true;
    }

    private boolean handleGiveBoosterCommand(final CommandSender sender, final String[] args) {
        if (!hasCommandPermission(sender, "givebooster")) {
            sender.sendMessage(configManager.getMessage("messages.no-permission"));
            return true;
        }

        if (args.length < 5) {
            sender.sendMessage(configManager.deserialize("<red>Nutze /jobcore givebooster <spieler> <job|all> <bonusProzent> <dauer>.</red>"));
            sender.sendMessage(configManager.deserialize("<gray>Beispiel: <white>/jobcore givebooster Steve miner 50 30m</white></gray>"));
            return true;
        }

        final Optional<Job> job = args[2].equalsIgnoreCase("all") ? Optional.empty() : Job.fromId(args[2]);
        if (!args[2].equalsIgnoreCase("all") && job.isEmpty()) {
            sender.sendMessage(configManager.deserialize("<red>Unbekannter Job: <white>%job%</white>.</red>", Map.of("job", args[2])));
            return true;
        }

        final Integer percent = parsePositiveInt(args[3]);
        if (percent == null) {
            sender.sendMessage(configManager.deserialize("<red>Der Bonus muss als positive Prozentzahl angegeben werden.</red>"));
            return true;
        }

        final Duration duration = parseDuration(args[4]);
        if (duration == null) {
            sender.sendMessage(configManager.deserialize("<red>Die Dauer muss z.B. <white>30m</white>, <white>2h</white> oder <white>1d</white> sein.</red>"));
            return true;
        }

        final double bonusMultiplier = percent / 100.0D;
        final Player onlineTarget = resolveOnlinePlayer(args[1]);
        if (onlineTarget != null) {
            jobManager.giveXpBooster(onlineTarget.getUniqueId(), job.orElse(null), bonusMultiplier, duration);
            playerDataManager.savePlayerData(onlineTarget.getUniqueId());
            sender.sendMessage(formatBoosterMessage(onlineTarget.getName(), job.orElse(null), percent, duration));
            return true;
        }

        sender.sendMessage(configManager.deserialize("<gray>Lade gespeicherte Daten fÃ¼r <white>%player%</white>...</gray>", Map.of("player", args[1])));
        playerDataManager.findPlayerAsync(args[1])
                .thenCompose(lookupResult -> {
                    if (lookupResult.isEmpty()) {
                        return CompletableFuture.completedFuture(BoosterMutationResult.notFound(args[1]));
                    }

                    final PlayerDataManager.PlayerDataLookupResult resolved = lookupResult.get();
                    jobManager.giveXpBooster(resolved.data(), job.orElse(null), bonusMultiplier, duration);
                    return playerDataManager.saveDetachedData(resolved.playerUuid(), resolved.data())
                            .thenApply(ignored -> BoosterMutationResult.success(resolved.playerName(), job.orElse(null), percent, duration));
                })
                .whenComplete((result, throwable) -> runSync(() -> {
                    if (throwable != null) {
                        sender.sendMessage(configManager.deserialize("<red>Offline-Booster konnte nicht gespeichert werden. Details stehen in der Konsole.</red>"));
                        getLogger().severe("Offline-Booster fehlgeschlagen: " + throwable.getMessage());
                        throwable.printStackTrace();
                        return;
                    }

                    if (!result.found()) {
                        sender.sendMessage(configManager.deserialize("<red>Spieler <white>%player%</white> wurde in den gespeicherten Daten nicht gefunden.</red>", Map.of("player", result.input())));
                        return;
                    }

                    sender.sendMessage(formatBoosterMessage(result.playerName(), result.job(), result.percent(), result.duration()));
                }));
        return true;
    }

    private void registerCommands() {
        registerCommand("jobcore");
        registerCommand("level");
    }

    private void registerApi() {
        getServer().getServicesManager().register(JobCoreApi.class, api, this, ServicePriority.Normal);
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
        pluginManager.registerEvents(questMenuManager, this);
        pluginManager.registerEvents(questNpcManager, this);
        pluginManager.registerEvents(new PlacedBlockListener(jobManager, placedBlockTracker), this);
        pluginManager.registerEvents(new NaturalLogListener(jobManager, configManager, placedBlockTracker, questManager), this);
        pluginManager.registerEvents(new MinerListener(jobManager, placedBlockTracker, questManager), this);
        pluginManager.registerEvents(new FarmerListener(jobManager, placedBlockTracker, questManager), this);
        pluginManager.registerEvents(new WarriorListener(this, jobManager, configManager, debugManager, questManager), this);
        pluginManager.registerEvents(new AnglerListener(jobManager, configManager, debugManager, questManager), this);
        pluginManager.registerEvents(new AlchemistListener(jobManager, configManager, debugManager, questManager), this);
        pluginManager.registerEvents(new QuestActivityListener(questManager), this);
    }

    private void registerPlaceholderExpansion() {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }

        new JobCorePlaceholderExpansion(this, configManager, playerDataManager, jobManager, questManager).register();
        getLogger().info("PlaceholderAPI-Erweiterung registriert.");
    }

    private void startTasks() {
        this.inactivityTask = new InactivityTask(this, bossBarManager, configManager);
        inactivityTask.runTaskTimer(this, 20L, 20L);

        this.autosaveTask = new BukkitRunnable() {
            @Override
            public void run() {
                playerDataManager.saveAll().exceptionally(throwable -> {
                    getLogger().severe("Autosave fehlgeschlagen: " + throwable.getMessage());
                    throwable.printStackTrace();
                    return null;
                });
            }
        }.runTaskTimer(this, 20L * 300L, 20L * 300L);
    }

    private void loadAlreadyOnlinePlayers() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            playerDataManager.loadPlayerData(player.getUniqueId());
            playerDataManager.updateLastKnownName(player.getUniqueId(), player.getName());
        }
    }

    private PlayerDataStorage createPlayerDataStorage() {
        final String storageType = configManager.getStorageType();
        if (storageType.equals("mysql")) {
            return new MySqlPlayerDataStorage(this, Job.WOODCUTTER.getId(), configManager);
        }

        if (!storageType.equals("yaml")) {
            getLogger().warning("Unbekanntes Speicher-Backend '" + storageType + "'. Es wird YAML verwendet.");
        }

        return new YamlPlayerDataStorage(this, Job.WOODCUTTER.getId());
    }

    private void logStartupBanner() {
        sendConsoleLine(" ");
        sendConsoleLine("        §f██╗ §f██████╗ §f██████╗  §f██████╗ §f██████╗ §f██████╗ §f███████╗");
        sendConsoleLine("        §f██║§f██╔═══██╗§f██╔══██╗§f██╔════╝§f██╔═══██╗§f██╔══██╗§f██╔════╝");
        sendConsoleLine("        §f██║§f██║   ██║§f██████╔╝§f██║     §f██║   ██║§f██████╔╝§f█████╗");
        sendConsoleLine("   §f██   ██║§f██║   ██║§f██╔══██╗§f██║     §f██║   ██║§f██╔══██╗§f██╔══╝");
        sendConsoleLine("   §f╚█████╔╝§f╚██████╔╝§f██████╔╝§f╚██████╗§f╚██████╔╝§f██║  ██║§f███████╗");
        sendConsoleLine("    §f╚════╝  §f╚═════╝ §f╚═════╝  §f╚═════╝ §f╚═════╝ §f╚═╝  ╚═╝§f╚══════╝");
        sendConsoleLine(" ");
        sendConsoleLine("   §aJobCore §fv" + getPluginMeta().getVersion() + " §7erfolgreich aktiviert!");
        sendConsoleLine("   §7Speicher-Backend: §f" + getStorageBackendDisplayName());
        sendConsoleLine(" ");
    }

    private String getStorageBackendDisplayName() {
        return configManager.getStorageType().equalsIgnoreCase("mysql") ? "MySQL" : "YAML";
    }

    private void sendConsoleLine(final String line) {
        Bukkit.getConsoleSender().sendMessage(LegacyComponentSerializer.legacySection().deserialize(line));
    }

    private Player resolveOnlinePlayer(final String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        final Player exactPlayer = Bukkit.getPlayerExact(input);
        if (exactPlayer != null) {
            return exactPlayer;
        }

        try {
            return Bukkit.getPlayer(UUID.fromString(input.trim()));
        } catch (final IllegalArgumentException ignored) {
            return null;
        }
    }

    private void sendStats(final CommandSender sender, final String playerName, final PlayerJobData data) {
        sender.sendMessage(configManager.deserialize("<gray>Job-Status von <white>%player%</white>:</gray>", Map.of("player", playerName)));
        sender.sendMessage(configManager.deserialize("<gray>BossBar: <white>%state%</white>", Map.of(
                "state", data.isBossBarEnabled() ? "aktiviert" : "deaktiviert"
        )));

        for (final Job job : jobManager.getJobs()) {
            final JobProgress progress = jobManager.getProgress(data, job);
            final long neededXp = jobManager.getXpForNextLevel(progress.getLevel());
            final String xpText = jobManager.isMaxLevel(progress.getLevel()) ? "MAX" : String.valueOf(progress.getXp());
            final String neededText = jobManager.isMaxLevel(progress.getLevel()) ? "MAX" : String.valueOf(neededXp);
            sender.sendMessage(configManager.deserialize(
                    "<gray>%job%: <white>Lv.%level%</white> <gray>- <white>%xp%/%needed%</white></gray>",
                    Map.of(
                            "job", configManager.getJobDisplayName(job),
                            "level", String.valueOf(progress.getLevel()),
                            "xp", xpText,
                            "needed", neededText
                    )
            ));
        }
    }

    private void runSync(final Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
            return;
        }
        Bukkit.getScheduler().runTask(this, runnable);
    }

    private net.kyori.adventure.text.Component formatBoosterMessage(
            final String playerName,
            final Job job,
            final int percent,
            final Duration duration
    ) {
        return configManager.deserialize(
                "<green>%player%</green><gray> erhielt <white>+%percent%%</white> XP-Booster fÃ¼r <white>%job%</white> (<white>%duration%</white>).</gray>",
                Map.of(
                        "player", playerName,
                        "percent", String.valueOf(percent),
                        "job", job == null ? "alle Jobs" : configManager.getJobDisplayName(job),
                        "duration", formatDuration(duration)
                )
        );
    }

    private Duration parseDuration(final String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        final String trimmed = input.trim().toLowerCase(java.util.Locale.ROOT);
        final char suffix = trimmed.charAt(trimmed.length() - 1);
        final String numberPart = Character.isLetter(suffix) ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        final long value;
        try {
            value = Long.parseLong(numberPart);
        } catch (final NumberFormatException exception) {
            return null;
        }

        if (value <= 0L) {
            return null;
        }

        return switch (suffix) {
            case 's' -> Duration.ofSeconds(value);
            case 'm' -> Duration.ofMinutes(value);
            case 'h' -> Duration.ofHours(value);
            case 'd' -> Duration.ofDays(value);
            default -> Character.isLetter(suffix) ? null : Duration.ofMinutes(value);
        };
    }

    private String formatDuration(final Duration duration) {
        final long totalSeconds = Math.max(0L, duration.toSeconds());
        final long days = totalSeconds / 86_400L;
        final long hours = (totalSeconds % 86_400L) / 3_600L;
        final long minutes = (totalSeconds % 3_600L) / 60L;
        final long seconds = totalSeconds % 60L;

        if (days > 0L) {
            return days + "d " + hours + "h";
        }
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0L) {
            return minutes + "m";
        }
        return seconds + "s";
    }

    private Integer parsePositiveInt(final String input) {
        try {
            final int value = Integer.parseInt(input);
            return value > 0 ? value : null;
        } catch (final NumberFormatException exception) {
            return null;
        }
    }

    private Integer parseNonNegativeInt(final String input) {
        try {
            final int value = Integer.parseInt(input);
            return value >= 0 ? value : null;
        } catch (final NumberFormatException exception) {
            return null;
        }
    }

    private boolean hasAdminPermission(final CommandSender sender) {
        return sender.hasPermission("jobcore.admin") || sender.hasPermission("customjobs.admin");
    }

    private boolean hasCommandPermission(final CommandSender sender, final String commandName) {
        return hasAdminPermission(sender) || sender.hasPermission("jobcore.command." + commandName.toLowerCase(java.util.Locale.ROOT));
    }

    private boolean hasAnyCommandPermission(final CommandSender sender) {
        return hasAdminPermission(sender)
                || hasCommandPermission(sender, "info")
                || hasCommandPermission(sender, "reload")
                || hasCommandPermission(sender, "stats")
                || hasCommandPermission(sender, "addxp")
                || hasCommandPermission(sender, "setlevel")
                || hasCommandPermission(sender, "debugxp")
                || hasCommandPermission(sender, "spawnquestnpc")
                || hasCommandPermission(sender, "removequestnpc")
                || hasCommandPermission(sender, "export")
                || hasCommandPermission(sender, "givebooster");
    }

    private boolean canTabCompleteAdminCommand(final CommandSender sender, final String commandName) {
        if (commandName == null || commandName.isBlank()) {
            return false;
        }
        return hasCommandPermission(sender, commandName);
    }

    private boolean hasConfigPermission(final CommandSender sender) {
        return sender.hasPermission("jobcore.command.config")
                || sender.hasPermission("jobcore.config")
                || sender.hasPermission("customjobs.config");
    }

    private boolean hasLevelPermission(final CommandSender sender) {
        return sender.hasPermission("jobcore.command.level")
                || sender.hasPermission("jobcore.level")
                || sender.hasPermission("customjobs.level");
    }

    private record OfflineMutationResult(boolean found, String input, String playerName, int value, Job job) {

        private static OfflineMutationResult notFound(final String input) {
            return new OfflineMutationResult(false, input, "", 0, null);
        }

        private static OfflineMutationResult success(final String playerName, final int value, final Job job) {
            return new OfflineMutationResult(true, "", playerName, value, job);
        }
    }

    private record BoosterMutationResult(
            boolean found,
            String input,
            String playerName,
            Job job,
            int percent,
            Duration duration
    ) {

        private static BoosterMutationResult notFound(final String input) {
            return new BoosterMutationResult(false, input, "", null, 0, Duration.ZERO);
        }

        private static BoosterMutationResult success(
                final String playerName,
                final Job job,
                final int percent,
                final Duration duration
        ) {
            return new BoosterMutationResult(true, "", playerName, job, percent, duration);
        }
    }
}
