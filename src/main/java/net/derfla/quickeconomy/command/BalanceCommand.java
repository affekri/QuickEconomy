package net.derfla.quickeconomy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.command.argument.AccountArgument;
import net.derfla.quickeconomy.database.TransactionManagement;
import net.derfla.quickeconomy.model.PlayerAccount;
import net.derfla.quickeconomy.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Comprehensive command handler for balance-related operations in QuickEconomy.
 * <p>
 * This command provides extensive functionality for managing player balances, including:
 * <ul>
 *   <li><strong>View Balance:</strong> Check your own or other players' balances</li>
 *   <li><strong>Transfer Money:</strong> Send money between players with optional messages</li>
 *   <li><strong>Administrative Tools:</strong> Set, add, or subtract balances (with permissions)</li>
 *   <li><strong>Transaction History:</strong> View paginated transaction logs (SQL mode only)</li>
 *   <li><strong>Balance Listing:</strong> List all player balances (with permissions)</li>
 * </ul>
 * </p>
 * <p>
 * The command supports both file-based and SQL database storage modes, with some features
 * like transaction history only available in SQL mode. Permission-based access control
 * ensures that administrative functions are restricted to authorized users.
 * </p>
 * <p>
 * <strong>Command Syntax Examples:</strong>
 * <ul>
 *   <li>{@code /bal} - View your own balance</li>
 *   <li>{@code /bal send <amount> <player> [message]} - Send money to another player</li>
 *   <li>{@code /bal set <amount> [player]} - Set a player's balance (admin)</li>
 *   <li>{@code /bal add <amount> [player]} - Add to a player's balance (admin)</li>
 *   <li>{@code /bal subtract <amount> [player]} - Subtract from a player's balance (admin)</li>
 *   <li>{@code /bal transactions [page]} - View transaction history</li>
 *   <li>{@code /bal list [player]} - List balances</li>
 * </ul>
 * </p>
 *
 * @author QuickEconomy
 * @since 1.0
 * @see PlayerAccount
 * @see AccountCache
 * @see Balances
 * @see TransactionManagement
 * @see CommandExecutor
 * @see TabCompleter
 */
public class BalanceCommand implements CommandExecutor, TabCompleter {

