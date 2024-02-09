package net.derfla.quickeconomy.util;

import net.derfla.quickeconomy.Main;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Chest;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public class BlockOwner {

    static Plugin plugin = Main.getInstance();
    public static boolean isLockedForPlayer (Chest chest, String player) {
        NamespacedKey lockedKey = new NamespacedKey(plugin, "playerLocked");
        if (!chest.getPersistentDataContainer().has(lockedKey)) return false;
        if (chest.getPersistentDataContainer().get(lockedKey, PersistentDataType.STRING) == null) return false;
        if (chest.getPersistentDataContainer().get(lockedKey, PersistentDataType.STRING).equals(player)) return false;
        return true;
    }

    public static boolean isLocked (Chest chest) {
        NamespacedKey lockedKey = new NamespacedKey(plugin, "playerLocked");
        if (!chest.getPersistentDataContainer().has(lockedKey)) return false;
        if (chest.getPersistentDataContainer().get(lockedKey, PersistentDataType.STRING) == null) return false;
        return true;
    }

    public static void setPlayerOwned(Chest chest, String player, boolean locked) {
        NamespacedKey lockedKey = new NamespacedKey(plugin, "playerLocked");
        if (locked){
            chest.getPersistentDataContainer().set(lockedKey, PersistentDataType.STRING, player);
            chest.update();
            plugin.getLogger().info("Locked chest to: " + player);
        }else {
            String owner = chest.getPersistentDataContainer().get(lockedKey, PersistentDataType.STRING);
            chest.getPersistentDataContainer().remove(lockedKey);
            chest.update();
            plugin.getLogger().info(player + " unlocked a chest from: " + owner);
        }

    }
}
