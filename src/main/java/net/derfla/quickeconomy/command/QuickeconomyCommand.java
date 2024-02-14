package net.derfla.quickeconomy.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class QuickeconomyCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String string, @NotNull String[] strings) {
        String helpMessage = "Use /bal or /balance to see your balance. Use /bal send, followed by an amount and another player, to send coins to that player. To create a shop place a sign on a chest. On the first line write [SHOP]. On the second line write the cost for the the items, such as 10 or 5.5, followed by a / and then what kind of shop it is. To deposit or withdraw coins, visit a bank!";
        String helpMessageOp = "As you are a server operator you have some more options. You can use /bal set:add:subtract to change the balance of any player. You can also create a bank, simply place down a sign and write [BANK] on the first line.";
        sender.sendMessage("§e" + helpMessage);
        if (!sender.isOp()) return true;
        sender.sendMessage("§e" + helpMessageOp);
        return true;
    }
}
