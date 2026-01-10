package org.betterservernetwork.click2enchant.tools;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.betterservernetwork.click2enchant.Click2Enchant;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class Tools {
    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.legacyAmpersand();
    private static final String DEBUG_PERMISSION = "click2enchant.debug";

    public static Component colorize(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        return LEGACY_AMPERSAND.deserialize(text);
    }

    public static boolean playerHasSpace(Player player, ItemStack item) {
        int freeSlots = 0;
        int maxStackSize = item.getMaxStackSize();

        for (ItemStack invItem : player.getInventory().getStorageContents()) {
            if (invItem == null) {
                freeSlots += maxStackSize;
            } else if (invItem.isSimilar(item)) {
                int amount = invItem.getAmount();
                freeSlots += maxStackSize - amount;
            }
        }

        return freeSlots >= item.getAmount();
    }

    public static boolean isDebugEnabled() {
        Click2Enchant plugin = Click2Enchant.getInstance();
        return plugin != null && plugin.getConfig().getBoolean("debug", false);
    }

    public static void debug(String message) {
        if (!isDebugEnabled() || message == null) return;
        Bukkit.getLogger().info("[Click2Enchant:debug] " + message);
    }

    public static void debug(Player player, String message) {
        if (!isDebugEnabled() || message == null) return;

        String playerName = player != null ? player.getName() : "<null>";
        debug(message + " (player=" + playerName + ")");

        if (player == null) return;
        if (!player.hasPermission(DEBUG_PERMISSION)) return;

        Click2Enchant plugin = Click2Enchant.getInstance();
        String prefix = plugin != null ? plugin.getConfig().getString("messages.prefix", "") : "";
        player.sendMessage(colorize(prefix + "[DEBUG] " + message));
    }
}