    /**
     * Executes the balance command with comprehensive subcommand handling.
     * <p>
     * This method serves as the main entry point for all balance-related operations.
     * It handles various subcommands and provides appropriate functionality based on
     * the arguments provided and the sender's permissions.
     * </p>
     *
     * @param sender the command sender (player or console)
     * @param command the command that was executed
     * @param string the command label used
     * @param strings the command arguments specifying the operation and parameters
     * @return true if the command was handled successfully, false otherwise
     */
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
            PlayerAccount targetPlayer = ctx.getArgument("player", PlayerAccount.class);
            playerUUID = AccountCache.getUUID(targetPlayer.name());
        } catch (Exception e) {
            // Handle player setting their own balance
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.translatable("provide.player", Styles.ERRORSTYLE));
                return Command.SINGLE_SUCCESS;
            }
            playerUUID = TypeChecker.trimUUID(String.valueOf(player.getUniqueId()));
        }
        double balance = Balances.getPlayerBalance(playerUUID);
        double difference = Math.abs(balance - money);
        if (balance < money) {
            Balances.executeTransaction("n2p", "command", "Server", playerUUID, difference, "Balance added by command.");
        } else {
            Balances.executeTransaction("p2n", "command", playerUUID, "Server", difference, "Balance subtracted by command.");
        }
        sender.sendMessage(Component.translatable("balcommand.moneyset", Styles.INFOSTYLE));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Handles the logic for adding money to a player's balance via Brigadier commands.
     * <p>
     * This method processes the "add" subcommand by extracting the amount and target player
     * from the command context, then executing a transaction to add the specified amount
     * to the player's balance.
     * </p>
     *
     * @param ctx the command context containing arguments and source information
     * @return {@link Command#SINGLE_SUCCESS} indicating successful command execution
     */
    private static int runAddLogic(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        double money = ctx.getArgument("money", Double.class);
        try {
            PlayerAccount targetPlayer = ctx.getArgument("player", PlayerAccount.class);
            Balances.executeTransaction("n2p", "command", "Server", AccountCache.getUUID(targetPlayer.name()), money, "Balance added by command.");
            sender.sendMessage(Component.translatable("balcommand.add", Component.text(money), Component.text(targetPlayer.name())).style(Styles.INFOSTYLE));
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.translatable("provide.player", Styles.ERRORSTYLE));
                return Command.SINGLE_SUCCESS;
            }
            Balances.executeTransaction("n2p", "command", "Server", TypeChecker.trimUUID(String.valueOf(player.getUniqueId())), money, "Balance added by command.");
            player.sendMessage(Component.translatable("balcommand.add.self", Component.text(money)).style(Styles.INFOSTYLE));
            return Command.SINGLE_SUCCESS;
        }
    }

    /**
     * Handles the logic for subtracting money from a player's balance via Brigadier commands.
     * <p>
     * This method processes the "subtract" subcommand by extracting the amount and target
     * player from the command context, then executing a transaction to remove the specified
     * amount from the player's balance.
     * </p>
     *
     * @param ctx the command context containing arguments and source information
     * @return {@link Command#SINGLE_SUCCESS} indicating successful command execution
     */
    private static int runSubLogic(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        double money = ctx.getArgument("money", Double.class);
        try {
            PlayerAccount targetPlayer = ctx.getArgument("player", PlayerAccount.class);
            Balances.executeTransaction("p2n", "command", AccountCache.getUUID(targetPlayer.name()), "Server", money, "Balance subtracted by command.");
            sender.sendMessage(Component.translatable("balcommand.sub", Component.text(money), Component.text(targetPlayer.name())).style(Styles.INFOSTYLE));
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(Component.translatable("provide.player", Styles.ERRORSTYLE));
                return Command.SINGLE_SUCCESS;
            }
            Player player = ((Player) sender).getPlayer();
            Balances.executeTransaction("p2n", "command", TypeChecker.trimUUID(String.valueOf(player.getUniqueId())), "Server", money, "Balance subtracted by command.");
            player.sendMessage(Component.translatable("balcommand.sub.self", Component.text(money)).style(Styles.INFOSTYLE));
            return Command.SINGLE_SUCCESS;
        }
    }

    /**
     * Handles the logic for sending money between players via Brigadier commands.
     * <p>
     * This method processes the "send" subcommand, facilitating peer-to-peer money transfers.
     * It performs validation to prevent self-transfers and ensures sufficient balance before
     * executing the transaction.
     * </p>
     *
     * @param ctx the command context containing money amount, target player, and optional message
     * @return {@link Command#SINGLE_SUCCESS} indicating successful command execution
     */
    private static int runSendLogic(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        double money = ctx.getArgument("money", Double.class);
        PlayerAccount account = ctx.getArgument("player", PlayerAccount.class);
        String message;
        try {
            message = ctx.getArgument("message", String.class);
        } catch (Exception e) {
            message = "";
        }
        Player player = (Player) ctx.getSource().getExecutor();

        if (account.name().equals(player.getName())) {
            sender.sendMessage(Component.translatable("balcommand.send.self", Styles.ERRORSTYLE));
            return Command.SINGLE_SUCCESS;
        }

        String targetUUID = AccountCache.getUUID(account.name());
        UUID convertedUUID = UUID.fromString(TypeChecker.untrimUUID(targetUUID));

        String trimmedPlayerUUID = TypeChecker.trimUUID(String.valueOf(player.getUniqueId()));
        if (Balances.getPlayerBalance(trimmedPlayerUUID) < money) {
            player.sendMessage(Component.translatable("balance.notenough", Styles.ERRORSTYLE));
            return Command.SINGLE_SUCCESS;
        }

        Balances.executeTransaction("p2p", "command", trimmedPlayerUUID, targetUUID, money, message);

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
                
                // Parse page number from arguments
                int page = 1;
                if (strings.length > 1) {
                    try {
                        page = Integer.parseInt(strings[1]);
                        if (page < 1) page = 1;
                    } catch (NumberFormatException e) {
                        page = 1;
                    }
                }
                
                String transactions = String.valueOf(DatabaseManager.displayTransactionsView(String.valueOf(transactionsPlayer.getUniqueId()), true, page).join());
                
                // Check if the user has any transactions at all
                if (page == 1 && transactions.isEmpty()) {
                    transactionsPlayer.sendMessage(Component.translatable("balcommand.transactions.empty", Styles.ERRORSTYLE));
                    break;
                }
                
                // If not page 1 and no transactions, check if this is an invalid page number
                if (page > 1 && transactions.isEmpty()) {
                    // Find the total number of pages by checking backwards
                    int lastValidPage = 1;
                    for (int checkPage = page - 1; checkPage >= 1; checkPage--) {
                        String checkTransactions = String.valueOf(DatabaseManager.displayTransactionsView(String.valueOf(transactionsPlayer.getUniqueId()), true, checkPage).join());
                        if (!checkTransactions.isEmpty()) {
                            lastValidPage = checkPage;
                            break;
                        }
                    }
                    
                    // If we found a valid page, this means the requested page is invalid
                    if (lastValidPage < page) {
                        transactionsPlayer.sendMessage(Component.translatable("balcommand.transactions.page.invalid", 
                            Component.text(page), Component.text(lastValidPage)).style(Styles.ERRORSTYLE));
                        break;
                    }
                }
                
                // Check if there are more transactions on the next page
                String nextPageTransactions = String.valueOf(DatabaseManager.displayTransactionsView(String.valueOf(transactionsPlayer.getUniqueId()), true, page + 1).join());
                boolean hasNextPage = !nextPageTransactions.isEmpty();
                
                // Display transactions with pagination controls
                transactionsPlayer.sendMessage(Component.translatable("balcommand.transactions.page", Component.text(page)).style(Styles.INFOSTYLE));
                transactionsPlayer.sendMessage(transactions);
                
                // Add navigation arrows
                Component navigation = Component.empty();
                
                // Previous page arrow (only show if not on page 1)
                if (page > 1) {
                    Component prevArrow = Component.translatable("balcommand.transactions.previous")
                        .style(Styles.INFOSTYLE)
                        .clickEvent(ClickEvent.runCommand("/bal transactions " + (page - 1)))
                        .hoverEvent(HoverEvent.showText(Component.translatable("balcommand.transactions.previous.hover", Component.text(page - 1))));
                    navigation = navigation.append(prevArrow);
                    
                    // Add space separator if both arrows will be present
                    if (hasNextPage) {
                        navigation = navigation.append(Component.text("  ").style(Styles.BODY));
                    }
                }
                
                // Next page arrow (only show if there are more transactions)
                if (hasNextPage) {
                    Component nextArrow = Component.translatable("balcommand.transactions.next")
                        .style(Styles.INFOSTYLE)
                        .clickEvent(ClickEvent.runCommand("/bal transactions " + (page + 1)))
                        .hoverEvent(HoverEvent.showText(Component.translatable("balcommand.transactions.next.hover", Component.text(page + 1))));
                    navigation = navigation.append(nextArrow);
                }
                
                // Only send navigation if there are arrows to show
                if (page > 1 || hasNextPage) {
                    transactionsPlayer.sendMessage(navigation);
                }
                break;
            case "list":
                if (sender instanceof  Player && !(sender.hasPermission("quickeconomy.balance.seeall"))) {
                    sender.sendMessage(Component.translatable("balcommand.incorrectarg", Styles.ERRORSTYLE));
                    break;
                }
                if(strings.length == 1) {
                    if(Main.SQLMode) {
                        sender.sendMessage(AccountCache.listAllAccounts().toString().replace(",", "\n").replace("[", "").replace("]", ""));
                    } else {
                        // List all balances in file mode
                        FileConfiguration file = BalanceFile.get();
                        if (file == null) {
                            sender.sendMessage(Component.translatable("balcommand.incorrectarg", Styles.ERRORSTYLE));
                            break;
                        }
                        ConfigurationSection players = file.getConfigurationSection("players");
                        if (players == null) {
                            sender.sendMessage(Component.translatable("balcommand.incorrectarg", Styles.ERRORSTYLE));
                            break;
                        }
                        StringBuilder balanceList = new StringBuilder();
                        for (String uuid : players.getKeys(false)) {
                            String name = players.getString(uuid + ".name");
                            float balance = (float) players.getDouble(uuid + ".balance");
                            if (name != null && balance > 0) {
                                balanceList.append(name).append(": ").append(balance).append("\n");
                            }
                        }
                        if (balanceList.length() > 0) {
                            sender.sendMessage(balanceList.toString());
                        } else {
                            sender.sendMessage(Component.translatable("balcommand.incorrectarg", Styles.ERRORSTYLE));
                        }
                    }
                    break;
                }
                // Handle /bal list playername case
                if(strings.length == 2) {
                    String checkPlayer;
                    if (Bukkit.getServer().getPlayerExact(strings[1]) != null) {
                        checkPlayer = Bukkit.getServer().getPlayerExact(strings[1]).getUniqueId().toString();
                    } else checkPlayer = Balances.getUUID(strings[1]);
                    if (!Balances.hasAccount(checkPlayer)) {
                        sender.sendMessage(Component.translatable("player.notexists", Component.text(strings[1])));
                        break;
                    }
                    float balance = Balances.getPlayerBalance(TypeChecker.trimUUID(checkPlayer));
                    if (balance == 0.0f) {
                        sender.sendMessage(Component.translatable("balcommand.see.other.error", Component.text(strings[1])).style(Styles.ERRORSTYLE));
                        break;
                    }
                    sender.sendMessage(Component.translatable("balcommand.see.other", Component.text(strings[1]), Component.text(balance)).style(Styles.INFOSTYLE));
                    break;
                }
                sender.sendMessage(Component.translatable("balcommand.incorrectarg", Styles.ERRORSTYLE));
                break;

            default:
                sender.sendMessage(Component.translatable("balcommand.incorrectarg", Styles.ERRORSTYLE));
                break;
        }
        return true;
    }


    /**
     * Provides tab completion suggestions for the balance command.
     * <p>
     * This method generates contextual suggestions based on the current argument position
     * and the sender's permissions. It supports completion for subcommands, amount suggestions,
     * player names, and page numbers depending on the context.
     * </p>
     *
     * @param sender the command sender requesting tab completion
     * @param command the command being completed
     * @param s the command alias used
     * @param strings the current command arguments
     * @return a list of completion suggestions, or null if no suggestions available
     */
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
            if (strings[0].equalsIgnoreCase("list")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(player -> player.toLowerCase().startsWith(strings[1]))
                        .collect(Collectors.toList());
            }
            
            if (strings[0].equalsIgnoreCase("transactions")) {
                // Suggest page numbers for transactions
                return Stream.of("1", "2", "3", "4", "5")
                        .filter(pageNum -> pageNum.startsWith(strings[1]))
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
