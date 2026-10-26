package dev.casperrs.cookieCore.api;

/**
 * The only supported surface of CookieCore for other plugins. Get it through Bukkit's services manager:
 * <pre>{@code
 * RegisteredServiceProvider<CookieCoreApi> rsp = Bukkit.getServicesManager().getRegistration(CookieCoreApi.class);
 * CookieCoreApi core = rsp == null ? null : rsp.getProvider();
 * }</pre>
 * Declare {@code softdepend: [CookieCore]} and handle the missing service. Everything outside the
 * {@code dev.casperrs.cookieCore.api} package is internal and may change at any time. The API exposes no Brigadier,
 * LuckPerms or storage types.
 */
public interface CookieCoreApi {

    /** The plugin version, for example {@code 1.0.0}. */
    String version();

    /** Players seen before, by name or id. */
    PlayerLookup players();
}
