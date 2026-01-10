package org.betterservernetwork.click2enchant;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class DisableCommandHandler implements CommandExecutor, TabCompleter, Listener {
    private static final String PERM_TOGGLE_ENCHANT = "click2enchant.command.toggleenchant";
    private static final String PERM_BYPASS_DISABLED = "click2enchant.bypass.disabled";

    public boolean enchantDisabled;

    /**
     * Per-player setting saved only after player successfully uses /toggleenchant.
     * true = disabled, false = enabled
     */
    private final Map<UUID, Boolean> playerDisabled = new HashMap<>();

    private File getFallbackLegacyDataFile() {
        return new File("plugins/ClickToEnchant/disabled.yaml");
    }

    private File getDataFile() {
        Click2Enchant plugin = Click2Enchant.getInstance();
        String path = plugin != null
                ? plugin.getConfig().getString("data.file", "plugins/Click2Enchant/disabled.yaml")
                : "plugins/Click2Enchant/disabled.yaml";
        return new File(path);
    }

    private boolean enforceCommandPermissions() {
        Click2Enchant plugin = Click2Enchant.getInstance();
        return plugin == null || plugin.getConfig().getBoolean("permissions.enforceCommands", true);
    }

    private void send(Player player, String messagePath, String fallback) {
        Click2Enchant plugin = Click2Enchant.getInstance();
        String prefix = plugin != null ? plugin.getConfig().getString("messages.prefix", "") : "";
        String msg = plugin != null ? plugin.getConfig().getString(messagePath, fallback) : fallback;
        player.sendMessage(org.betterservernetwork.click2enchant.tools.Tools.colorize(prefix + msg));
    }

    public DisableCommandHandler() {
        loadData();
    }

    private void saveData() {
        File dataFile = getDataFile();

        YamlConfiguration config = new YamlConfiguration();
        config.set("enchantDisabled", enchantDisabled);

        for (Map.Entry<UUID, Boolean> entry : playerDisabled.entrySet()) {
            config.set("players." + entry.getKey(), entry.getValue());
        }

        try {
            File parent = dataFile.getParentFile();
            if (parent != null && !parent.exists()) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }

            config.save(dataFile);
        } catch (IOException e) {
            Bukkit.getLogger().severe("Failed to save Click2Enchant data: " + e.getMessage());
        }
    }

    public void onDisable() {
        saveData();
        Bukkit.getLogger().info("Saved Click2Enchant data.");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission(PERM_TOGGLE_ENCHANT)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        if (!playerDisabled.containsKey(uuid)) {
            // Default: players with permission start disabled
            playerDisabled.put(uuid, true);
            saveData();
        }
    }

    public void loadData() {
        File dataFile = getDataFile();

        // Backward compatibility: old default folder name
        if (!dataFile.exists()) {
            File legacy = getFallbackLegacyDataFile();
            if (legacy.exists()) {
                dataFile = legacy;
            }
        }

        if (!dataFile.exists()) {
            Bukkit.getLogger().info("No Click2Enchant data file found; starting with defaults.");
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);

        if (config.get("enchantDisabled") != null) enchantDisabled = (boolean) config.get("enchantDisabled");

        if (config.getConfigurationSection("players") != null) {
            for (String key : Objects.requireNonNull(config.getConfigurationSection("players")).getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);

                    // Backward compatible with old format where presence meant true
                    Object raw = config.get("players." + key);
                    boolean disabled = true;
                    if (raw instanceof Boolean) {
                        disabled = (Boolean) raw;
                    } else if (raw != null) {
                        disabled = config.getBoolean("players." + key, true);
                    }

                    playerDisabled.put(uuid, disabled);
                } catch (IllegalArgumentException ignored) {
                    // ignore invalid UUID entries
                }
            }
        }

        Bukkit.getLogger().info("Loaded Click2Enchant data.");
    }

    public boolean isDisabled(Player player) {
        // Players without permission are not managed by this plugin.
        if (!player.hasPermission(PERM_TOGGLE_ENCHANT)) {
            return false;
        }

        Click2Enchant plugin = Click2Enchant.getInstance();
        boolean bypassAllowed = plugin == null || plugin.getConfig().getBoolean("behavior.bypassDisableChecksWithPermission", true);
        if (bypassAllowed) {
            if (player.hasPermission(PERM_BYPASS_DISABLED)) {
                return false;
            }
        }

        // Per-player state is only meaningful for players who are allowed to use /toggleenchant.
        if (!player.hasPermission(PERM_TOGGLE_ENCHANT)) {
            return enchantDisabled;
        }

        return enchantDisabled || Boolean.TRUE.equals(playerDisabled.get(player.getUniqueId()));
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender commandSender,
            @NotNull Command command, @NotNull String s,
            @NotNull String[] arguments) {
        if (!(commandSender instanceof Player)) {
            return true;
        }

        Player player = (Player) commandSender;

        if (enforceCommandPermissions()) {
            if (!player.hasPermission(PERM_TOGGLE_ENCHANT)) {
                return true;
            }
        }

        if (arguments.length > 0) {
            send(player, "messages.usage.toggleEnchant", "&cPoprawne użycie:&r /toggleenchant");
            return true;
        }

        UUID uuid = player.getUniqueId();
        boolean currentlyDisabled = playerDisabled.getOrDefault(uuid, true);
        boolean newDisabled = !currentlyDisabled;

        // Only players who successfully used the command are stored in data.
        playerDisabled.put(uuid, newDisabled);

        // Persist immediately so admins can edit the file without waiting for shutdown.
        saveData();

        if (newDisabled) {
            send(player, "messages.toggleEnchant.disabled", "&cKlikanie enchantów wyłączone.");
        } else {
            send(player, "messages.toggleEnchant.enabled", "&aKlikanie enchantów włączone.");
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender commandSender,
            @NotNull Command command,
            @NotNull String s,
            @NotNull String[] strings) {
        return Collections.emptyList();
    }
}
