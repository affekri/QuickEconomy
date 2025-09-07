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

import java.util.*;

/**
 * Comprehensive command builder for balance-related operations in QuickEconomy.
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
 * @see PlayerAccount
 * @see AccountCache
 * @see Balances
 * @see TransactionManagement
 * @since 1.0
 */
public class BalanceCommand {


    /**
     * Creates the /balance command with all subcommands and permission checks.
     * Command logic is handled in separate methods for clarity and reusability.
     *
     * @return a {@link LiteralArgumentBuilder} for the /balance command
     */
    public static LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("balance").requires(sender -> sender.getSender().hasPermission("quickeconomy.balance")).executes(BalanceCommand::runBalanceLogic)

                .then(Commands.literal("set").requires(sender -> sender.getSender().hasPermission("quickeconomy.balance.modifyall"))
                        .then(Commands.argument("money", DoubleArgumentType.doubleArg(0.1))
                                .then(Commands.argument("player", new AccountArgument()).executes(BalanceCommand::runSetLogic))
                                .executes(BalanceCommand::runSetLogic)))

                .then(Commands.literal("add").requires(sender -> sender.getSender().hasPermission("quickeconomy.balance.modifyall"))
                        .then(Commands.argument("money", DoubleArgumentType.doubleArg(0.1))
                                .then(Commands.argument("player", new AccountArgument()).executes(BalanceCommand::runAddLogic))
                                .executes(BalanceCommand::runAddLogic)))

                .then(Commands.literal("sub").requires(sender -> sender.getSender().hasPermission("quickeconomy.balance.modifyall"))
                        .then(Commands.argument("money", DoubleArgumentType.doubleArg(0.1))
                                .then(Commands.argument("player", new AccountArgument()).executes(BalanceCommand::runSubLogic))
                                .executes(BalanceCommand::runSubLogic)))

