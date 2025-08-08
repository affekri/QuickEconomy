package net.derfla.quickeconomy.command;

import net.derfla.quickeconomy.util.BankInventory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Command executor for the bank command that opens the bank inventory GUI.
 * This command allows players to access their bank interface for managing their economy.
 */
public class BankCommand implements CommandExecutor {

    /**
     * Executes the bank command to open the bank inventory GUI for players.
     * Only players can use this command as it requires a player entity to open the inventory.
     *
     * @param sender  The command sender (must be a player)
     * @param command The command that was executed
     * @param string  The alias used to call this command
     * @param strings The arguments passed to the command (not used in this implementation)
     * @return true if the command was handled successfully, false otherwise
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String string, @NotNull String[] strings) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("You can only see your balance as a player!");
            return true;
        }
        new BankInventory(((Player) sender).getPlayer());
        return true;
    }
}
