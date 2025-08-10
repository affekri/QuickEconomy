package net.derfla.quickeconomy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.command.argument.AccountArgument;
import net.derfla.quickeconomy.database.TransactionManagement;
import net.derfla.quickeconomy.file.BalanceFile;
import net.derfla.quickeconomy.model.PlayerAccount;
import net.derfla.quickeconomy.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.UUID;


public class BalanceCommand {

    private static LiteralArgumentBuilder<CommandSourceStack> buildCommandTree(String rootLiteral) {
        BalanceCommand handler = new BalanceCommand();
        return Commands.literal(rootLiteral)
                .requires(sender -> sender.getSender().hasPermission("quickeconomy.balance"))
                .executes(BalanceCommand::runBalanceLogic)
                .then(Commands.literal("set").requires(sender -> sender.getSender().hasPermission("quickeconomy.balance.modifyall"))
                        .then(Commands.argument("money", StringArgumentType.string())
                                .executes(handler::runSetLogic)
                                .then(Commands.argument("player", new AccountArgument())
                                        .executes(handler::runSetLogic))))
                .then(Commands.literal("add").requires(sender -> sender.getSender().hasPermission("quickeconomy.balance.modifyall"))
                        .then(Commands.argument("money", StringArgumentType.string())
                                .executes(handler::runAddLogic)
                                .then(Commands.argument("player", new AccountArgument())
                                        .executes(handler::runAddLogic))))
                .then(Commands.literal("subtract").requires(sender -> sender.getSender().hasPermission("quickeconomy.balance.modifyall"))
                        .then(Commands.argument("money", StringArgumentType.string())
                                .executes(handler::runSubLogic)
                                .then(Commands.argument("player", new AccountArgument())
                                        .executes(handler::runSubLogic))))
                .then(Commands.literal("send").requires(sender -> sender.getExecutor() instanceof Player)
                        .then(Commands.argument("money", StringArgumentType.string())
                                .then(Commands.argument("player", new AccountArgument())
                                        .executes(handler::runSendLogic)
                                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                                .executes(handler::runSendLogic)))))
                .then(Commands.literal("list").requires(sender -> sender.getSender().hasPermission("quickeconomy.balance.seeall"))
                        .executes(ctx -> {
                            if(Main.SQLMode) {
                                ctx.getSource().getSender().sendMessage(AccountCache.listAllAccounts().toString().replace(",", "\n").replace("[", "").replace("]", ""));
                            } else {
                                // List all balances in file mode
                                FileConfiguration file = BalanceFile.get();
                                if (file == null) {
                                    ctx.getSource().getSender().sendMessage(Component.translatable("balcommand.incorrectarg", Styles.ERRORSTYLE));
                                    return Command.SINGLE_SUCCESS;
                                }
                                ConfigurationSection players = file.getConfigurationSection("players");
                                if (players == null) {
                                    ctx.getSource().getSender().sendMessage(Component.translatable("balcommand.incorrectarg", Styles.ERRORSTYLE));
                                    return Command.SINGLE_SUCCESS;
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
                                    ctx.getSource().getSender().sendMessage(balanceList.toString());
                                } else {
                                    ctx.getSource().getSender().sendMessage(Component.translatable("balcommand.incorrectarg", Styles.ERRORSTYLE));
                                }
                            }
                            return Command.SINGLE_SUCCESS;
                        })
                )
                .then(Commands.literal("transactions").requires(sender -> Main.SQLMode && sender.getSender() instanceof Player)
                        .executes(BalanceCommand::runTransactionsLogic)
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(BalanceCommand::runTransactionsLogic)))
                .then(Commands.argument("player", new AccountArgument())
                        .executes(handler::runSeeLogic));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return buildCommandTree("balance");
    }

    public static LiteralArgumentBuilder<CommandSourceStack> createShortCommand() {
        return buildCommandTree("bal");
    }

    private static int runBalanceLogic(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getExecutor() instanceof Player player)) {
            ctx.getSource().getSender().sendMessage("You can only see your balance as a player!");
            return Command.SINGLE_SUCCESS;
        }
        player.sendMessage(Component.translatable("balance.see", Component.text(Balances.getPlayerBalance(String.valueOf(player.getUniqueId())))).style(Styles.INFOSTYLE));
        return Command.SINGLE_SUCCESS;
    }

    private int runSetLogic(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        double money;
        try {
            money = AbbreviationUtil.fromString(ctx.getArgument("money", String.class));
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.translatable("balcommand.invalidnumber", Styles.ERRORSTYLE));
            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.translatable("balcommand.abbreviation.invalid", Component.text(e.getMessage())).style(Styles.ERRORSTYLE));
            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
        }
        String playerUUID;
        try {
            PlayerAccount targetPlayer = ctx.getArgument("player", PlayerAccount.class);
            playerUUID = AccountCache.getUUID(targetPlayer.name());
        } catch (Exception e) {
            // Handle player setting their own balance
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.translatable("provide.player", Styles.ERRORSTYLE));
                return com.mojang.brigadier.Command.SINGLE_SUCCESS;
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
        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }


    private int runAddLogic(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        double money;
        try {
            money = AbbreviationUtil.fromString(ctx.getArgument("money", String.class));
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.translatable("balcommand.invalidnumber", Styles.ERRORSTYLE));
            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.translatable("balcommand.abbreviation.invalid", Component.text(e.getMessage())).style(Styles.ERRORSTYLE));
            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
        }
        try {
            PlayerAccount targetPlayer = ctx.getArgument("player", PlayerAccount.class);
            Balances.executeTransaction("n2p", "command", "Server", AccountCache.getUUID(targetPlayer.name()), money, "Balance added by command.");
            sender.sendMessage(Component.translatable("balcommand.add", Component.text(money), Component.text(targetPlayer.name())).style(Styles.INFOSTYLE));
            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.translatable("provide.player", Styles.ERRORSTYLE));
                return com.mojang.brigadier.Command.SINGLE_SUCCESS;
            }
            Balances.executeTransaction("n2p", "command", "Server", TypeChecker.trimUUID(String.valueOf(player.getUniqueId())), money, "Balance added by command.");
            player.sendMessage(Component.translatable("balcommand.add.self", Component.text(money)).style(Styles.INFOSTYLE));
            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
        }
    }


    private int runSubLogic(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        double money;
        try {
            money = AbbreviationUtil.fromString(ctx.getArgument("money", String.class));
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.translatable("balcommand.invalidnumber", Styles.ERRORSTYLE));
            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.translatable("balcommand.abbreviation.invalid", Component.text(e.getMessage())).style(Styles.ERRORSTYLE));
            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
        }
        try {
            PlayerAccount targetPlayer = ctx.getArgument("player", PlayerAccount.class);
            Balances.executeTransaction("p2n", "command", AccountCache.getUUID(targetPlayer.name()), "Server", money, "Balance subtracted by command.");
            sender.sendMessage(Component.translatable("balcommand.sub", Component.text(money), Component.text(targetPlayer.name())).style(Styles.INFOSTYLE));
            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(Component.translatable("provide.player", Styles.ERRORSTYLE));
                return com.mojang.brigadier.Command.SINGLE_SUCCESS;
            }
            Player player = ((Player) sender).getPlayer();
            Balances.executeTransaction("p2n", "command", TypeChecker.trimUUID(String.valueOf(player.getUniqueId())), "Server", money, "Balance subtracted by command.");
            player.sendMessage(Component.translatable("balcommand.sub.self", Component.text(money)).style(Styles.INFOSTYLE));
            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
        }
    }


    private int runSendLogic(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        double money;
        try {
            money = AbbreviationUtil.fromString(ctx.getArgument("money", String.class));
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.translatable("balcommand.invalidnumber", Styles.ERRORSTYLE));
            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.translatable("balcommand.abbreviation.invalid", Component.text(e.getMessage())).style(Styles.ERRORSTYLE));
            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
        }
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
            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
        }

        String targetUUID = AccountCache.getUUID(account.name());
        UUID convertedUUID = UUID.fromString(TypeChecker.untrimUUID(targetUUID));

        String trimmedPlayerUUID = TypeChecker.trimUUID(String.valueOf(player.getUniqueId()));
        if (Balances.getPlayerBalance(trimmedPlayerUUID) < money) {
            player.sendMessage(Component.translatable("balance.notenough", Styles.ERRORSTYLE));
            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
        }

        Balances.executeTransaction("p2p", "command", trimmedPlayerUUID, targetUUID, money, message);

        player.sendMessage(Component.translatable("balcommand.send", Component.text(money), Component.text(account.name())).style(Styles.INFOSTYLE));
        Player onlineTarget = Bukkit.getPlayer(convertedUUID);
        if (onlineTarget != null) {
            // Alerts the receiving player if it's online
            if (message.isEmpty()) {
                onlineTarget.sendMessage(Component.translatable("balcommand.send.receive", Component.text(money), Component.text(player.getName())).style(Styles.INFOSTYLE));
            } else {
                onlineTarget.sendMessage(Component.translatable("balcommand.send.receivemesssage", Component.text(money), Component.text(player.getName()), Component.text(message)).style(Styles.INFOSTYLE));
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private int runSeeLogic(CommandContext<CommandSourceStack> ctx) {
        Player player = (Player) ctx.getSource().getExecutor();
        PlayerAccount targetPlayer = ctx.getArgument("player", PlayerAccount.class);
        float balance = (float) Balances.getPlayerBalance(AccountCache.getUUID(targetPlayer.name()));
        if (balance == 0.0f) {
            player.sendMessage(Component.translatable("balcommand.see.other.error", Component.text(targetPlayer.name())).style(Styles.ERRORSTYLE));
            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
        }
        player.sendMessage(Component.translatable("balcommand.see.other", Component.text(targetPlayer.name()), Component.text(balance)).style(Styles.INFOSTYLE));
        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }

    private static int runTransactionsLogic(CommandContext<CommandSourceStack> ctx) {
        Player transactionsPlayer = (Player) ctx.getSource().getSender();
        int page;
        try {
            page = ctx.getArgument("page", Integer.class);
        } catch (IllegalArgumentException e) {
            page = 1; // Default to page 1 if no argument is provided
        }

        String transactions = String.valueOf(TransactionManagement.displayTransactionsView(String.valueOf(transactionsPlayer.getUniqueId()), true, page).join());

        // Check if the user has any transactions at all
        if (page == 1 && transactions.isEmpty()) {
            transactionsPlayer.sendMessage(Component.translatable("balcommand.transactions.empty", Styles.ERRORSTYLE));
            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
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
                return com.mojang.brigadier.Command.SINGLE_SUCCESS;
            }
        }

        // Check if there are more transactions on the next page
        String nextPageTransactions = String.valueOf(TransactionManagement.displayTransactionsView(String.valueOf(transactionsPlayer.getUniqueId()), true, page + 1).join());
        boolean hasNextPage = !nextPageTransactions.isEmpty();

        // Display transactions with pagination controls
        transactionsPlayer.sendMessage(Component.translatable("balcommand.transactions.page", Component.text(page)).style(Styles.INFOSTYLE));
        transactionsPlayer.sendMessage(Component.text(transactions));

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
        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }


}
