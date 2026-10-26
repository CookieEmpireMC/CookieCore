package dev.casperrs.cookieCore.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieCore.TestSupport;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class StorageThreadAndSettingsTest {

    @Test
    void singleNamedDaemonThreadRunsTasksInOrder() throws Exception {
        ExecutorService io = StorageThread.create("CookieCore-Test-IO");
        List<String> threads = Collections.synchronizedList(new ArrayList<>());
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());
        boolean[] daemon = new boolean[1];
        for (int i = 0; i < 20; i++) {
            int n = i;
            io.execute(() -> {
                threads.add(Thread.currentThread().getName());
                daemon[0] = Thread.currentThread().isDaemon();
                order.add(n);
            });
        }
        assertTrue(StorageThread.shutdown(io, 5, new TestSupport.LogCapture().logger));
        assertEquals(1, threads.stream().distinct().count());
        assertEquals("CookieCore-Test-IO", threads.getFirst());
        assertTrue(daemon[0], "a daemon thread never blocks server shutdown");
        assertEquals(java.util.stream.IntStream.range(0, 20).boxed().toList(), order);
    }

    @Test
    void shutdownLetsQueuedWorkFinish() {
        ExecutorService io = StorageThread.create("t");
        boolean[] done = new boolean[1];
        io.execute(() -> {
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            done[0] = true;
        });
        assertTrue(StorageThread.shutdown(io, 5, new TestSupport.LogCapture().logger));
        assertTrue(done[0]);
    }

    @Test
    void shutdownGivesUpAfterTheTimeoutAndLogs() throws Exception {
        ExecutorService io = StorageThread.create("t");
        TestSupport.LogCapture log = new TestSupport.LogCapture();
        CountDownLatch started = new CountDownLatch(1);
        io.execute(() -> {
            started.countDown();
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException ignored) {
                // interrupted by shutdownNow
            }
        });
        io.execute(() -> { });
        assertTrue(started.await(5, TimeUnit.SECONDS));
        assertFalse(StorageThread.shutdown(io, 1, log.logger));
        assertEquals(1, log.messages().size());
        assertTrue(log.messages().getFirst().contains("1 queued"), log.messages().getFirst());
    }

    @Test
    void settingsTypeParsing() {
        assertEquals(Optional.of(DatabaseSettings.Type.H2), DatabaseSettings.Type.parse(" H2 "));
        assertEquals(Optional.of(DatabaseSettings.Type.MARIADB), DatabaseSettings.Type.parse("MariaDB"));
        assertEquals(Optional.of(DatabaseSettings.Type.MARIADB), DatabaseSettings.Type.parse("mysql"));
        assertEquals(Optional.empty(), DatabaseSettings.Type.parse("sqlite"));
        assertEquals(Optional.empty(), DatabaseSettings.Type.parse(null));
        assertEquals(Optional.empty(), DatabaseSettings.Type.parse(""));
    }

    @Test
    void urlsAndDriversPerType() {
        DatabaseSettings h2 = new DatabaseSettings(DatabaseSettings.Type.H2, Path.of("plugins/x/data/core"), "h", 1, "d", "u", "pw", "core_");
        assertTrue(h2.jdbcUrl().startsWith("jdbc:h2:file:"), h2.jdbcUrl());
        assertEquals("org.h2.Driver", h2.driverClass());
        DatabaseSettings maria = new DatabaseSettings(DatabaseSettings.Type.MARIADB, Path.of("x"), "db.local", 3307, "cc", "user", "pw", "core_");
        assertEquals("jdbc:mariadb://db.local:3307/cc", maria.jdbcUrl());
        assertEquals("org.mariadb.jdbc.Driver", maria.driverClass());
        assertFalse(maria.describe().contains("pw"));
        assertFalse(h2.describe().contains("pw"));
    }

    @Test
    void driverClassesAreOnTheTestClasspathSoThePomListsThem() throws Exception {
        Class.forName("org.h2.Driver");
        Class.forName("org.mariadb.jdbc.Driver");
    }

    @Test
    void driverConnectionFactoryOpensAnH2File() throws Exception {
        DatabaseSettings h2 = new DatabaseSettings(DatabaseSettings.Type.H2, Path.of("target/test-h2/" + System.nanoTime()), "h", 1, "d", "u", "", "core_");
        try (var c = new DriverConnectionFactory(h2).open()) {
            assertTrue(c.isValid(2));
        }
    }
}
