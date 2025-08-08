package net.derfla.quickeconomy.util;

import net.derfla.quickeconomy.Main;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Chest;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Utilities for storing and querying ownership and shop state for chests via
 * Bukkit's persistent data container. Keys are stored as trimmed UUID strings
 * (or two UUIDs separated by a space) to represent one or two owners.
 */
public class BlockOwner {

    static Plugin plugin = Main.getInstance();
    static NamespacedKey lockedKey = new NamespacedKey(plugin, "playerLocked");
    static NamespacedKey shopOpenKey = new NamespacedKey(plugin, "shopOpen");

    /**
     * Check whether a chest is locked for the given player.
     *
     * @param chest      chest block instance
     * @param playerUUID trimmed UUID of the player
     * @return true if the chest is locked for the player; false if unlocked or owned by them
     */
    public static boolean isLockedForPlayer (Chest chest, String playerUUID) {
        if (!chest.getPersistentDataContainer().has(lockedKey)) return false;
        if (chest.getPersistentDataContainer().get(lockedKey, PersistentDataType.STRING) == null) return false;
        String nbt = chest.getPersistentDataContainer().get(lockedKey, PersistentDataType.STRING);
        if (nbt == null) return false;
        if (nbt.contains(" ")) {
            String[] splitNBT = nbt.split(" ");
            if (splitNBT[0].equals(playerUUID) || splitNBT[1].equals(playerUUID)) return false;
        }
        if (nbt.equals(playerUUID)) return false;
        return true;
    }

    /**
     * Check whether a chest has any lock owners set.
     *
     * @param chest chest block instance
     * @return true if a lock key is present; false otherwise
     */
    public static boolean isLocked (Chest chest) {
        if (!chest.getPersistentDataContainer().has(lockedKey)) return false;
        if (chest.getPersistentDataContainer().get(lockedKey, PersistentDataType.STRING) == null) return false;
        return true;
    }

    /**
     * Lock a chest to one or two players.
     *
     * @param chest       chest block instance
     * @param playerUUID  primary owner (trimmed UUID)
     * @param player2UUID optional secondary owner (trimmed UUID) or empty string
     */
    public static void setPlayerLocked(Chest chest, String playerUUID, String player2UUID) {
        String nbtValue;
        if (player2UUID.isEmpty()) {
            nbtValue = playerUUID;
        }else nbtValue = playerUUID + " " + player2UUID;
        chest.getPersistentDataContainer().set(lockedKey, PersistentDataType.STRING, nbtValue);
        chest.update();
        if (nbtValue.equals(playerUUID)) {
            plugin.getLogger().info("Locked chest to: " + playerUUID);
            return;
        }
        plugin.getLogger().info("Locked chest to: " + playerUUID + " & " + player2UUID);
    }

    /**
     * Remove any lock from the chest and log the action by a player name.
     *
     * @param chest      chest block instance
     * @param playerName the name of the player that initiated the unlock (for logging)
     */
    public static void unlockFromPlayer(Chest chest, String playerName) {
        String nbt = chest.getPersistentDataContainer().get(lockedKey, PersistentDataType.STRING);
        chest.getPersistentDataContainer().remove(lockedKey);
        chest.update();
        if (nbt == null) {
            plugin.getLogger().info("Unlocked a chest!");
            return;
        }
        if (nbt.contains(" ")) nbt = nbt.replace(" ", " & ");
        plugin.getLogger().info(playerName + " unlocked a chest from: " + nbt);
    }

    /**
     * Mark whether a chest is being used as an open shop.
     *
     * @param chest    chest block instance
     * @param shopOpen true if shop is open
     */
    public static void setShopOpen(Chest chest, boolean shopOpen) {
        chest.getPersistentDataContainer().set(shopOpenKey, PersistentDataType.BOOLEAN, shopOpen);
        chest.update();
    }

    /**
     * Check if the chest is marked as an open shop.
     *
     * @param chest chest block instance
     * @return true if open, false otherwise
     */
    public static boolean isShopOpen(Chest chest) {
        if (!chest.getPersistentDataContainer().has(shopOpenKey, PersistentDataType.BOOLEAN)) return false;
        return chest.getPersistentDataContainer().get(shopOpenKey, PersistentDataType.BOOLEAN);
    }
    /**
     * Check if the chest has the shop marker set (open or not).
     *
     * @param chest chest block instance
     * @return true if the shop marker key exists
     */
    public static boolean isShop(Chest chest) {
        return chest.getPersistentDataContainer().has(shopOpenKey, PersistentDataType.BOOLEAN);
    }

    /**
     * Convert legacy owner keys (player names) to trimmed UUIDs in-place.
     * Skips conversion if the key already matches new format.
     *
     * @param chest chest block instance
     */
    public static void convertChestKeyToUUID(Chest chest) {
        if (!chest.getPersistentDataContainer().has(lockedKey)) return;
        if (chest.getPersistentDataContainer().get(lockedKey, PersistentDataType.STRING) == null) return;
        String oldKey = chest.getPersistentDataContainer().get(lockedKey, PersistentDataType.STRING);
        // Check if oldKey already is new format
        if((oldKey.contains(" ") && oldKey.length() == 65) || oldKey.length() == 32) return;

        String newKey;
        if(oldKey.contains(" ")) {
            String[] splitOldKey = oldKey.split(" ");
            newKey = Balances.getUUID(splitOldKey[0]) + " " + Balances.getUUID(splitOldKey[1]);
        } else {
            newKey = Balances.getUUID(oldKey);
        }
        assert newKey != null;
        chest.getPersistentDataContainer().set(lockedKey, PersistentDataType.STRING, newKey);
        chest.update();
    }

    /**
     * Get the list of owner UUIDs for a chest lock.
     * Returns null if no lock is present.
     *
     * @param chest chest block instance
     * @return a list with one or two trimmed UUIDs, or null if not locked
     */
    public static List<String> getChestOwner(Chest chest){
        List<String> owner = new ArrayList<>();
        if (!chest.getPersistentDataContainer().has(lockedKey)) return null;
        if (chest.getPersistentDataContainer().get(lockedKey, PersistentDataType.STRING) == null) return null;
        String key = chest.getPersistentDataContainer().get(lockedKey, PersistentDataType.STRING);
        if(key.contains(" ")) {
            return List.of(key.split(" "));
        }
        owner.add(key);
        return owner;
    }

}
