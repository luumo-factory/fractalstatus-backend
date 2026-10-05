package ai.luumo.fractalstatus.stats;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckStatsTest {

    private CheckStats stats;

    @BeforeEach
    void setUp() {
        stats = new CheckStats();
    }

    @Test
    void emptySnapshotHasZeroTotal() {
        Map<String, Object> s = stats.snapshot();
        assertEquals(0, s.get("total"));
        assertTrue(((Map<?, ?>) s.get("modules")).isEmpty());
        assertEquals(CheckStats.WINDOW_SECONDS, s.get("windowSeconds"));
    }

    @Test
    void recordedChecksAppearInSnapshot() {
        stats.record("ping");
        stats.record("ping");
        stats.record("http");

        Map<String, Object> s = stats.snapshot();
        Map<?, ?> modules = (Map<?, ?>) s.get("modules");

        assertEquals(2, modules.get("ping"));
        assertEquals(1, modules.get("http"));
        assertEquals(3, s.get("total"));
    }

    @Test
    void modulesAreSortedAlphabetically() {
        stats.record("ping");
        stats.record("http");
        stats.record("mqtt");
        stats.record("dns");

        Map<?, ?> modules = (Map<?, ?>) stats.snapshot().get("modules");
        var keys = modules.keySet().toArray();
        assertEquals("dns",  keys[0]);
        assertEquals("http", keys[1]);
        assertEquals("mqtt", keys[2]);
        assertEquals("ping", keys[3]);
    }

    @Test
    void totalMatchesSumOfModules() {
        stats.record("ping");
        stats.record("ping");
        stats.record("ping");
        stats.record("http");
        stats.record("http");

        Map<String, Object> s = stats.snapshot();
        Map<?, ?> modules = (Map<?, ?>) s.get("modules");
        int sum = modules.values().stream().mapToInt(v -> (int) v).sum();
        assertEquals(sum, s.get("total"));
        assertEquals(5, s.get("total"));
    }

    @Test
    void unseenModulesAreAbsentFromSnapshot() {
        stats.record("ping");
        Map<?, ?> modules = (Map<?, ?>) stats.snapshot().get("modules");
        assertFalse(modules.containsKey("http"));
        assertFalse(modules.containsKey("mqtt"));
    }

    @Test
    void multipleSnapshotCallsAreIdempotentForRecentEntries() {
        stats.record("ping");
        stats.record("ping");

        Map<String, Object> s1 = stats.snapshot();
        Map<String, Object> s2 = stats.snapshot();

        assertEquals(s1.get("total"), s2.get("total"));
    }
}
