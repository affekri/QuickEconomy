package net.derfla.quickeconomy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.derfla.quickeconomy.util.BankInventory;
import org.bukkit.entity.Player;

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
 * @see BankInventory
 * @since 1.0
 */
public class BankCommand {

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
     * @return 1 to indicate the command was handled successfully
     * @see BankInventory#BankInventory(Player)
     */
    public static LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("bank").requires(sender -> sender.getSender().hasPermission("quickeconomy.bank.command"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player)) {
                        ctx.getSource().getSender().sendMessage("You can only open the bank as a player!");
                        return Command.SINGLE_SUCCESS;
                    }
                    new BankInventory((Player) ctx.getSource().getExecutor());
                    return Command.SINGLE_SUCCESS;
                });
    }
}
