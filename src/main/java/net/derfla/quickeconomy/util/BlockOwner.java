package net.derfla.quickeconomy.util;

import net.derfla.quickeconomy.Main;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Chest;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public class BlockOwner {

    static Plugin plugin = Main.getInstance();
    static NamespacedKey lockedKey = new NamespacedKey(plugin, "playerLocked");
    static NamespacedKey shopOpenKey = new NamespacedKey(plugin, "shopOpen");
    public static boolean isLockedForPlayer (Chest chest, String player) {
        if (!chest.getPersistentDataContainer().has(lockedKey)) return false;
        if (chest.getPersistentDataContainer().get(lockedKey, PersistentDataType.STRING) == null) return false;
        String nbt = chest.getPersistentDataContainer().get(lockedKey, PersistentDataType.STRING);
        if (nbt == null) return false;
        if (nbt.contains(" ")) {
            String[] splitNBT = nbt.split(" ");
            if (splitNBT[0].equals(player) || splitNBT[1].equals(player)) return false;
        }
        if (nbt.equals(player)) return false;
        return true;
    }

    public static boolean isLocked (Chest chest) {
        if (!chest.getPersistentDataContainer().has(lockedKey)) return false;
        if (chest.getPersistentDataContainer().get(lockedKey, PersistentDataType.STRING) == null) return false;
        return true;
    }

    public static void setPlayerLocked(Chest chest, String player, String player2) {
        String nbtValue;
        if (player2.isEmpty()) {
            nbtValue = player;
        }else nbtValue = player + " " + player2;
        chest.getPersistentDataContainer().set(lockedKey, PersistentDataType.STRING, nbtValue);
        chest.update();
        if (nbtValue.equals(player)) {
            plugin.getLogger().info("Locked chest to: " + player);
            return;
        }
        plugin.getLogger().info("Locked chest to: " + player + " & " + player2);
    }

    public static void unlockFromPlayer(Chest chest, String player) {
        String nbt = chest.getPersistentDataContainer().get(lockedKey, PersistentDataType.STRING);
        chest.getPersistentDataContainer().remove(lockedKey);
        chest.update();
        if (nbt == null) {
            plugin.getLogger().info("Unlocked a chest!");
            return;
        }
        if (nbt.contains(" ")) nbt = nbt.replace(" ", " & ");
        plugin.getLogger().info(player + " unlocked a chest from: " + nbt);
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
}
