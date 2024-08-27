package net.derfla.quickeconomy.command;

import net.derfla.quickeconomy.util.BankInventory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class BankCommand implements CommandExecutor {

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
