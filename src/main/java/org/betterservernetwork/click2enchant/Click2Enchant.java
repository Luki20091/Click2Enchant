package org.betterservernetwork.click2enchant;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class Click2Enchant extends JavaPlugin {
    private static Click2Enchant instance;

    private DisableCommandHandler disableCommandHandler;

    public static Click2Enchant getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        disableCommandHandler = new DisableCommandHandler();
        GlobalEnchantCommand globalEnchantCommand = new GlobalEnchantCommand(disableCommandHandler);
        ReloadConfigCommand reloadConfigCommand = new ReloadConfigCommand();

        Bukkit.getPluginManager().registerEvents(disableCommandHandler, this);
        Bukkit.getPluginManager().registerEvents(new ClickHandler(disableCommandHandler), this);

        Objects.requireNonNull(getCommand("toggleenchant")).setExecutor(disableCommandHandler);
        Objects.requireNonNull(getCommand("toggleenchant")).setTabCompleter(disableCommandHandler);
        Objects.requireNonNull(getCommand("toggleglobalenchant")).setExecutor(globalEnchantCommand);
        Objects.requireNonNull(getCommand("toggleglobalenchant")).setTabCompleter(globalEnchantCommand);

        Objects.requireNonNull(getCommand("click2enchantreload")).setExecutor(reloadConfigCommand);
        Objects.requireNonNull(getCommand("click2enchantreload")).setTabCompleter(reloadConfigCommand);

        getLogger().info("Enabled.");
    }

    @Override
    public void onDisable() {
        if (disableCommandHandler != null) {
            disableCommandHandler.onDisable();
            disableCommandHandler = null;
        }

        instance = null;
    }
}
