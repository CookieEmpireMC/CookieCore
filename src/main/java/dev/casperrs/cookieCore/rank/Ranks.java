package dev.casperrs.cookieCore.rank;

import dev.casperrs.cookieCore.config.YamlFiles;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The ranks of {@code chat.yml} and the source of prefixes and tab list order built from them (LuckPerms first when it
 * runs). Built once and shared by the chat and the tab list, so the tab list works with {@code chat.enabled: false} too.
 */
public final class Ranks {

    private static final String FILE = "chat.yml";

    private final JavaPlugin plugin;
    private final PermissionRankSource permission;
    private final RankSource source;
    private volatile @Nullable RankSettings settings;

    public Ranks(JavaPlugin plugin) {
        this.plugin = plugin;
        this.permission = new PermissionRankSource(() -> settings);
        this.source = RankSources.create(permission, plugin.getLogger());
    }

    /** LuckPerms when available, else the permission ranks. */
    public RankSource source() {
        return source;
    }

    /** The permission ranks alone: their cache is filled on join ({@link #join}) and emptied on quit. */
    public PermissionRankSource permission() {
        return permission;
    }

    /** Main thread: resolve the permission rank of a player who just joined. */
    public void join(Player player) {
        permission.refresh(player);
    }

    public void quit(UUID player) {
        permission.forget(player);
    }

    /**
     * Enable and reload, main thread: reads the ranks of {@code chat.yml} again and resolves them for everybody online (a
     * {@code /reload} fires no join events). A broken file keeps the previous ranks; before the first good read nothing is
     * ranked. Problems are added to {@code problems}.
     */
    public void reload(List<String> problems) {
        Optional<RankSettings> fresh = Optional.empty();
        try {
            fresh = YamlFiles.load(plugin, FILE).map(yaml -> RankSettings.parse(YamlFiles.toMap(yaml), problems::add));
        } catch (YamlFiles.BadYamlException e) {
            problems.add(e.getMessage());
        }
        if (fresh.isPresent()) {
            settings = fresh.get();
        } else if (settings == null) {
            settings = YamlFiles.jarCopy(plugin, FILE).map(yaml -> RankSettings.parse(YamlFiles.toMap(yaml), problems::add)).orElse(null);
        }
        permission.refreshAll(Bukkit.getOnlinePlayers());
    }
}
