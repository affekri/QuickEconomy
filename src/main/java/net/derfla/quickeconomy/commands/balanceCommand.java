package net.derfla.quickeconomy.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import net.derfla.quickeconomy.utils.balances;
import net.derfla.quickeconomy.utils.typeChecker;

public class balanceCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String string, @NotNull String[] strings) {
        if (strings.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("You can only see your balance as a player!");
                return true;
            }
            Player player = ((Player) sender).getPlayer();

            player.sendMessage("Your balance is: " + balances.getPlayerBalance(player.getName()));
            return true;
        }

        if (!(typeChecker.isFloat(strings[1]))){
            sender.sendMessage("§cPlease provide a number!");
            return true;
        }
        float money = Float.parseFloat(strings[1]);



        switch (strings[0].toLowerCase()) {
            case "set":
                if (sender instanceof  Player && !(sender.isOp())) {
                    sender.sendMessage("§cYou need to be an server operator to use this command!");
                    break;
                }


                if (strings.length == 2) {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage("§cPlease provide a player!");
                        break;
                    }
                    Player player = ((Player) sender).getPlayer();

                    balances.setPlayerBalance(player.getName(), money);
                    player.sendMessage("§eMoney set!");
                    break;
                }
                balances.setPlayerBalance(strings[2], money);
                sender.sendMessage("§eSet balance of " + strings[2] + " to " + money);
                break;


            case "add":
                if (sender instanceof  Player && !(sender.isOp())) {
                    sender.sendMessage("§cYou need to be an server operator to use this command!");
                    break;
                }
                if (strings.length == 2) {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage("Please provide a player!");
                        break;
                    }
                    Player player = ((Player) sender).getPlayer();

                    balances.addPlayerBalance(player.getName(), money);
                    player.sendMessage("§eAdded " + money + " to your balance!");
                    break;
                }
                balances.addPlayerBalance(strings[2], money);
                sender.sendMessage("§eAdded " + money + " to " + strings[2] + "'s balance!");
                break;

            case "subtract":
                if (sender instanceof  Player && !(sender.isOp())) {
                    sender.sendMessage("§cYou need to be an server operator to use this command!");
                    break;
                }
                if (strings.length == 2) {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage("§cPlease provide a player!");
                        break;
                    }
                    Player player = ((Player) sender).getPlayer();
                    balances.subPlayerBalance(player.getName(), money);
                    player.sendMessage("§eSubtracted " + money + " from your balance!");
                    break;
                }
                balances.subPlayerBalance(strings[2], money);
                sender.sendMessage("§eSubtracted " + money + " from " + strings[2] + "'s balance!");
                break;

            case "send":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cYou can only send coins as a player!");
                    break;
                }
                if (strings.length == 2) {
                    sender.sendMessage("§cPlease provide a player!");
                    break;
                }
                if (money < 0) {
                    sender.sendMessage("§cPlease use a positive number!");
                    break;
                }
                Player player = ((Player) sender).getPlayer();
                if (balances.getPlayerBalance(player.getName()) < money) {
                    player.sendMessage("§cYou do not have enough balance!");
                    break;
                }
                balances.subPlayerBalance(player.getName(), money);
                balances.addPlayerBalance(strings[2], money);
                player.sendMessage("§eSent " + money + " coins to " + strings[2] + "!");
                if (Bukkit.getPlayer(strings[2]) != null) {
                    // Alerts the receiving player if it's online
                    Player targetPlayer = Bukkit.getPlayer(strings[2]);
                    targetPlayer.sendMessage("§eYou just received " + money + " coins from " + player.getName() + "!");
                    break;
                }

                break;

            default:
                sender.sendMessage("§cInvalid arguments!");
                break;
        }
        return true;
    }
}
