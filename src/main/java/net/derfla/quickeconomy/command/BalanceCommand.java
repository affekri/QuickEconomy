package net.derfla.quickeconomy.command;

import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BalanceCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String string, @NotNull String[] strings) {
        if (strings.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("You can only see your balance as a player!");
                return true;
            }
            Player player = ((Player) sender).getPlayer();
            player.sendMessage(Component.translatable("balance.see", Component.text(Balances.getPlayerBalance(String.valueOf(player.getUniqueId())))).style(Styles.INFOSTYLE));
            return true;
        }
        double money = 0;
        boolean moneySet;
        try {
            money = Double.parseDouble(strings[1]);
            moneySet = true;
        } catch (Exception e) {
            moneySet = false;
        }

        switch (strings[0].toLowerCase()) {
            case "set":
                if (sender instanceof  Player && !(sender.hasPermission("quickeconomy.balance.modifyall"))) {
                    sender.sendMessage(Component.translatable("balcommand.incorrectarg", Styles.ERRORSTYLE));
                    break;
                }
                if (!moneySet) {
                    sender.sendMessage(Component.translatable("provide.number", Styles.ERRORSTYLE));
                    break;
                }


                if (strings.length == 2) {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(Component.translatable("provide.player", Styles.ERRORSTYLE));
                        break;
                    }
                    Player player = ((Player) sender).getPlayer();

                    Balances.setPlayerBalance(String.valueOf(player.getUniqueId()), money);
                    player.sendMessage(Component.translatable("balcommand.moneyset", Styles.INFOSTYLE));
                    break;
                }
                Balances.setPlayerBalance(Balances.getUUID(strings[2]), money);
                sender.sendMessage(Component.translatable("balcommand.set", Component.text(strings[2]), Component.text(money)).style(Styles.INFOSTYLE));
                break;


            case "add":
                if (sender instanceof  Player && !(sender.hasPermission("quickeconomy.balance.modifyall"))) {
                    sender.sendMessage(Component.translatable("balcommand.incorrectarg", Styles.ERRORSTYLE));
                    break;
                }
                if (!moneySet) {
                    sender.sendMessage(Component.translatable("provide.number", Styles.ERRORSTYLE));
                    break;
                }
                if (strings.length == 2) {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(Component.translatable("provide.player", Styles.ERRORSTYLE));
                        break;
                    }
                    Player player = ((Player) sender).getPlayer();

                    Balances.addPlayerBalance(String.valueOf(player.getUniqueId()), money);
                    player.sendMessage(Component.translatable("balcommand.add.self", Component.text(money)).style(Styles.INFOSTYLE));
                    break;
                }
                Balances.addPlayerBalance(Balances.getUUID(strings[2]), money);
                sender.sendMessage(Component.translatable("balcommand.add", Component.text(money), Component.text(strings[2])).style(Styles.INFOSTYLE));
                break;

            case "subtract":
                if (sender instanceof  Player && !(sender.hasPermission("quickeconomy.balance.modifyall"))) {
                    sender.sendMessage(Component.translatable("balcommand.incorrectarg", Styles.ERRORSTYLE));
                    break;
                }
                if (!moneySet) {
                    sender.sendMessage(Component.translatable("provide.number", Styles.ERRORSTYLE));
                    break;
                }
                if (strings.length == 2) {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(Component.translatable("provide.player", Styles.ERRORSTYLE));
                        break;
                    }
                    Player player = ((Player) sender).getPlayer();
                    Balances.subPlayerBalance(String.valueOf(player.getUniqueId()), money);
                    player.sendMessage(Component.translatable("balcommand.sub.self", Component.text(money)).style(Styles.INFOSTYLE));
                    break;
                }
                Balances.subPlayerBalance(Balances.getUUID(strings[2]), money);
                sender.sendMessage(Component.translatable("balcommand.sub", Component.text(money), Component.text(strings[2])).style(Styles.INFOSTYLE));
                break;

            case "send":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(Component.translatable("balcommand.send.notplayer", Styles.ERRORSTYLE));
                    break;
                }
                if (!moneySet) {
                    sender.sendMessage(Component.translatable("provide.number", Styles.ERRORSTYLE));
                    break;
                }
                if (strings.length == 2) {
                    sender.sendMessage(Component.translatable("provide.player", Styles.ERRORSTYLE));
                    break;
                }
                if (money < 0) {
                    sender.sendMessage(Component.translatable("balcommand.send.negative", Styles.ERRORSTYLE));
                    break;
                }
                if (strings[2].equals(sender.getName())) {
                    sender.sendMessage(Component.translatable("balcommand.send.self", Styles.ERRORSTYLE));
                    break;
                }
                String targetUUID = Balances.getUUID(strings[2]);

                if (!Balances.hasAccount(targetUUID)) {
                    sender.sendMessage(Component.translatable("player.notexists", Component.text(strings[2])));
                    break;
                }

                Player player = ((Player) sender).getPlayer();
                if (Balances.getPlayerBalance(String.valueOf(player.getUniqueId())) < money) {
                    player.sendMessage(Component.translatable("balance.notenough", Styles.ERRORSTYLE));
                    break;
                }
                Balances.executeTransaction("p2p", "command", String.valueOf(player.getUniqueId()), targetUUID, money, null);

                player.sendMessage(Component.translatable("balcommand.send", Component.text(money), Component.text(strings[2])).style(Styles.INFOSTYLE));
                if (Bukkit.getPlayer(strings[2]) != null) {
                    // Alerts the receiving player if it's online
                    Player targetPlayer = Bukkit.getPlayer(strings[2]);
                    targetPlayer.sendMessage(Component.translatable("balcommand.send.receive", Component.text(money), Component.text(player.getName())).style(Styles.INFOSTYLE));
                    break;
                }

                break;
            case "transactions":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("You can only use this command as a player");
                    break;
                }
                Player transactionsPlayer = (Player) sender;
                if(!Main.SQLMode) {
                    sender.sendMessage(Component.translatable("balcommand.incorrectarg", Styles.ERRORSTYLE));
                    break;
                }
                String transactions = String.valueOf(DatabaseManager.displayTransactionsView(String.valueOf(transactionsPlayer.getUniqueId()), true, 1).join());
                if(transactions.isEmpty()){
                    transactionsPlayer.sendMessage(Component.translatable("balcommand.transactions.empty", Styles.ERRORSTYLE));
                    break;
                }
                transactionsPlayer.sendMessage(transactions);
                break;
            case "list":
                if (sender instanceof  Player && !(sender.hasPermission("quickeconomy.balance.seeall"))) {
                    sender.sendMessage(Component.translatable("balcommand.incorrectarg", Styles.ERRORSTYLE));
                    break;
                }
                if(strings.length < 2) {
                    if(Main.SQLMode) {
                        sender.sendMessage(AccountCache.listAllAccounts().toString().replace(",", "\n").replace("[", "").replace("]", ""));
                    } else {
                        sender.sendMessage(Component.translatable("provide.player", Styles.ERRORSTYLE));
                    }
                    break;
                }
                String checkPlayer;
                if (Bukkit.getServer().getPlayerExact(strings[1]) != null) {
                    checkPlayer = Bukkit.getServer().getPlayerExact(strings[1]).getUniqueId().toString();
                } else checkPlayer = Balances.getUUID(strings[1]);
                if (!Balances.hasAccount(checkPlayer)) {
                    sender.sendMessage(Component.translatable("player.notexists", Component.text(strings[1])));
                    break;
                }
                double balance = Balances.getPlayerBalance(TypeChecker.trimUUID(checkPlayer));
                if (balance == 0.0) {
                    sender.sendMessage(Component.translatable("balcommand.see.other.error", Component.text(strings[1])).style(Styles.ERRORSTYLE));
                    break;
                }
                sender.sendMessage(Component.translatable("balcommand.see.other", Component.text(strings[1]), Component.text(balance)).style(Styles.INFOSTYLE));
                break;

            default:
                sender.sendMessage(Component.translatable("balcommand.incorrectarg", Styles.ERRORSTYLE));
                break;
        }
        return true;
    }


    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        if (strings.length == 1) {
            List<String> returnList = new ArrayList<>(Collections.singletonList("send"));
            if(Main.SQLMode) {
                returnList.add("transactions");
            }
            if (sender.hasPermission("quickeconomy.balance.seeall") || !(sender instanceof Player)) {
                returnList.add("list");
            }
            if (sender.hasPermission("quickeconomy.balance.modifyall") || ! (sender instanceof Player)) {
                List<String> subCommands = Arrays.asList("set", "add", "subtract");
                returnList.addAll(subCommands);
            }
            return returnList.stream()
                    .filter(subCommand -> subCommand.toLowerCase().startsWith(strings[0]))
                    .collect(Collectors.toList());
        }
        if (strings.length == 2) {
            if(sender.hasPermission("quickeconomy.balance.seeall") && strings[0].equalsIgnoreCase("list")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(player -> player.toLowerCase().startsWith(strings[1]))
                        .collect(Collectors.toList());
            }

            String balance;
            if (sender instanceof Player) {
                balance = String.valueOf(Balances.getPlayerBalance(String.valueOf(((Player) sender).getUniqueId())));
            } else balance = "1001";
            return Stream.of("10", "100", "1000", balance)
                    .filter(amount -> amount.startsWith(strings[1]))
                    .collect(Collectors.toList());
        }
        if (strings.length == 3) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(player -> player.toLowerCase().startsWith(strings[2]))
                    .collect(Collectors.toList());
        }
        return null;
    }
}
