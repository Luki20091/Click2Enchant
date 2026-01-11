package org.betterservernetwork.click2enchant;

import org.betterservernetwork.click2enchant.tools.Tools;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class ClickHandler implements Listener {
    private static final String PERM_MECHANIC_ENCHANT = "click2enchant.mechanic.enchant";
    private static final String PERM_MECHANIC_COMBINE_BOOKS = "click2enchant.mechanic.combinebooks";
    private static final String PERM_MECHANIC_DISENCHANT = "click2enchant.mechanic.disenchant";
    private static final String PERM_MECHANIC_SPLIT_BOOK = "click2enchant.mechanic.splitbook";
    private static final String PERM_BASE_ACCESS = "click2enchant.command.toggleenchant";

    private final DisableCommandHandler disableHandler;

    public ClickHandler(DisableCommandHandler disableHandler) {
        this.disableHandler = disableHandler;
    }

    private boolean requirePlayerInventoryScreen() {
        Click2Enchant plugin = Click2Enchant.getInstance();
        return plugin != null && plugin.getConfig().getBoolean("ui.requirePlayerInventoryScreen", true);
    }

    private boolean isPlayerInventoryScreen(InventoryClickEvent event) {
        if (event == null || event.getView() == null || event.getView().getTopInventory() == null) return true;
        return event.getView().getTopInventory().getType() == InventoryType.CRAFTING;
    }

    private boolean isIgnoredMaterial(Material material) {
        if (material == null) return false;
        if (material.isAir()) return true;

        Click2Enchant plugin = Click2Enchant.getInstance();
        if (plugin == null) return false;

        String name = material.name();

        for (String raw : plugin.getConfig().getStringList("filters.bannedItems")) {
            if (raw == null || raw.isBlank()) continue;
            Material m = Material.matchMaterial(raw.trim());
            if (m != null && m == material) return true;
            if (raw.trim().equalsIgnoreCase(name)) return true;
        }

        for (String raw : plugin.getConfig().getStringList("filters.bannedMaterialNameSuffixes")) {
            if (raw == null || raw.isBlank()) continue;
            String suffix = raw.trim().toUpperCase(Locale.ROOT);
            if (!suffix.isEmpty() && name.endsWith(suffix)) return true;
        }

        for (String raw : plugin.getConfig().getStringList("filters.bannedMaterialNamePrefixes")) {
            if (raw == null || raw.isBlank()) continue;
            String prefix = raw.trim().toUpperCase(Locale.ROOT);
            if (!prefix.isEmpty() && name.startsWith(prefix)) return true;
        }

        return false;
    }

    private boolean hasBaseAccess(Player player) {
        return player != null && player.hasPermission(PERM_BASE_ACCESS);
    }

    private void playSound(Player player, Sound sound) {
        playSound(player, sound, 1.0f, 1.0f);
    }

    private void playSound(Player player, Sound sound, float volume, float pitch) {
        if (player == null || sound == null) return;
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    private void playFailXpSound(Player player, String mechanicKey) {
        if ("enchant".equalsIgnoreCase(mechanicKey)) {
            playSound(player, Sound.BLOCK_ANVIL_LAND, 1.0f, 0.6f);
            return;
        }

        // disenchant/splitBook
        playSound(player, Sound.BLOCK_GRINDSTONE_USE, 1.0f, 0.6f);
    }

    private void playFailSpaceSound(Player player, String mechanicKey) {
        if ("enchant".equalsIgnoreCase(mechanicKey)) {
            playSound(player, Sound.BLOCK_ANVIL_LAND, 1.0f, 0.6f);
            return;
        }

        playSound(player, Sound.BLOCK_GRINDSTONE_USE, 1.0f, 0.6f);
    }

    private boolean xpEnabled(String mechanicKey) {
        Click2Enchant plugin = Click2Enchant.getInstance();
        if (plugin == null) return true;
        if (!plugin.getConfig().getBoolean("xp.enabled", true)) return false;
        return plugin.getConfig().getBoolean("xp.costs." + mechanicKey + ".enabled", true);
    }

    private boolean bypassXpForPlayer(Player player) {
        Click2Enchant plugin = Click2Enchant.getInstance();
        boolean bypassCreative = plugin == null || plugin.getConfig().getBoolean("xp.bypassCreative", true);
        return bypassCreative && player.getGameMode() == GameMode.CREATIVE;
    }

    private int xpForNextLevel(int level) {
        if (level <= 15) return 2 * level + 7;
        if (level <= 30) return 5 * level - 38;
        return 9 * level - 158;
    }

    private int totalXpAtLevel(int level) {
        if (level <= 0) return 0;
        if (level <= 16) return level * level + 6 * level;
        if (level <= 31) return (int) Math.round(2.5D * level * level - 40.5D * level + 360D);
        return (int) Math.round(4.5D * level * level - 162.5D * level + 2220D);
    }

    private int getTotalExpPoints(Player player) {
        if (player == null) return 0;
        int level = Math.max(0, player.getLevel());
        float progress = Math.max(0f, Math.min(1f, player.getExp()));
        int xpIntoLevel = Math.round(progress * xpForNextLevel(level));
        return totalXpAtLevel(level) + xpIntoLevel;
    }

    private void setTotalExpPoints(Player player, int totalExp) {
        if (player == null) return;
        int safe = Math.max(0, totalExp);

        // Standard Bukkit pattern for setting total exp reliably.
        player.setExp(0f);
        player.setLevel(0);
        player.setTotalExperience(0);
        if (safe > 0) {
            player.giveExp(safe);
        }
    }

    private double xpCostForLevel(String mechanicKey, int level) {
        if (level <= 0) return 0D;

        Click2Enchant plugin = Click2Enchant.getInstance();
        if (plugin == null) {
            return (double) level;
        }

        String basePath = "xp.costs." + mechanicKey;
        double defaultPerLevel = plugin.getConfig().getDouble(basePath + ".defaultPerLevel", 1.0D);

        // Try override: xp.costs.<mechanic>.perLevel.<level>
        String overridePath = basePath + ".perLevel." + level;
        if (plugin.getConfig().contains(overridePath)) {
            double v = plugin.getConfig().getDouble(overridePath, level * defaultPerLevel);
            return Math.max(0D, v);
        }

        return Math.max(0D, level * defaultPerLevel);
    }

    private String formatLevels(double value) {
        double rounded = Math.round(value * 100.0D) / 100.0D;
        if (Math.abs(rounded - Math.rint(rounded)) < 1e-9) {
            return String.valueOf((int) Math.rint(rounded));
        }
        String s = String.format(Locale.US, "%.2f", rounded);
        while (s.contains(".") && (s.endsWith("0") || s.endsWith("."))) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private int pointsForLevelCost(Player player, double levelCost) {
        if (player == null) return 0;
        if (levelCost <= 0D) return 0;

        // Kept for backward compatibility with older code paths.
        // NOTE: XP per level is non-linear; use expPointsToRemoveForLevels/expPointsToAddForLevels for accuracy.
        int currentLevel = Math.max(0, player.getLevel());
        int pointsPerLevel = Math.max(1, xpForNextLevel(currentLevel));
        double points = levelCost * pointsPerLevel;
        return (int) Math.max(0, Math.ceil(points));
    }

    /**
     * Converts a "cost in XP levels" to the equivalent XP points to REMOVE from the player,
     * taking into account that XP per level is non-linear.
     */
    private int expPointsToRemoveForLevels(Player player, double levelCost) {
        if (player == null) return 0;
        if (levelCost <= 0D) return 0;

        int level = Math.max(0, player.getLevel());
        double remainingLevels = Math.max(0D, levelCost);
        double points = 0D;

        // Removing 1 "level" corresponds to subtracting the XP required to go from (level) -> (level-1),
        // i.e. xpForNextLevel(level-1). Preserve current progress by operating on total XP points.
        while (remainingLevels > 1e-9 && level > 0) {
            double step = Math.min(1D, remainingLevels);
            int pointsForThisLevel = Math.max(0, xpForNextLevel(level - 1));
            points += step * pointsForThisLevel;
            remainingLevels -= step;
            if (step >= 1D - 1e-9) {
                level--;
            }
        }

        // Not enough levels available to pay this cost.
        if (remainingLevels > 1e-6) {
            return Integer.MAX_VALUE;
        }

        return (int) Math.max(0, Math.ceil(points));
    }

    /**
     * Converts a "refund in XP levels" to the equivalent XP points to ADD to the player.
     */
    private int expPointsToAddForLevels(Player player, double levelAmount) {
        if (player == null) return 0;
        if (levelAmount <= 0D) return 0;

        int level = Math.max(0, player.getLevel());
        double remainingLevels = Math.max(0D, levelAmount);
        double points = 0D;

        // Adding 1 "level" corresponds to adding the XP required to go from (level) -> (level+1),
        // i.e. xpForNextLevel(level).
        while (remainingLevels > 1e-9) {
            double step = Math.min(1D, remainingLevels);
            int pointsForThisLevel = Math.max(0, xpForNextLevel(level));
            points += step * pointsForThisLevel;
            remainingLevels -= step;
            if (step >= 1D - 1e-9) {
                level++;
            }
        }

        return (int) Math.max(0, Math.ceil(points));
    }

    private void sendNotEnoughXp(Player player, double costLevels) {
        Click2Enchant plugin = Click2Enchant.getInstance();
        String prefix = plugin != null ? plugin.getConfig().getString("messages.prefix", "") : "";
        String msg = plugin != null
                ? plugin.getConfig().getString("messages.notEnoughXp", "&cNie masz wystarczająco poziomów XP. Wymagane: &e{cost}&c.")
                : "&cNie masz wystarczająco poziomów XP. Wymagane: &e{cost}&c.";
        msg = msg.replace("{cost}", formatLevels(costLevels));
        player.sendMessage(Tools.colorize(prefix + msg));
    }

    private int maxEnchantsPerItem() {
        Click2Enchant plugin = Click2Enchant.getInstance();
        int max = plugin != null ? plugin.getConfig().getInt("limits.maxEnchantsPerItem", 4) : 4;
        return Math.max(0, max);
    }

    private void sendMaxLimitedMessage(Player player, String messagePath, String fallback, int max) {
        Click2Enchant plugin = Click2Enchant.getInstance();
        String prefix = plugin != null ? plugin.getConfig().getString("messages.prefix", "") : "";
        String msg = plugin != null ? plugin.getConfig().getString(messagePath, fallback) : fallback;
        msg = msg.replace("{max}", String.valueOf(max));
        player.sendMessage(Tools.colorize(prefix + msg));
    }

    private void sendMendingBanned(Player player) {
        send(player, "messages.mendingBanned", "&cMending jest całkowicie zablokowany.");
    }

    private boolean containsMending(Map<Enchantment, Integer> enchants) {
        return enchants != null && enchants.containsKey(Enchantment.MENDING);
    }

    /**
     * Predicts the final enchant count after applying a stored-enchants book to an item,
     * following the same sequential conflict checks as the original handler.
     */
    private int predictedFinalEnchantCount(ItemStack targetItem, Map<Enchantment, Integer> storedFromCursor) {
        if (targetItem == null) return 0;
        if (storedFromCursor == null || storedFromCursor.isEmpty()) return targetItem.getEnchantments().size();

        Set<Enchantment> skip = new HashSet<>();
        for (Map.Entry<Enchantment, Integer> entry : storedFromCursor.entrySet()) {
            Enchantment enchantment = entry.getKey();
            Integer cursorLevel = entry.getValue();
            if (enchantment == null || cursorLevel == null) continue;

            Integer currentLevel = targetItem.getEnchantments().get(enchantment);
            if (currentLevel != null && currentLevel.intValue() == cursorLevel && enchantment.getMaxLevel() > currentLevel) {
                skip.add(enchantment);
            } else if (currentLevel != null) {
                skip.add(enchantment);
            }
        }

        Set<Enchantment> existing = new HashSet<>(targetItem.getEnchantments().keySet());
        for (Map.Entry<Enchantment, Integer> entry : storedFromCursor.entrySet()) {
            Enchantment enchantment = entry.getKey();
            Integer cursorLevel = entry.getValue();
            if (enchantment == null || cursorLevel == null) continue;
            if (skip.contains(enchantment)) continue;
            if (!enchantment.canEnchantItem(targetItem)) continue;

            boolean conflicts = false;
            for (Enchantment existingEnchant : existing) {
                if (enchantment.conflictsWith(existingEnchant) && enchantment != existingEnchant) {
                    conflicts = true;
                    break;
                }
            }
            if (conflicts) continue;

            existing.add(enchantment);
        }

        return existing.size();
    }

    private boolean tryChargeXpLevels(Player player, String mechanicKey, double costLevels) {
        if (costLevels <= 0D) return true;
        if (bypassXpForPlayer(player)) return true;

        int costPoints = expPointsToRemoveForLevels(player, costLevels);
        if (costPoints <= 0) return true;

        int total = getTotalExpPoints(player);
        if (costPoints == Integer.MAX_VALUE || total < costPoints) {
            sendNotEnoughXp(player, costLevels);
            return false;
        }

        setTotalExpPoints(player, total - costPoints);
        return true;
    }

    private void playVillagerNo(Player player) {
        playSound(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
    }

    private void refundXpLevels(Player player, double refundLevels) {
        if (refundLevels <= 0D) return;
        if (bypassXpForPlayer(player)) return;

        int refundPoints = expPointsToAddForLevels(player, refundLevels);
        if (refundPoints <= 0) return;
        int total = getTotalExpPoints(player);
        setTotalExpPoints(player, total + refundPoints);
    }

    private boolean mechanicsEnabled(String key, boolean fallback) {
        Click2Enchant plugin = Click2Enchant.getInstance();
        return plugin == null || plugin.getConfig().getBoolean("mechanics." + key + ".enabled", fallback);
    }

    private boolean enforceMechanicPermissions() {
        Click2Enchant plugin = Click2Enchant.getInstance();
        return plugin == null || plugin.getConfig().getBoolean("permissions.enforceMechanics", true);
    }

    private boolean sendNoPermission(String mechanicKey) {
        Click2Enchant plugin = Click2Enchant.getInstance();
        return plugin != null && plugin.getConfig().getBoolean("mechanics." + mechanicKey + ".sendNoPermissionMessage", false);
    }

    private void send(Player player, String messagePath, String fallback) {
        Click2Enchant plugin = Click2Enchant.getInstance();
        String prefix = plugin != null ? plugin.getConfig().getString("messages.prefix", "") : "";
        String msg = plugin != null ? plugin.getConfig().getString(messagePath, fallback) : fallback;
        player.sendMessage(Tools.colorize(prefix + msg));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();

        // Players without base permission are completely ignored by this plugin.
        if (!hasBaseAccess(player)) return;

        // When a non-player inventory (chest/anvil/etc) is open, optionally disable mechanics entirely.
        if (requirePlayerInventoryScreen() && !isPlayerInventoryScreen(event)) return;

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        if (!mechanicsEnabled("enchant", true)) return;

        if (current == null || current.getType().isAir()) return;
        if (isIgnoredMaterial(current.getType())) return;
        if (event.getClick() != ClickType.LEFT) return;
        if (cursor == null || cursor.getType() != Material.ENCHANTED_BOOK) return;
        if (!(cursor.getItemMeta() instanceof EnchantmentStorageMeta)) return;

        Tools.debug(player, "Enchant click detected: current=" + current.getType() + ", cursor=ENCHANTED_BOOK");

        if (disableHandler.isDisabled(player)) {
            Tools.debug(player, "Blocked: disabled (global/per-player)");
            playVillagerNo(player);
            return;
        }

        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) cursor.getItemMeta();

        if (!meta.hasStoredEnchants()) {
            Tools.debug(player, "Blocked: cursor book has no stored enchants");
            playVillagerNo(player);
            return;
        }

        Map<Enchantment, Integer> storedFromCursorPreview = meta.getStoredEnchants();
        if (containsMending(storedFromCursorPreview)) {
            sendMendingBanned(player);
            Tools.debug(player, "Blocked: cursor contains MENDING");
            playVillagerNo(player);
            return;
        }

        // Separate toggle for book->book combining.
        if (current.getType() == Material.ENCHANTED_BOOK && !mechanicsEnabled("combineBooks", true)) {
            Tools.debug(player, "Blocked: mechanics.combineBooks.enabled=false");
            playVillagerNo(player);
            return;
        }

        // Enchant count limits apply only when applying to non-book items.
        if (current.getType() != Material.ENCHANTED_BOOK) {
            int max = maxEnchantsPerItem();
            if (max > 0) {
                int existingCount = current.getEnchantments().size();
                if (existingCount > max) {
                    sendMaxLimitedMessage(player, "messages.itemHasTooManyEnchants", "&cNie można: ten item ma więcej niż &e{max}&c enchanty.", max);
                    Tools.debug(player, "Blocked: item already has too many enchants (" + existingCount + ">" + max + ")");
                    playVillagerNo(player);
                    return;
                }

                // If item has Mending already, block any plugin-enchanting interaction.
                if (current.getEnchantments().containsKey(Enchantment.MENDING)) {
                    sendMendingBanned(player);
                    Tools.debug(player, "Blocked: item already has MENDING");
                    playVillagerNo(player);
                    return;
                }

                int predicted = predictedFinalEnchantCount(current, storedFromCursorPreview);
                if (predicted > max) {
                    sendMaxLimitedMessage(player, "messages.tooManyEnchants", "&cNie można nałożyć więcej niż &e{max}&c enchantów na item.", max);
                    Tools.debug(player, "Blocked: predicted enchants exceed max (" + predicted + ">" + max + ")");
                    playVillagerNo(player);
                    return;
                }
            }
        }

        boolean chargeXp = xpEnabled("enchant");
        ItemStack currentBefore = chargeXp ? current.clone() : null;
        ItemStack cursorBefore = chargeXp ? cursor.clone() : null;
        Map<Enchantment, Integer> storedFromCursorBefore = meta.getStoredEnchants();

        event.setCancelled(true);

        Map<Enchantment, Integer> storedFromCursor = storedFromCursorBefore;

        if (current.getType() == Material.ENCHANTED_BOOK &&
                current.getItemMeta() instanceof EnchantmentStorageMeta) {
            EnchantmentStorageMeta currentBookMeta = (EnchantmentStorageMeta) current.getItemMeta();
            if (currentBookMeta != null && containsMending(currentBookMeta.getStoredEnchants())) {
                sendMendingBanned(player);
                Tools.debug(player, "Blocked: target book contains MENDING");
                playVillagerNo(player);
                return;
            }

            if (enforceMechanicPermissions() && !player.hasPermission(PERM_MECHANIC_COMBINE_BOOKS)) {
                if (sendNoPermission("combineBooks")) {
                    send(player, "messages.noPermission", "&cBrak uprawnień.");
                }
                Tools.debug(player, "Blocked: missing permission " + PERM_MECHANIC_COMBINE_BOOKS);
                playVillagerNo(player);
                return;
            }

            EnchantmentStorageMeta meta2 = (EnchantmentStorageMeta) current.getItemMeta();

            if (!meta2.hasStoredEnchants()) return;

            EnchantmentStorageMeta meta3 = null;

            Map<Enchantment, Integer> storedOnCurrentBook = meta2.getStoredEnchants();

            for (Map.Entry<Enchantment, Integer> entry : storedFromCursor.entrySet()) {
                Enchantment enchantment = entry.getKey();
                Integer cursorLevel = entry.getValue();
                if (cursorLevel == null) {
                    continue;
                }

                Integer currentLevel = storedOnCurrentBook.get(enchantment);

                if (currentLevel != null && currentLevel.intValue() == cursorLevel && enchantment.getMaxLevel() > currentLevel) {
                    meta2.removeStoredEnchant(enchantment);
                    meta2.addStoredEnchant(enchantment, cursorLevel + 1, true);
                } else if (currentLevel != null) {
                    if (meta3 == null) {
                        meta3 = (EnchantmentStorageMeta) (new ItemStack(Material.ENCHANTED_BOOK)).getItemMeta();
                    }
                    meta3.addStoredEnchant(enchantment, cursorLevel, true);
                } else {
                    meta2.addStoredEnchant(enchantment, cursorLevel, true);
                }
            }

            if (meta3 != null) {
                ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
                item.setItemMeta(meta3);
                player.setItemOnCursor(item);
            } else {
                player.setItemOnCursor(new ItemStack(Material.AIR));
            }

            current.setItemMeta(meta2);
            event.setCurrentItem(current);
            // Cursor already updated via Player#setItemOnCursor above.

            if (chargeXp) {
                double cost = 0D;
                ItemStack afterCursor = player.getItemOnCursor();
                Map<Enchantment, Integer> remaining = Map.of();
                if (afterCursor != null && afterCursor.getType() == Material.ENCHANTED_BOOK && afterCursor.getItemMeta() instanceof EnchantmentStorageMeta) {
                    remaining = ((EnchantmentStorageMeta) afterCursor.getItemMeta()).getStoredEnchants();
                }

                for (Map.Entry<Enchantment, Integer> entry : storedFromCursorBefore.entrySet()) {
                    Enchantment enchantment = entry.getKey();
                    Integer lvl = entry.getValue();
                    if (lvl == null) continue;
                    Integer stillOnCursor = remaining.get(enchantment);
                    if (stillOnCursor != null && stillOnCursor.intValue() == lvl.intValue()) {
                        continue;
                    }
                    cost += xpCostForLevel("enchant", lvl);
                }

                if (!tryChargeXpLevels(player, "enchant", cost)) {
                    event.setCurrentItem(currentBefore);
                    player.setItemOnCursor(cursorBefore);
                    playVillagerNo(player);
                    return;
                }
            }

            playSound(player, Sound.BLOCK_ANVIL_USE);
            Tools.debug(player, "CombineBooks success");

            return;
        }

        // Applying enchants to non-book items
        if (enforceMechanicPermissions() && !player.hasPermission(PERM_MECHANIC_ENCHANT)) {
            if (sendNoPermission("enchant")) {
                send(player, "messages.noPermission", "&cBrak uprawnień.");
            }
            Tools.debug(player, "Blocked: missing permission " + PERM_MECHANIC_ENCHANT);
            playVillagerNo(player);
            return;
        }

        EnchantmentStorageMeta meta2 = (EnchantmentStorageMeta) cursor.getItemMeta();
        Set<Enchantment> skip = new HashSet<>();

        boolean anyApplied = false;

        for (Map.Entry<Enchantment, Integer> entry : storedFromCursor.entrySet()) {
            Enchantment enchantment = entry.getKey();
            Integer cursorLevel = entry.getValue();
            if (cursorLevel == null) {
                continue;
            }

            Integer currentLevel = current.getEnchantments().get(enchantment);
            if (currentLevel != null && currentLevel.intValue() == cursorLevel && enchantment.getMaxLevel() > currentLevel) {
                skip.add(enchantment);
                meta2.removeStoredEnchant(enchantment);
                current.removeEnchantment(enchantment);
                current.addEnchantment(enchantment, cursorLevel + 1);
                anyApplied = true;
            } else if (currentLevel != null) {
                skip.add(enchantment);
            }
        }

        loop:
        for (Map.Entry<Enchantment, Integer> entry : storedFromCursor.entrySet()) {
            Enchantment enchantment = entry.getKey();
            Integer cursorLevel = entry.getValue();
            if (cursorLevel == null) {
                continue;
            }

            if (!enchantment.canEnchantItem(current) || skip.contains(enchantment)) {
                continue;
            }

            // Intentionally re-read enchantments to preserve original behavior
            // in case earlier operations in this handler changed them.
            for (Enchantment enchantment2 : current.getEnchantments().keySet()) {
                if (enchantment.conflictsWith(enchantment2) && enchantment != enchantment2) {
                    continue loop;
                }
            }

            meta2.removeStoredEnchant(enchantment);
            current.addEnchantment(enchantment, cursorLevel);
            anyApplied = true;
        }

        // If nothing could be applied (e.g. book has enchants but none fit/conflict), signal failure.
        if (!anyApplied) {
            Tools.debug(player, "No applicable enchants for this item");
            playVillagerNo(player);
            return;
        }

        if (meta2.hasStoredEnchants()) {
            cursor.setItemMeta(meta2);
            player.setItemOnCursor(cursor);
        } else {
            player.setItemOnCursor(new ItemStack(Material.AIR));
        }

        if (chargeXp) {
            double cost = 0D;
            ItemStack afterCursor = player.getItemOnCursor();
            Map<Enchantment, Integer> remaining = Map.of();
            if (afterCursor != null && afterCursor.getType() == Material.ENCHANTED_BOOK && afterCursor.getItemMeta() instanceof EnchantmentStorageMeta) {
                remaining = ((EnchantmentStorageMeta) afterCursor.getItemMeta()).getStoredEnchants();
            }

            for (Map.Entry<Enchantment, Integer> entry : storedFromCursorBefore.entrySet()) {
                Enchantment enchantment = entry.getKey();
                Integer lvl = entry.getValue();
                if (lvl == null) continue;
                Integer stillOnCursor = remaining.get(enchantment);
                if (stillOnCursor != null && stillOnCursor.intValue() == lvl.intValue()) {
                    continue;
                }
                cost += xpCostForLevel("enchant", lvl);
            }

            if (!tryChargeXpLevels(player, "enchant", cost)) {
                event.setCurrentItem(currentBefore);
                player.setItemOnCursor(cursorBefore);
                playVillagerNo(player);
                return;
            }
        }

        playSound(player, Sound.BLOCK_ANVIL_USE);
        event.setCurrentItem(current);
        Tools.debug(player, "Enchant apply success");
    }

    @EventHandler
    public void onInventoryRightClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();

        // Players without base permission are completely ignored by this plugin.
        if (!hasBaseAccess(player)) return;

        if (requirePlayerInventoryScreen() && !isPlayerInventoryScreen(event)) return;

        ItemStack current = event.getCurrentItem();

        if (!mechanicsEnabled("disenchant", true)) return;

        if (current == null) return;
        if (event.getClick() != ClickType.RIGHT) return;
        if (current.getType().isAir()) return;
        if (isIgnoredMaterial(current.getType())) return;
        if (current.getEnchantments().isEmpty()) return;

        Tools.debug(player, "Disenchant click detected: current=" + current.getType() + ", enchants=" + current.getEnchantments().size());

        // Block disenchant if item has too many enchants
        int max = maxEnchantsPerItem();
        if (max > 0) {
            int existingCount = current.getEnchantments().size();
            if (existingCount > max) {
                sendMaxLimitedMessage(player, "messages.itemHasTooManyEnchants", "&cNie można: ten item ma więcej niż &e{max}&c enchanty.", max);
                Tools.debug(player, "Blocked: disenchant denied because item has too many enchants (" + existingCount + ">" + max + ")");
                return;
            }
        }

        // Mending is fully banned
        if (current.getEnchantments().containsKey(Enchantment.MENDING)) {
            sendMendingBanned(player);
            Tools.debug(player, "Blocked: disenchant denied because item has MENDING");
            return;
        }

        if (disableHandler.isDisabled(player)) {
            Tools.debug(player, "Blocked: disabled (global/per-player)");
            return;
        }

        if (enforceMechanicPermissions() && !player.hasPermission(PERM_MECHANIC_DISENCHANT)) {
            if (sendNoPermission("disenchant")) {
                send(player, "messages.noPermission", "&cBrak uprawnień.");
            }
            Tools.debug(player, "Blocked: missing permission " + PERM_MECHANIC_DISENCHANT);
            return;
        }

        event.setCancelled(true);

        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        if (Tools.playerHasSpace(player, item)) {
            Map<Enchantment, Integer> beforeEnchants = new HashMap<>(current.getEnchantments());
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
            double removedLevelCost = 0D;

            for (Map.Entry<Enchantment, Integer> entry : beforeEnchants.entrySet()) {
                Enchantment enchantment = entry.getKey();
                Integer level = entry.getValue();
                if (enchantment == null || level == null) continue;

                current.removeEnchantment(enchantment);
                boolean removed = !current.getEnchantments().containsKey(enchantment);
                if (!removed) {
                    continue;
                }

                meta.addStoredEnchant(enchantment, level, true);
                if (xpEnabled("disenchant")) {
                    removedLevelCost += xpCostForLevel("disenchant", level);
                }
            }

            if (!meta.hasStoredEnchants()) {
                Tools.debug(player, "Disenchant: nothing could be removed");
                return;
            }

            item.setItemMeta(meta);
            player.getInventory().addItem(item);
            event.setCurrentItem(current);

            if (xpEnabled("disenchant") && removedLevelCost > 0D) {
                Click2Enchant plugin = Click2Enchant.getInstance();
                double refundPercent = plugin != null ? plugin.getConfig().getDouble("xp.costs.disenchant.refundPercent", 0.25D) : 0.25D;
                double refundLevels = removedLevelCost * Math.max(0D, refundPercent);
                refundXpLevels(player, refundLevels);
                Tools.debug(player, "Disenchant refundLevels=" + formatLevels(refundLevels));
            }

            playSound(player, Sound.BLOCK_GRINDSTONE_USE);
            Tools.debug(player, "Disenchant success");
        } else {
            send(player, "messages.notEnoughSpace", "&cNie masz wystarczająco miejsca w ekwipunku.");
            playFailSpaceSound(player, "disenchant");
            Tools.debug(player, "Blocked: not enough space");
        }
    }

    @EventHandler
    public void onInventoryRightClickBook(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();

        // Players without base permission are completely ignored by this plugin.
        if (!hasBaseAccess(player)) return;

        if (requirePlayerInventoryScreen() && !isPlayerInventoryScreen(event)) return;

        ItemStack current = event.getCurrentItem();

        if (!mechanicsEnabled("splitBook", true)) return;

        if (current == null) return;
        if (event.getClick() != ClickType.RIGHT) return;
        if (current.getType().isAir()) return;
        if (current.getType() != Material.ENCHANTED_BOOK) return;
        if (isIgnoredMaterial(current.getType())) return;

        Tools.debug(player, "SplitBook click detected: current=ENCHANTED_BOOK");

        if (disableHandler.isDisabled(player)) {
            Tools.debug(player, "Blocked: disabled (global/per-player)");
            return;
        }

        if (enforceMechanicPermissions() && !player.hasPermission(PERM_MECHANIC_SPLIT_BOOK)) {
            if (sendNoPermission("splitBook")) {
                send(player, "messages.noPermission", "&cBrak uprawnień.");
            }
            Tools.debug(player, "Blocked: missing permission " + PERM_MECHANIC_SPLIT_BOOK);
            return;
        }

        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) current.getItemMeta();
        if (!meta.hasStoredEnchants()) {
            Tools.debug(player, "Blocked: book has no stored enchants");
            return;
        }

        // Mending is fully banned
        if (containsMending(meta.getStoredEnchants())) {
            sendMendingBanned(player);
            Tools.debug(player, "Blocked: splitBook denied because book contains MENDING");
            return;
        }

        if (xpEnabled("splitBook")) {
            Click2Enchant plugin = Click2Enchant.getInstance();
            double flat = plugin != null ? plugin.getConfig().getDouble("xp.costs.splitBook.flat", 1.0D) : 1.0D;
            double cost = Math.max(0D, flat);
            if (!tryChargeXpLevels(player, "splitBook", cost)) {
                playFailXpSound(player, "splitBook");
                return;
            }
        }

        event.setCancelled(true);

        Map<Enchantment, Integer> stored = meta.getStoredEnchants();
        Enchantment firstEnchantment = null;
        int firstLevel = 0;
        for (Map.Entry<Enchantment, Integer> entry : stored.entrySet()) {
            firstEnchantment = entry.getKey();
            firstLevel = entry.getValue();
            break;
        }

        if (firstEnchantment == null) {
            return;
        }

        if (stored.size() > 1) {
            if (playerHasSpace(player, stored.size())) {
                for (Map.Entry<Enchantment, Integer> entry : stored.entrySet()) {
                    player.getInventory().addItem(getEnchantedBook(entry.getKey(), entry.getValue()));
                }

                event.setCurrentItem(new ItemStack(Material.AIR));

                playSound(player, Sound.BLOCK_GRINDSTONE_USE);
                Tools.debug(player, "SplitBook success (multi-enchant)");
            } else {
                send(player, "messages.notEnoughSpace", "&cNie masz wystarczająco miejsca w ekwipunku.");
                playFailSpaceSound(player, "splitBook");
                Tools.debug(player, "Blocked: not enough space");
            }
        } else if (firstLevel > 1) {
            if (playerHasSpace(player, 2)) {
                player.getInventory().addItem(
                        getEnchantedBook(firstEnchantment, firstLevel-1),
                        getEnchantedBook(firstEnchantment, firstLevel-1));

                event.setCurrentItem(new ItemStack(Material.AIR));

                playSound(player, Sound.BLOCK_GRINDSTONE_USE);
                Tools.debug(player, "SplitBook success (level split)");
            } else {
                send(player, "messages.notEnoughSpace", "&cNie masz wystarczająco miejsca w ekwipunku.");
                playFailSpaceSound(player, "splitBook");
                Tools.debug(player, "Blocked: not enough space");
            }
        }
    }

    public static boolean playerHasSpace(Player player, int items) {
        int freeSlots = 0;
        int maxStackSize = 1;

        for (ItemStack invItem : player.getInventory().getStorageContents()) {
            if (invItem == null) {
                freeSlots += maxStackSize;
            }
        }

        return freeSlots >= items;
    }

    private ItemStack getEnchantedBook(Enchantment enchantment, int level) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
        meta.addStoredEnchant(enchantment, level, true);
        item.setItemMeta(meta);
        return item;
    }
}