                .then(Commands.literal("send").requires(sender -> sender.getExecutor() instanceof Player)
                        .then(Commands.argument("money", DoubleArgumentType.doubleArg(0.1))
                                .then(Commands.argument("player", new AccountArgument()).executes(BalanceCommand::runSendLogic)
                                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                                .executes(BalanceCommand::runSendLogic)))))

                .then(Commands.literal("transactions").requires(sender -> sender.getExecutor() instanceof Player)
                        .then(Commands.argument("page", IntegerArgumentType.integer(1)))
                        .executes(BalanceCommand::runTransactionLogic).executes(BalanceCommand::runTransactionLogic))

                .then(Commands.literal("list").requires(sender -> sender.getSender().hasPermission("quickeconomy.balance.seeall"))
                        .then(Commands.argument("player", new AccountArgument()).executes(BalanceCommand::runListLogic))
                        .executes(BalanceCommand::runListLogic));
    }

    /**
     * Creates the /bal command as a short alias for /balance.
     * Command logic is handled in separate methods for clarity and reusability.
     *
     * @return a {@link LiteralArgumentBuilder} for the /bal command
     */
    public static LiteralArgumentBuilder<CommandSourceStack> createShortCommand() {
        return Commands.literal("bal").requires(sender -> sender.getSender().hasPermission("quickeconomy.balance")).executes(BalanceCommand::runBalanceLogic)

                .then(Commands.literal("set").requires(sender -> sender.getSender().hasPermission("quickeconomy.balance.modifyall"))
                        .then(Commands.argument("money", DoubleArgumentType.doubleArg(0.1))
                                .then(Commands.argument("player", new AccountArgument()).executes(BalanceCommand::runSetLogic))
                                .executes(BalanceCommand::runSetLogic)))

                .then(Commands.literal("add").requires(sender -> sender.getSender().hasPermission("quickeconomy.balance.modifyall"))
                        .then(Commands.argument("money", DoubleArgumentType.doubleArg(0.1))
                                .then(Commands.argument("player", new AccountArgument()).executes(BalanceCommand::runAddLogic))
                                .executes(BalanceCommand::runAddLogic)))

                .then(Commands.literal("sub").requires(sender -> sender.getSender().hasPermission("quickeconomy.balance.modifyall"))
                        .then(Commands.argument("money", DoubleArgumentType.doubleArg(0.1))
                                .then(Commands.argument("player", new AccountArgument()).executes(BalanceCommand::runSubLogic))
                                .executes(BalanceCommand::runSubLogic)))

                .then(Commands.literal("send").requires(sender -> sender.getExecutor() instanceof Player)
                        .then(Commands.argument("money", DoubleArgumentType.doubleArg(0.1))
                                .then(Commands.argument("player", new AccountArgument()).executes(BalanceCommand::runSendLogic)
                                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                                .executes(BalanceCommand::runSendLogic)))))

                .then(Commands.literal("transactions").requires(sender -> sender.getExecutor() instanceof Player)
                        .then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(BalanceCommand::runTransactionLogic))
                        .executes(BalanceCommand::runTransactionLogic))

                .then(Commands.literal("list").requires(sender -> sender.getSender().hasPermission("quickeconomy.balance.seeall"))
                        .then(Commands.argument("player", new AccountArgument()).executes(BalanceCommand::runListLogic))
                        .executes(BalanceCommand::runListLogic));
    }

    /**
     * Handles the logic for displaying a player's own balance.
     * Only players can use this command; non-player senders receive an error message.
     *
     * @param ctx the command context
     * @return {@link Command#SINGLE_SUCCESS} indicating successful command execution
     */
    private static int runBalanceLogic(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(ctx.getSource().getExecutor() instanceof Player player)) {
            sender.sendMessage("You can only see your balance as a player!");
            return Command.SINGLE_SUCCESS;
        }
        player.sendMessage(Component.translatable("balance.see", Component.text(Balances.getPlayerBalance(String.valueOf(player.getUniqueId())))).style(Styles.INFOSTYLE));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Handles the logic for setting a player's balance via Brigadier commands.
     * If a player argument is provided, sets that player's balance; otherwise, sets the sender's balance.
     * Only accessible to users with the appropriate permission.
     *
     * @param ctx the command context
     * @return {@link Command#SINGLE_SUCCESS} indicating successful command execution
     */
    private static int runSetLogic(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        double money = ctx.getArgument("money", Double.class);
        String playerUUID;
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
     * If a player argument is provided, adds to that player's balance; otherwise, adds to the sender's balance.
     * Only accessible to users with the appropriate permission.
     *
     * @param ctx the command context
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

        player.sendMessage(Component.translatable("balcommand.send", Component.text(money), Component.text(account.name())).style(Styles.INFOSTYLE));
        if (Bukkit.getPlayer(convertedUUID) != null) {
            // Alerts the receiving player if it's online
            Player targetPlayer = Bukkit.getPlayer(convertedUUID);
            if (message.isEmpty()) {
                targetPlayer.sendMessage(Component.translatable("balcommand.send.receive", Component.text(money), Component.text(player.getName())).style(Styles.INFOSTYLE));
            } else {
                targetPlayer.sendMessage(Component.translatable("balcommand.send.receivemesssage", Component.text(money), Component.text(player.getName()), Component.text(message)).style(Styles.INFOSTYLE));
            }
            return Command.SINGLE_SUCCESS;
        }

        return Command.SINGLE_SUCCESS;
    }


    /**
     * Handles the logic for displaying a paginated transaction history for a player.
     * Only available in SQL mode. Provides navigation between pages and error handling for invalid pages.
     *
     * @param ctx the command context
     * @return {@link Command#SINGLE_SUCCESS} indicating successful command execution
     */
    private static int runTransactionLogic(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Player transactionsPlayer = (Player) ctx.getSource().getExecutor();
        int page;
        try {
            page = ctx.getArgument("page", Integer.class);
        } catch (Exception e) {
            page = 1;
        }

        if (!Main.SQLMode) {
            sender.sendMessage(Component.translatable("balcommand.incorrectarg", Styles.ERRORSTYLE));
            return Command.SINGLE_SUCCESS;
        }

        String transactions = String.valueOf(TransactionManagement.displayTransactionsView(String.valueOf(transactionsPlayer.getUniqueId()), true, page).join());

        // Check if the user has any transactions at all
        if (page == 1 && transactions.isEmpty()) {
            transactionsPlayer.sendMessage(Component.translatable("balcommand.transactions.empty", Styles.ERRORSTYLE));
            return Command.SINGLE_SUCCESS;
        }

        // If not page 1 and no transactions, check if this is an invalid page number
        if (page > 1 && transactions.isEmpty()) {
            // Find the total number of pages by checking backwards
            int lastValidPage = 1;
            for (int checkPage = page - 1; checkPage >= 1; checkPage--) {
                String checkTransactions = String.valueOf(TransactionManagement.displayTransactionsView(String.valueOf(transactionsPlayer.getUniqueId()), true, checkPage).join());
                if (!checkTransactions.isEmpty()) {
                    lastValidPage = checkPage;
                    break;
                }
            }

            // If we found a valid page, this means the requested page is invalid
            if (lastValidPage < page) {
                transactionsPlayer.sendMessage(Component.translatable("balcommand.transactions.page.invalid",
                        Component.text(page), Component.text(lastValidPage)).style(Styles.ERRORSTYLE));
                return Command.SINGLE_SUCCESS;
            }
        }

        // Check if there are more transactions on the next page
        String nextPageTransactions = String.valueOf(TransactionManagement.displayTransactionsView(String.valueOf(transactionsPlayer.getUniqueId()), true, page + 1).join());
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
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Handles the logic for listing all player balances or a specific player's balance.
     * If a player argument is provided, displays that player's balance; otherwise, lists all accounts.
     * Only accessible to users with the appropriate permission.
     *
     * @param ctx the command context
     * @return {@link Command#SINGLE_SUCCESS} indicating successful command execution
     */
    private static int runListLogic(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        PlayerAccount targetPlayer;
        try {
            targetPlayer = ctx.getArgument("player", PlayerAccount.class);
        } catch (Exception e) {
            targetPlayer = null;
        }

        if (targetPlayer == null) {
            sender.sendMessage(" " + AccountCache.listAllAccounts().toString().replace(",", "\n").replace("[", "").replace("]", ""));
            return Command.SINGLE_SUCCESS;
        }
        // Handle /bal list playername case
        if (!Balances.hasAccountName(targetPlayer.name())) {
            sender.sendMessage(Component.translatable("player.notexists", Component.text(targetPlayer.name())).style(Styles.ERRORSTYLE));
            return Command.SINGLE_SUCCESS;
        }
        double balance = Balances.getPlayerBalance(AccountCache.getUUID(targetPlayer.name()));
        if (balance == 0.0) {
            sender.sendMessage(Component.translatable("balcommand.see.other.error", Component.text(targetPlayer.name())).style(Styles.ERRORSTYLE));
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage(Component.translatable("balcommand.see.other", Component.text(targetPlayer.name()), Component.text(balance)).style(Styles.INFOSTYLE));
        return Command.SINGLE_SUCCESS;
    }
}
