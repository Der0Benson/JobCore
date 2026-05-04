# JobCore API

JobCore registriert eine offentliche Bukkit-Service-API, die andere Plugins direkt nutzen konnen.

## API beziehen

```java
import de.derbenson.jobcore.api.JobCoreApi;
import de.derbenson.jobcore.api.JobCoreApiProvider;

JobCoreApi api = JobCoreApiProvider.get().orElse(null);
if (api == null) {
    return;
}
```

## Typische Nutzung

XP fur einen Job vergeben:

```java
api.grantDirectExperience(player, Job.MINER, 50);
```

Job-Fortschritt lesen:

```java
JobProgressSnapshot progress = api.getJobProgress(player.getUniqueId(), Job.MINER);
int level = progress.level();
long xp = progress.xp();
```

Aktive Quest lesen:

```java
api.getActiveQuest(QuestPeriod.DAILY).ifPresent(quest -> {
    player.sendMessage("Daily Quest: " + quest.displayName());
});
```

Quest annehmen oder abgeben:

```java
api.acceptQuest(player, "daily_backstube");
api.claimQuest(player, "daily_backstube");
```

## Verfugbare Events

- `JobCoreExperienceGainEvent`
- `JobCoreLevelUpEvent`
- `JobCoreQuestAcceptEvent`
- `JobCoreQuestAbandonEvent`
- `JobCoreQuestProgressEvent`
- `JobCoreQuestCompleteEvent`
- `JobCoreQuestClaimEvent`

## Event-Beispiel

```java
import de.derbenson.jobcore.api.event.JobCoreLevelUpEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class MyListener implements Listener {

    @EventHandler
    public void onLevelUp(final JobCoreLevelUpEvent event) {
        event.getPlayer().sendMessage("Neues Level in " + event.getJob().getDisplayName());
    }
}
```
