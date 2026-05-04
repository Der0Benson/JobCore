package de.derbenson.jobcore;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestManagerTest {

    private static final String CURRENT_DAILY_CYCLE = "2026-05-04";

    @Test
    void cleanupExpiredQuestProgressRemovesOnlyStaleOrEmptyEntries() {
        final PlayerJobData data = new PlayerJobData();
        final PlayerQuestProgress activeProgress = new PlayerQuestProgress(3, true, false, false, CURRENT_DAILY_CYCLE);
        final PlayerQuestProgress claimableProgress = new PlayerQuestProgress(10, false, true, false, CURRENT_DAILY_CYCLE);
        final PlayerQuestProgress claimedProgress = new PlayerQuestProgress(10, false, true, true, CURRENT_DAILY_CYCLE);

        data.setQuestProgress("daily_active", activeProgress);
        data.setQuestProgress("daily_claimable", claimableProgress);
        data.setQuestProgress("daily_claimed", claimedProgress);
        data.setQuestProgress("daily_expired", new PlayerQuestProgress(4, true, false, false, "2026-05-03"));
        data.setQuestProgress("daily_empty", new PlayerQuestProgress(0, false, false, false, CURRENT_DAILY_CYCLE));
        data.setQuestProgress("unknown_old_quest", new PlayerQuestProgress(8, true, false, false, CURRENT_DAILY_CYCLE));

        final boolean changed = QuestManager.cleanupExpiredQuestProgress(
                data,
                Map.of(
                        "daily_active", quest("daily_active"),
                        "daily_claimable", quest("daily_claimable"),
                        "daily_claimed", quest("daily_claimed"),
                        "daily_expired", quest("daily_expired"),
                        "daily_empty", quest("daily_empty")
                ),
                period -> CURRENT_DAILY_CYCLE
        );

        assertTrue(changed);
        assertSame(activeProgress, data.getQuestProgress("daily_active"));
        assertSame(claimableProgress, data.getQuestProgress("daily_claimable"));
        assertSame(claimedProgress, data.getQuestProgress("daily_claimed"));
        assertNull(data.getQuestProgress("daily_expired"));
        assertNull(data.getQuestProgress("daily_empty"));
        assertNull(data.getQuestProgress("unknown_old_quest"));
    }

    @Test
    void cleanupExpiredQuestProgressLeavesCleanDataUnchanged() {
        final PlayerJobData data = new PlayerJobData();
        final PlayerQuestProgress activeProgress = new PlayerQuestProgress(1, true, false, false, CURRENT_DAILY_CYCLE);
        data.setQuestProgress("daily_active", activeProgress);

        final boolean changed = QuestManager.cleanupExpiredQuestProgress(
                data,
                Map.of("daily_active", quest("daily_active")),
                period -> CURRENT_DAILY_CYCLE
        );

        assertFalse(changed);
        assertSame(activeProgress, data.getQuestProgress("daily_active"));
    }

    private static Quest quest(final String id) {
        return new Quest(
                id,
                QuestPeriod.DAILY,
                id,
                null,
                Job.WOODCUTTER,
                QuestObjectiveType.BREAK_BLOCK,
                Set.of(),
                10,
                100,
                "",
                List.of()
        );
    }
}
