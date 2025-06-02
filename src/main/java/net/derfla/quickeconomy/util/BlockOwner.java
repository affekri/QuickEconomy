package net.derfla.quickeconomy.util;

import net.derfla.quickeconomy.Main;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Chest;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

public class BlockOwner {

    static Plugin plugin = Main.getInstance();
    static NamespacedKey lockedKey = new NamespacedKey(plugin, "playerLocked");
    static NamespacedKey shopOpenKey = new NamespacedKey(plugin, "shopOpen");

    public static boolean isLockedForPlayer (Chest chest, String playerUUID) {
        if (!chest.getPersistentDataContainer().has(lockedKey)) return false;
        String nbt = chest.getPersistentDataContainer().get(lockedKey, PersistentDataType.STRING);
        if (nbt == null || nbt.isEmpty()) return false; // Check for null or empty
        if (nbt.contains(" ")) {
            String[] splitNBT = nbt.split(" ");
            if (splitNBT[0].equals(playerUUID) || splitNBT[1].equals(playerUUID)) return false;
        }
        if (nbt.equals(playerUUID)) return false;
        return true;
    }

    public static boolean isLocked (Chest chest) {
        if (!chest.getPersistentDataContainer().has(lockedKey)) return false;
        String nbt = chest.getPersistentDataContainer().get(lockedKey, PersistentDataType.STRING);
        return nbt != null && !nbt.isEmpty(); // Check for null or empty
    }

    public static void setPlayerLocked(Chest chest, String playerUUID, String player2UUID) {
        // Ensure playerUUID (owner1) is not null or empty before proceeding
        if (playerUUID == null || playerUUID.isEmpty()) {
            plugin.getLogger().warning("[BlockOwner] Attempted to lock chest at " + chest.getLocation() + " with null or empty primary owner UUID. Key will not be set.");
            return; 
        }

        String nbtValue;
        if (player2UUID == null || player2UUID.isEmpty()) {
            nbtValue = playerUUID;
        } else {
            nbtValue = playerUUID + " " + player2UUID;
        }
        plugin.getLogger().info("[BlockOwner Debug] SETTING playerLocked PDC for chest at " + chest.getLocation() + ". Value: '" + nbtValue + "'. (Owner1: " + playerUUID + ", Owner2: " + player2UUID + ")");
        chest.getPersistentDataContainer().set(lockedKey, PersistentDataType.STRING, nbtValue);
        chest.update();
    }

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

    public static void setShopOpen(Chest chest, boolean shopOpen) {
        chest.getPersistentDataContainer().set(shopOpenKey, PersistentDataType.BOOLEAN, shopOpen);
        chest.update();
    }

    public static boolean isShopOpen(Chest chest) {
        if (!chest.getPersistentDataContainer().has(shopOpenKey, PersistentDataType.BOOLEAN)) return false;
        Boolean val = chest.getPersistentDataContainer().get(shopOpenKey, PersistentDataType.BOOLEAN);
        return val != null && val;
    }
    public static boolean isShop(Chest chest) {
        return chest.getPersistentDataContainer().has(lockedKey) || chest.getPersistentDataContainer().has(shopOpenKey, PersistentDataType.BOOLEAN);
    }

    public static void convertChestKeyToUUID(Chest chest) {
        if (!chest.getPersistentDataContainer().has(lockedKey)) return;
        String oldKey = chest.getPersistentDataContainer().get(lockedKey, PersistentDataType.STRING);
        if (oldKey == null || oldKey.isEmpty()) return;
        if((oldKey.contains(" ") && oldKey.length() == 65) || oldKey.length() == 32) return;

        String newKey;
        if(oldKey.contains(" ")) {
            String[] splitOldKey = oldKey.split(" ");
            newKey = AccountCache.getUUID(splitOldKey[0]) + " " + AccountCache.getUUID(splitOldKey[1]);
        } else {
            newKey = AccountCache.getUUID(oldKey);
        }
        if (newKey == null || newKey.trim().isEmpty() || newKey.contains("null")) { 
             plugin.getLogger().warning("Failed to convert chest key to valid UUID for key: " + oldKey);
             return;
        }
        chest.getPersistentDataContainer().set(lockedKey, PersistentDataType.STRING, newKey.trim());
        chest.update();
    }

    public static List<String> getChestOwner(Chest chest){
        plugin.getLogger().info("[BlockOwner Debug] GETTING playerLocked PDC for chest at " + chest.getLocation());
        if (!chest.getPersistentDataContainer().has(lockedKey)) {
            plugin.getLogger().info("[BlockOwner Debug] Chest at " + chest.getLocation() + " does NOT have 'playerLocked' PDC key.");
            return null;
        }
        String key = chest.getPersistentDataContainer().get(lockedKey, PersistentDataType.STRING);
        plugin.getLogger().info("[BlockOwner Debug] Chest at " + chest.getLocation() + " 'playerLocked' PDC key raw value: '" + key + "'");
        if (key == null || key.isEmpty()) { 
            plugin.getLogger().info("[BlockOwner Debug] Value for 'playerLocked' at " + chest.getLocation() + " is null or empty.");
            return null;
        }
        List<String> owners = new ArrayList<>();
        if(key.contains(" ")) {
            for (String ownerUUID : key.split(" ")) {
                if (ownerUUID != null && !ownerUUID.isEmpty()) {
                    owners.add(ownerUUID);
                }
            }
            if (owners.isEmpty()) plugin.getLogger().info("[BlockOwner Debug] Value '" + key + "' for 'playerLocked' at " + chest.getLocation() + " resulted in an empty owner list after split.");
            return owners.isEmpty() ? null : owners;
        }
        owners.add(key);
        plugin.getLogger().info("[BlockOwner Debug] Final owners list for " + chest.getLocation() + ": " + owners.toString());
        return owners;
    }

}
