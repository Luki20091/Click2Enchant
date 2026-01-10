package org.betterservernetwork.click2enchant;

import org.betterservernetwork.click2enchant.tools.Tools;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class ReloadConfigCommand implements CommandExecutor, TabCompleter {
    private static final String PERM_RELOAD = "click2enchant.command.reload";

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        Click2Enchant plugin = Click2Enchant.getInstance();
        if (plugin == null) {
            return true;
        }

        if (sender instanceof Player player) {
            if (!player.hasPermission(PERM_RELOAD)) {
                return true;
            }
        }

        plugin.reloadConfig();

        String prefix = plugin.getConfig().getString("messages.prefix", "");
        String msg = plugin.getConfig().getString("messages.reloadConfig", "&aKonfiguracja przeładowana.");
        sender.sendMessage(Tools.colorize(prefix + msg));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        return Collections.emptyList();
    }
}
