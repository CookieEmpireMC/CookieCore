package dev.casperrs.cookieCore.storage;

import java.util.UUID;

/** Persistence of each player's sidebar choice. Blocking, storage thread only, like {@link PlayerStore}. */
public interface SidebarStore {

    /** Whether the player wants the sidebar; true when they never chose. */
    boolean loadVisible(UUID uuid) throws StorageException;

    void saveVisible(UUID uuid, boolean visible) throws StorageException;
}
