package net.derfla.quickeconomy.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class QuickeconomyCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String string, @NotNull String[] strings) {
        if (sender.hasPermission("quickeconomy.balance")) {
            sender.sendMessage("§eUse /bal or /balance to see your balance. Use /bal send, followed by an amount and another player, to send coins to that player.");
        }
        if (sender.hasPermission("quickeconomy.balance.seeall")) {
            sender.sendMessage("§eYou can see other players balances. Use /bal followed by a player name.");
        }
        if (sender.hasPermission("quickeconomy.balance.modifyall")) {
            sender.sendMessage("§eYou can use /bal set:add:subtract to change the balance of any player.");
        }
        if (sender.hasPermission("quickeconomy.shop.create")) {
            sender.sendMessage("§eTo create a shop place a sign on a chest. On the first line write [SHOP]. On the second line write the cost for the the items, such as 10 or 5.5, followed by a / and then what kind of shop it is.");
        }
        if (sender.hasPermission("quickeconomy.bank")) {
            sender.sendMessage("§eTo deposit or withdraw coins, visit a bank!");
        }
        if (sender.hasPermission("quickeconomy.bank.create")) {
            sender.sendMessage("§eYou can create a bank, simply place down a sign and write [BANK] on the first line.");
        }
        return true;
    }
}
