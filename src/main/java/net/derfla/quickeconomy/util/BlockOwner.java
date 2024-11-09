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

    public static boolean isLocked (Chest chest) {
        if (!chest.getPersistentDataContainer().has(lockedKey)) return false;
        if (chest.getPersistentDataContainer().get(lockedKey, PersistentDataType.STRING) == null) return false;
        return true;
    }

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
        return chest.getPersistentDataContainer().get(shopOpenKey, PersistentDataType.BOOLEAN);
    }
    public static boolean isShop(Chest chest) {
        return chest.getPersistentDataContainer().has(shopOpenKey, PersistentDataType.BOOLEAN);
    }

    public static void convertChestKeyToUUID(Chest chest) {
        if (!chest.getPersistentDataContainer().has(lockedKey)) return;
        if (chest.getPersistentDataContainer().get(lockedKey, PersistentDataType.STRING) == null) return;
        String oldKey = chest.getPersistentDataContainer().get(lockedKey, PersistentDataType.STRING);
        // Check if oldKey already is new format
        if((oldKey.contains(" ") && oldKey.length() == 65) || oldKey.length() == 32) return;

        String newKey;
        if(oldKey.contains(" ")) {
            String[] splitOldKey = oldKey.split(" ");
            newKey = MojangAPI.getUUID(splitOldKey[0]) + " " + MojangAPI.getUUID(splitOldKey[1]);
        } else {
            newKey = MojangAPI.getUUID(oldKey);
        }
        assert newKey != null;
        chest.getPersistentDataContainer().set(lockedKey, PersistentDataType.STRING, newKey);
        chest.update();
    }

    public static List<String> getChestOwner(Chest chest){
        List<String> owner = new ArrayList<>();
        if (!chest.getPersistentDataContainer().has(lockedKey)) return null;
        if (chest.getPersistentDataContainer().get(lockedKey, PersistentDataType.STRING) == null) return null;
        String key = chest.getPersistentDataContainer().get(lockedKey, PersistentDataType.STRING);
        if(key.contains(" ")) {
            return List.of(key.split(" "));
        }
        owner.add(key);
        owner.add(" ");
        return owner;
    }

}
