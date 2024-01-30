package net.derfla.quickeconomy.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import net.derfla.quickeconomy.util.Balances;
import net.derfla.quickeconomy.util.TypeChecker;

public class BalanceCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String string, @NotNull String[] strings) {
        if (strings.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("You can only see your balance as a player!");
                return true;
            }
            Player player = ((Player) sender).getPlayer();

            player.sendMessage("Your balance is: " + Balances.getPlayerBalance(player.getName()));
            return true;
        }

        if (!(TypeChecker.isFloat(strings[1]))){
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

                    Balances.setPlayerBalance(player.getName(), money);
                    player.sendMessage("§eMoney set!");
                    break;
                }
                Balances.setPlayerBalance(strings[2], money);
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

                    Balances.addPlayerBalance(player.getName(), money);
                    player.sendMessage("§eAdded " + money + " to your balance!");
                    break;
                }
                Balances.addPlayerBalance(strings[2], money);
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
                    Balances.subPlayerBalance(player.getName(), money);
                    player.sendMessage("§eSubtracted " + money + " from your balance!");
                    break;
                }
                Balances.subPlayerBalance(strings[2], money);
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
                if (Balances.getPlayerBalance(player.getName()) < money) {
                    player.sendMessage("§cYou do not have enough balance!");
                    break;
                }
                Balances.subPlayerBalance(player.getName(), money);
                Balances.addPlayerBalance(strings[2], money);
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
