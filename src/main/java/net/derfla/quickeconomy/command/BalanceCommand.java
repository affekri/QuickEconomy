package net.derfla.quickeconomy.command;

import net.derfla.quickeconomy.util.Styles;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import net.derfla.quickeconomy.util.Balances;
import net.derfla.quickeconomy.util.TypeChecker;
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
            player.sendMessage(Component.translatable("balance.see", Component.text(Balances.getPlayerBalance(player.getName()))).style(Styles.INFOSTYLE));
            return true;
        }
        if (strings.length == 1) {
            if (sender instanceof  Player && !(sender.hasPermission("quickeconomy.balance.seeall"))) {
                sender.sendMessage(Component.translatable("balcommand.incorrectarg", Styles.ERRORSTYLE));
                return true;
            }
            String checkPlayer;
            if (Bukkit.getServer().getPlayerExact(strings[0]) != null) {
                checkPlayer = Bukkit.getServer().getPlayerExact(strings[0]).getName();
            } else checkPlayer = strings[0];
            float balance = Balances.getPlayerBalance(checkPlayer);
            if (balance == 0.0f) {
                sender.sendMessage(Component.translatable("balcommand.see.other.error", Component.text(strings[0])).style(Styles.ERRORSTYLE));
                return true;
            }
            sender.sendMessage(Component.translatable("balcommand.see.other", Component.text(checkPlayer), Component.text(balance)).style(Styles.INFOSTYLE));
            return true;
        }
        if (strings[1] == null) {
            return true;
        }

        if (!(TypeChecker.isFloat(strings[1]))){
            sender.sendMessage(Component.translatable("provide.number", Styles.ERRORSTYLE));
            return true;
        }
        float money = Float.parseFloat(strings[1]);

        switch (strings[0].toLowerCase()) {
            case "set":
                if (sender instanceof  Player && !(sender.hasPermission("quickeconomy.balance.modifyall"))) {
                    sender.sendMessage(Component.translatable("balcommand.incorrectarg", Styles.ERRORSTYLE));
                    break;
                }


                if (strings.length == 2) {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(Component.translatable("provide.player", Styles.ERRORSTYLE));
                        break;
                    }
                    Player player = ((Player) sender).getPlayer();

                    Balances.setPlayerBalance(player.getName(), money);
                    player.sendMessage(Component.translatable("balcommand.moneyset", Styles.INFOSTYLE));
                    break;
                }
                Balances.setPlayerBalance(strings[2], money);
                sender.sendMessage(Component.translatable("balcommand.set", Component.text(strings[2]), Component.text(money)).style(Styles.INFOSTYLE));
                break;


            case "add":
                if (sender instanceof  Player && !(sender.hasPermission("quickeconomy.balance.modifyall"))) {
                    sender.sendMessage(Component.translatable("balcommand.incorrectarg", Styles.ERRORSTYLE));
                    break;
                }
                if (strings.length == 2) {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(Component.translatable("provide.player", Styles.ERRORSTYLE));
                        break;
                    }
                    Player player = ((Player) sender).getPlayer();

                    Balances.addPlayerBalance(player.getName(), money);
                    player.sendMessage(Component.translatable("balcommand.add.self", Component.text(money)).style(Styles.INFOSTYLE));
                    break;
                }
                Balances.addPlayerBalance(strings[2], money);
                sender.sendMessage(Component.translatable("balcommand.add", Component.text(money), Component.text(strings[2])).style(Styles.INFOSTYLE));
                break;

            case "subtract":
                if (sender instanceof  Player && !(sender.hasPermission("quickeconomy.balance.modifyall"))) {
                    sender.sendMessage(Component.translatable("balcommand.incorrectarg", Styles.ERRORSTYLE));
                    break;
                }
                if (strings.length == 2) {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(Component.translatable("provide.player", Styles.ERRORSTYLE));
                        break;
                    }
                    Player player = ((Player) sender).getPlayer();
                    Balances.subPlayerBalance(player.getName(), money);
                    player.sendMessage(Component.translatable("balcommand.sub.self", Component.text(money)).style(Styles.INFOSTYLE));
                    break;
                }
                Balances.subPlayerBalance(strings[2], money);
                sender.sendMessage(Component.translatable("balcommand.sub", Component.text(money), Component.text(strings[2])).style(Styles.INFOSTYLE));
                break;

            case "send":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(Component.translatable("balcommand.send.notplayer", Styles.ERRORSTYLE));
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
                if (Bukkit.getServer().getPlayer(strings[2]) == null || Balances.getPlayerBalance(strings[2]) == 0.0f) {
                    sender.sendMessage( Component.translatable("player.notexists", Component.text(strings[2])));
                    break;
                }
                Player player = ((Player) sender).getPlayer();
                if (Balances.getPlayerBalance(player.getName()) < money) {
                    player.sendMessage(Component.translatable("balance.notenough", Styles.ERRORSTYLE));
                    break;
                }
                Balances.subPlayerBalance(player.getName(), money);
                Balances.addPlayerBalance(strings[2], money);

                player.sendMessage(Component.translatable("balcommand.send", Component.text(money), Component.text(strings[2])).style(Styles.INFOSTYLE));
                if (Bukkit.getPlayer(strings[2]) != null) {
                    // Alerts the receiving player if it's online
                    Player targetPlayer = Bukkit.getPlayer(strings[2]);
                    targetPlayer.sendMessage(Component.translatable("balcommand.send.receive", Component.text(money), Component.text(player.getName())).style(Styles.INFOSTYLE));
                    break;
                }

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
            if (sender.hasPermission("quickeconomy.balance.seeall") || !(sender instanceof Player)) {
                List<String> players =  Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
                returnList.addAll(players);
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
            String balance;
            if (sender instanceof Player) {
                balance = String.valueOf(Balances.getPlayerBalance(sender.getName()));
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
