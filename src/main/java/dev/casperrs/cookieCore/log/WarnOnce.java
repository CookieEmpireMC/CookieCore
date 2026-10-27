package dev.casperrs.cookieCore.log;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/** Logs a warning once per key until {@link #clear}, so a problem that repeats every second fills the log only once. Safe from any thread. */
public final class WarnOnce {

    private final Logger log;
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    public WarnOnce(Logger log) {
        this.log = log;
    }

    public void warn(String key, String message) {
        if (warned.add(key)) {
            log.warning(message);
        }
    }

    public void clear() {
        warned.clear();
    }
}
