package net.derfla.quickeconomy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.derfla.quickeconomy.util.BankInventory;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Brigadier command for opening the bank inventory UI.
 *
 * <p>Restricted to players and gated by the permission
 * <code>quickeconomy.bank.command</code>.</p>
 */
public class BankCommand {

    /**
     * Builds the /bank command.
     *
     * @return a literal builder for the bank command
     */
    public static LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("bank")
            .requires(sender -> sender.getSender().hasPermission("quickeconomy.bank.command"))
            .executes(ctx -> {
                CommandSender sender = ctx.getSource().getSender();
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Only players can open the bank!");
                    return Command.SINGLE_SUCCESS;
                }
                new BankInventory(((Player) sender).getPlayer());
                return Command.SINGLE_SUCCESS;
            });
    }
}
 