package net.derfla.quickeconomy.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for finding and working with chest blocks in relation to signs and other chests.
 * This class provides methods to locate chests based on sign directions, determine chest types
 * (single/double), and handle chest-to-chest relationships for double chest configurations.
 * 
 * <p>The utility uses regex pattern matching to parse block state strings and extract
 * directional and type information from Minecraft block data.</p>
 * 
 * <p>Common use cases include:
 * <ul>
 *   <li>Finding chests adjacent to shop signs</li>
 *   <li>Locating the other half of a double chest</li>
 *   <li>Determining if a chest is part of a double chest configuration</li>
 *   <li>Checking for locked chests above current position</li>
 * </ul>
 * </p>
 * 
 * @author QuickEconomy
 * @version 1.0
 * @since 1.0
 */
public class FindChest {

    /**
     * Finds the chest that a sign is pointing to based on the sign's facing direction.
     * This method examines the sign's orientation and returns the chest block that would
     * be located behind the sign (relative to its facing direction).
     * 
     * <p>The method uses regex pattern matching to extract the facing direction from
     * the sign's block state string and calculates the appropriate relative position.</p>
     * 
     * @param sign the sign block to analyze
     * @return the chest that the sign is attached to/pointing at, or null if no valid chest is found
     */
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
    /**
     * Checks if there is a locked chest directly above the specified block.
     * This method is useful for verifying chest protection in vertically stacked configurations.
     * 
     * @param block the block to check above
     * @return true if there is a locked chest one block above the specified position, false otherwise
     */
    public static boolean topLocked(Block block) {
        Block topBlock = block.getRelative(0, 1, 0);
        if (topBlock == null) return false;
        if (!topBlock.getType().equals(Material.CHEST)) return false;
        if (!(topBlock.getState() instanceof Chest)) return false;
        return BlockOwner.isLocked((Chest) topBlock.getState());
    }

    /**
     * Determines if a chest is part of a double chest configuration.
     * This method examines the chest's type property to determine if it's connected to another chest.
     * 
     * @param chest the chest to examine
     * @return true if the chest is part of a double chest (left or right), false if it's a single chest
     */
    public static boolean isDouble (Chest chest) {
        Pattern pattern = Pattern.compile("type=([a-z]+)");
        Matcher matcher = pattern.matcher(chest.getBlock().toString());
        if (!matcher.find()) return false;
        String type = matcher.group(1);
        if (type.equals("single")) return false;
        return true;
    }

    /**
     * Finds the other half of a double chest by analyzing the current chest's type and facing direction.
     * This method calculates the position of the connected chest based on the chest's orientation
     * and whether it's the left or right side of the double chest.
     * 
     * <p>The method uses regex pattern matching to extract both the type (left/right) and facing
     * direction, then calculates the appropriate relative position to find the connected chest.</p>
     * 
     * @param chest the chest for which to find the connected half
     * @return the other half of the double chest, or null if the chest is single or no connected chest exists
     */
    public static Chest get(Chest chest) {
        Pattern pattern = Pattern.compile("type=([a-z]+)");
        Matcher matcher = pattern.matcher(chest.getBlock().toString());
        if (!matcher.find()) return null;
        String type = matcher.group(1);
        boolean isRight;
        if (type.equals("right")) {
            isRight = true;
        } else if (type.equals("left")) {
            isRight = false;
        } else return null;

        Pattern pattern2 = Pattern.compile("facing=([a-z]+)");
        Matcher matcher2 = pattern2.matcher(chest.getBlock().toString());
        if (!matcher2.find()) return null;
        String facing = matcher2.group(1);
        Block block;
        switch (facing) {
            case "north":
                if (isRight) {
                    block = chest.getBlock().getRelative(-1, 0, 0);
                    break;
                }
                block = chest.getBlock().getRelative(1, 0, 0);
                break;
            case "west":
                if (isRight) {
                    block = chest.getBlock().getRelative(0, 0, 1);
                    break;
                }
                block = chest.getBlock().getRelative(0, 0, -1);
                break;
            case "south":
                if (isRight) {
                    block = chest.getBlock().getRelative(1, 0, 0);
                    break;
                }
                block = chest.getBlock().getRelative(-1, 0, 0);
                break;
            case "east":
                if (isRight){
                    block = chest.getBlock().getRelative(0, 0, -1);
                    break;
                }
                block = chest.getBlock().getRelative(0, 0, 1);
                break;
            default:
                block = null;
                break;
        }
        if (block == null) return null;
        if (!(block.getState() instanceof Chest)) return null;
        return (Chest) block.getState();
    }
    
    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private FindChest() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
