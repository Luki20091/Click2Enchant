package org.betterservernetwork.click2enchant;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class GlobalEnchantCommand implements CommandExecutor, TabCompleter {
    private static final String PERM_TOGGLE_GLOBAL_ENCHANT = "click2enchant.toggleglobalenchant";

    private final DisableCommandHandler disableHandler;

    public GlobalEnchantCommand(DisableCommandHandler disableHandler) {
        this.disableHandler = disableHandler;
    }

    private boolean hasPermission(Player player) {
        Click2Enchant plugin = Click2Enchant.getInstance();
        if (plugin == null) return true;

        if (!plugin.getConfig().getBoolean("permissions.enforceCommands", true)) {
            return true;
        }

        return player.hasPermission(PERM_TOGGLE_GLOBAL_ENCHANT);
    }

    private void send(Player player, String messagePath, String fallback) {
        Click2Enchant plugin = Click2Enchant.getInstance();
        String prefix = plugin != null ? plugin.getConfig().getString("messages.prefix", "") : "";
        String msg = plugin != null ? plugin.getConfig().getString(messagePath, fallback) : fallback;
        player.sendMessage(org.betterservernetwork.click2enchant.tools.Tools.colorize(prefix + msg));
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

        if (!hasPermission(player)) {
            return true;
        }

        if (arguments.length > 0) {
            send(player, "messages.usage.toggleGlobalEnchant", "&cPoprawne użycie:&r /toggleglobalenchant");
            return true;
        }

        disableHandler.enchantDisabled = !disableHandler.enchantDisabled;

        if (disableHandler.enchantDisabled) {
            send(player, "messages.globalEnchant.disabled", "&cKlikanie enchantów wyłączone globalnie.");
        } else {
            send(player, "messages.globalEnchant.enabled", "&aKlikanie enchantów włączone globalnie.");
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender commandSender,
            @NotNull Command command,
            @NotNull String s,
            @NotNull String[] strings) {
        return new ArrayList<>();
    }
}
