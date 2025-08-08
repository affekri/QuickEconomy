package net.derfla.quickeconomy.command;

import net.derfla.quickeconomy.util.BankInventory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Command handler for the bank functionality in QuickEconomy.
 * <p>
 * This command allows players to open a bank interface where they can
 * manage their money through a graphical user interface. The bank provides
 * an alternative way to interact with the economy system beyond text commands.
 * </p>
 * <p>
 * The command is restricted to players only, as it requires opening an inventory
 * interface which is not available to console senders.
 * </p>
 *
 * @author QuickEconomy
 * @since 1.0
 * @see BankInventory
 * @see CommandExecutor
 */
public class BankCommand implements CommandExecutor {

    /**
     * Executes the bank command to open the bank inventory interface for a player.
     * <p>
     * This method handles the bank command execution by:
     * <ul>
     *   <li>Validating that the sender is a player (not console)</li>
     *   <li>Opening the bank inventory interface for the player</li>
     *   <li>Providing appropriate error messages for non-player senders</li>
     * </ul>
     * </p>
     *
     * @param sender the command sender (must be a player)
     * @param command the command that was executed
     * @param string the command label used
     * @param strings the command arguments (not used in this implementation)
     * @return true to indicate the command was handled successfully
     * @see BankInventory#BankInventory(Player)
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
