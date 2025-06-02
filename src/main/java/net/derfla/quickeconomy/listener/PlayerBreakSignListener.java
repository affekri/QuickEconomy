package net.derfla.quickeconomy.listener;

import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.database.Shop;
import net.derfla.quickeconomy.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.Plugin;

import java.util.List;

public class PlayerBreakSignListener implements Listener {

    static Plugin plugin = Main.getInstance();

    @EventHandler
    public void onPlayerBreakSign(BlockBreakEvent event) {
        if (!(event.getBlock().getState() instanceof Sign)) {
            return;
        }
        Sign sign = (Sign) event.getBlock().getState();
        List<Component> listLines = sign.getSide(Side.FRONT).lines();
        Component bankHeaderStyled = PlayerPlaceSignListener.getBankHeaderComponent();
        Component shopHeaderStyled = PlayerPlaceSignListener.getShopHeaderComponent();

        if (listLines.isEmpty() || listLines.get(0) == null) return;

        String rawLine0 = TypeChecker.getRawString(listLines.get(0));
        boolean isBankSignRaw = "[BANK]".equalsIgnoreCase(rawLine0);
        boolean isShopSignRaw = "[SHOP]".equalsIgnoreCase(rawLine0);

        boolean isActualBankSign = isBankSignRaw && listLines.get(0).style().equals(bankHeaderStyled.style());
        boolean isActualShopSign = isShopSignRaw && listLines.get(0).style().equals(shopHeaderStyled.style());

        if (!isActualBankSign && !isActualShopSign) {
            return;
        }

        Player player = event.getPlayer();

        if (isActualBankSign) {
            if (!player.hasPermission("quickeconomy.bank.destroy")) {
                player.sendMessage(Component.translatable("bank.destroy.noperm", Styles.ERRORSTYLE)); 
                event.setCancelled(true);
            }
            return; 
        }
        
        if (isActualShopSign) {
            String ownerNameOnSign = TypeChecker.getRawString(listLines.get(2));
            String ownerUUIDOnSign = null;
            if (ownerNameOnSign != null && !ownerNameOnSign.isEmpty()) {
                 ownerUUIDOnSign = AccountCache.getUUID(ownerNameOnSign);
            }
           
            boolean isOwner = ownerUUIDOnSign != null && ownerUUIDOnSign.equals(TypeChecker.trimUUID(player.getUniqueId().toString()));

            if (player.hasPermission("quickeconomy.shop.destroyall") || isOwner) {
                Chest chest = FindChest.get(sign); 
                if (chest != null) { 
                    BlockOwner.unlockFromPlayer(chest, player.getName()); 
                    
                    if (Main.SQLMode) {
                        Location chestLoc = chest.getLocation();
                        Shop.removeShop(chestLoc.getBlockX(), chestLoc.getBlockY(), chestLoc.getBlockZ())
                            .exceptionally(ex -> {
                                plugin.getLogger().warning("Failed to remove shop from database (sign break): " + ex.getMessage());
                                return null;
                            });
                    }
                } else {
                    plugin.getLogger().warning("Could not find chest for shop sign at " + sign.getLocation());
                }
                
                player.sendMessage(Component.translatable("shop.destroyed", Styles.INFOSTYLE));
            } else {
                player.sendMessage(Component.translatable("shop.breaksign.canceled", Styles.ERRORSTYLE));
                event.setCancelled(true);
            }
        }
    }
}
