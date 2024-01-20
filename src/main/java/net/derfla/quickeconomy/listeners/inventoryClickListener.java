package net.derfla.quickeconomy.listeners;

import net.derfla.quickeconomy.utils.bankInventory;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class inventoryClickListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        //filter out bad events
        if (event.getInventory() == null || event.getCurrentItem() == null) return;

        //check if the inventory is an instance of our menu
        if (event.getInventory().getHolder() instanceof bankInventory) {
            //call teh function and hold its state
            Boolean cancel = ((bankInventory) event.getInventory().getHolder()).trigger(event.getCurrentItem(), event.getSlot());
            //set the event cancelled based on the return state
            event.setCancelled(true);
        }
    }

}
