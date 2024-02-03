package net.derfla.quickeconomy.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FindChest {

    public static Chest get(Sign sign){
        Pattern pattern = Pattern.compile("facing=([a-z]+)");
        Matcher matcher = pattern.matcher(sign.getBlock().toString());

        if (!matcher.find()) return null;
        String facing = matcher.group(1);
        Location signLocation = sign.getLocation();
        Block block;
        switch (facing) {
            case "north":
                block = signLocation.getBlock().getRelative(0, 0, 1);
                break;
            case "east":
                block = signLocation.getBlock().getRelative(-1, 0, 0);
                break;
            case "south":
                block = signLocation.getBlock().getRelative(0, 0, -1);
                break;
            case "west":
                block = signLocation.getBlock().getRelative(1, 0, 0);
                break;
            default:
                block = null;
                break;
        }
        if (block == null) return null;
        if (!(block.getState() instanceof Chest)) return null;
        if (!block.getType().equals(Material.CHEST)) return null;
        return (Chest) block.getState();
    }
    public static boolean topLocked(Block block) {
        Block topBlock = block.getRelative(0, 1, 0);
        if (topBlock == null) return false;
        if (!topBlock.getType().equals(Material.CHEST)) return false;
        if (!(topBlock.getState() instanceof Chest)) return false;
        return BlockOwner.isLocked((Chest) topBlock.getState());
    }

    public static boolean isDouble (Chest chest) {
        Pattern pattern = Pattern.compile("type=([a-z]+)");
        Matcher matcher = pattern.matcher(chest.getBlock().toString());
        if (!matcher.find()) return false;
        String type = matcher.group(1);
        if (type.equals("single")) return false;
        return true;
    }
}
