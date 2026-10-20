package dev.casperrs.cookieCore.storage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * The single IO thread: every database read and write of the plugin runs here, in order, never on the main thread.
 * One thread means one JDBC connection and no locking in the stores.
 */
public final class StorageThread {

    private StorageThread() {
    }

    /** A daemon executor with one thread called {@code name}. */
    public static ExecutorService create(String name) {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Lets the queued work finish, waiting at most {@code timeoutSeconds}, then stops the thread.
     * Returns whether everything finished in time.
     */
    public static boolean shutdown(ExecutorService io, int timeoutSeconds, Logger log) {
        io.shutdown();
        try {
            if (io.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                return true;
            }
            log.warning("Storage did not finish within " + timeoutSeconds + "s; dropping " + io.shutdownNow().size() + " queued task(s)");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            io.shutdownNow();
        }
        return false;
    }
}
