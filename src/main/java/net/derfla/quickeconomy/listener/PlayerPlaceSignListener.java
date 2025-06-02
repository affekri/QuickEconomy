package net.derfla.quickeconomy.listener;

import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.plugin.Plugin;

import net.derfla.quickeconomy.database.Shop;

public class PlayerPlaceSignListener implements Listener {

    static Plugin plugin = Main.getInstance();
    private static final Component bankHeader = Component.text("[BANK]").style(Styles.BANKHEADER);
    private static final Component shopHeader = Component.text("[SHOP]").style(Styles.SHOPHEADER);

    @EventHandler
    public void onPlayerPlaceSign(SignChangeEvent event) {
        if (!(event.getBlock().getState() instanceof Sign)) {
            return;
        }
        if (event.isCancelled()) {
            return;
        }
        Sign sign = (Sign) event.getBlock().getState();
        Player player = event.getPlayer();

        // Check if the first line is [BANK] or [SHOP]
        Component line0 = event.line(0);
        if (line0 == null) return; // Should not happen with SignChangeEvent usually

        boolean isBankHeader = shopHeader.equals(Component.text("[BANK]")); // Using .equals for components
        boolean isShopHeaderBefore = shopHeader.equals(Component.text("[SHOP]"));

        // New way to compare Component content if direct .equals might fail due to styling not yet applied
        String rawLine0 = TypeChecker.getRawString(line0);
        boolean isBankRaw = "[BANK]".equalsIgnoreCase(rawLine0);
        boolean isShopRaw = "[SHOP]".equalsIgnoreCase(rawLine0);

        if (!isBankRaw && !isShopRaw) return;

        if (isBankRaw) {
            if (!player.hasPermission("quickeconomy.bank.create")) return;
            event.line(0, bankHeader); // Apply styled header
            event.line(1, Component.text("Deposit items").style(Styles.BODY));
            event.line(2, Component.text("for coins!").style(Styles.BODY));
            // sign.update() is handled by the server after event processing if not cancelled
            player.sendMessage(Component.translatable("bank.created", Styles.INFOSTYLE));
            return;
        } else if (isShopRaw) {
            if (!player.hasPermission("quickeconomy.shop.create")) return;
            
            Chest chest = FindChest.get(sign); 
            if (chest == null) {
                player.sendMessage(Component.translatable("shop.nochest", Styles.ERRORSTYLE));
                event.setCancelled(true); // Cancel event if no valid chest found
                return;
            }

            if (FindChest.isDouble(chest)) {
                player.sendMessage(Component.translatable("shop.chest.notsingle", Styles.ERRORSTYLE));
                event.setCancelled(true);
                return;
            }

            String playerTrimmedUUID = TypeChecker.trimUUID(player.getUniqueId().toString());
            if (BlockOwner.isLockedForPlayer(chest, playerTrimmedUUID)) {
                player.sendMessage(Component.translatable("shop.locked.chest", Styles.ERRORSTYLE));
                event.setCancelled(true);
                return;
            }
            if (BlockOwner.isLocked(chest)) {
                player.sendMessage(Component.translatable("shop.chest.alreadyshop", Styles.ERRORSTYLE));
                event.setCancelled(true);
                return;
            }
            
            Component line1Comp = event.line(1);
            if (line1Comp == null) {
                player.sendMessage(Component.translatable("shop.sign.line1error", Styles.ERRORSTYLE));
                event.setCancelled(true); return;
            }
            String line1 = TypeChecker.getRawString(line1Comp);
            if (line1 == null || line1.isEmpty() || !line1.contains("/")) {
                player.sendMessage(Component.translatable("shop.sign.line1error", Styles.ERRORSTYLE));
                event.setCancelled(true); return;
            }
            String[] splitLine1 = line1.split("/");
            if (!TypeChecker.isDouble(splitLine1[0])) {
                player.sendMessage(Component.translatable("shop.sign.line1error", Styles.ERRORSTYLE));
                event.setCancelled(true); return;
            }
            double cost = TypeChecker.formatDouble(Double.parseDouble(splitLine1[0]));
            if (cost <= 0.0) {
                player.sendMessage(Component.translatable("shop.sign.costtolow", Styles.ERRORSTYLE));
                event.setCancelled(true); return;
            }

            String shopType = (splitLine1.length == 2 && splitLine1[1].equalsIgnoreCase("item")) ? "Item" : "Stack";
            
            event.line(0, shopHeader); // Apply styled header
            event.line(1, Component.text(cost + "/" + shopType).style(Styles.BODY));
            event.line(2, Component.text(player.getName()).style(Styles.BODY));
            
            String fullOwner1UUID = player.getUniqueId().toString(); // Full UUID for DB
            String trimmedOwner1UUID = TypeChecker.trimUUID(fullOwner1UUID); // Trimmed UUID for PDC
            String owner2NameRaw = TypeChecker.getRawString(event.line(3));
            String trimmedOwner2UUID = ""; // Default to empty, will be a trimmed UUID if populated

            if (owner2NameRaw != null && !owner2NameRaw.isEmpty()) {
                if (owner2NameRaw.equalsIgnoreCase(player.getName())) {
                    event.line(3, Component.empty());
                    player.sendMessage(Component.translatable("shop.sign.self.second", Styles.ERRORSTYLE));
                    // Continues as a single-owner shop
                } else {
                    String potentialOwner2TrimmedUUID = AccountCache.getUUID(owner2NameRaw); // AccountCache returns trimmed UUID
                    if (potentialOwner2TrimmedUUID != null && !potentialOwner2TrimmedUUID.isEmpty() && Balances.hasAccount(potentialOwner2TrimmedUUID)) {
                        trimmedOwner2UUID = potentialOwner2TrimmedUUID;
                        event.line(3, Component.text(owner2NameRaw).style(Styles.BODY)); // Keep player name on sign
                        player.sendMessage(Component.translatable("shop.created.split", Component.text(owner2NameRaw)).style(Styles.INFOSTYLE));
                    } else {
                        event.line(3, Component.empty());
                        player.sendMessage(Component.translatable("player.notexists", Styles.ERRORSTYLE));
                        // Continues as a single-owner shop
                    }
                }
            } else {
                 player.sendMessage(Component.translatable("shop.created", Styles.INFOSTYLE));
            }

            BlockOwner.setShopOpen(chest, false); // Mark as not being actively used by buyer/owner

            // Set the owner(s) in the chest's Persistent Data Container using TRIMMED UUIDs
            BlockOwner.setPlayerLocked(chest, trimmedOwner1UUID, trimmedOwner2UUID);

            if (Main.SQLMode) {
                Location chestLoc = chest.getLocation();
                // Use full UUID for Owner1 for database, Shop.addShop will trim it.
                // trimmedOwner2UUID is already trimmed or empty.
                Shop.addShop(chestLoc.getBlockX(), chestLoc.getBlockY(), chestLoc.getBlockZ(), fullOwner1UUID, trimmedOwner2UUID)
                    .exceptionally(ex -> {
                        plugin.getLogger().warning("Failed to add shop to database: " + ex.getMessage());
                        return null;
                    });
            }
            // Sign update will happen automatically if event is not cancelled.
        }
    }

    public static Component getBankHeaderComponent(){
        return bankHeader;
    }
    public static Component getShopHeaderComponent(){
        return shopHeader;
    }
}
