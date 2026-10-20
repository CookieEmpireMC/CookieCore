package dev.casperrs.cookieCore.api;

import java.util.UUID;

/** A player Core has seen join at least once: the stable id and the last known name. */
public record PlayerRef(UUID uuid, String name) {
}
