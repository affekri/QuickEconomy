package net.derfla.quickeconomy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.derfla.quickeconomy.util.BankInventory;
import org.bukkit.entity.Player;

public class BankCommand {

    /**
     * Create the /bank command. Contains all command logic, due to the small size.
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
