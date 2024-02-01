package net.derfla.quickeconomy.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class QuickeconomyCommand implements CommandExecutor {

    static Plugin plugin = Bukkit.getPluginManager().getPlugin("QuickEconomy");
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String string, @NotNull String[] strings) {
        String helpMessage = "Use /bal or /balance to see your balance. Use /bal send, followed by an amount and another player, to send coins to that player. To create a shop place a sign on a chest. On the first line write [SHOP]. On the second line write the cost for the the items, such as 10 or 5.5. To deposit or withdraw coins, visit a bank!";
        String helpMessageOp = "As you are a server operator you have some more options. You can use /bal set:add:subtract to change the balance of any player. You can also create a bank, simply place down a sign and write [BANK] on the first line.";
        if (plugin.getConfig().contains("helpMessage") && plugin.getConfig().getString("helpMessage") != null) {
            helpMessage = plugin.getConfig().getString("helpMessage");
        }else plugin.getLogger().warning("Could not find string helpMessage in config.yml");

        sender.sendMessage("§e" + helpMessage);
        if (!sender.isOp()) return true;
        if (plugin.getConfig().contains("helpMessageOp") && plugin.getConfig().getString("helpMessageOp") != null) {
            helpMessageOp = plugin.getConfig().getString("helpMessageOp");
        } else plugin.getLogger().warning("Could not find string helpMessageOp in config.yml");
        sender.sendMessage("§e" + helpMessageOp);
        return true;
    }
}
