package net.derfla.quickeconomy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.database.Migration;
import net.derfla.quickeconomy.database.System;
import net.derfla.quickeconomy.database.TableManagement;
import net.derfla.quickeconomy.database.Utility;
import net.derfla.quickeconomy.util.DerflaAPI;
import net.derfla.quickeconomy.util.Styles;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.Timestamp;

public class QuickeconomyCommand {

    static Plugin plugin = Main.getInstance();

    public static LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("quickeconomy").requires(sender -> sender.getSender().hasPermission("quickeconomy.help"))
                .executes(QuickeconomyCommand::runPluginInfoLogic)
                .then(Commands.literal("migrate").requires(sender -> sender.getSender().hasPermission("quickeconomy.migrate"))
                        .executes(QuickeconomyCommand::runMigrateLogic)
                ).then(Commands.literal("rollback").requires(sender -> sender.getSender().hasPermission("quickeconomy.rollback"))
                        .then(Commands.argument("year", IntegerArgumentType.integer(1900, 9999))
                                .then(Commands.argument("month", IntegerArgumentType.integer(1, 12))
                                        .then(Commands.argument("day", IntegerArgumentType.integer(1, 31))
                                                .then(Commands.argument("hour", IntegerArgumentType.integer(0, 23))
                                                        .then(Commands.argument("minute", IntegerArgumentType.integer(0, 59))
                                                                .then(Commands.argument("second", IntegerArgumentType.integer(0, 59))
                                                                        .executes(QuickeconomyCommand::runRollbackLogic))))))))
                .then(Commands.literal("setup").requires(sender -> sender.getSender().hasPermission("quickeconomy.setup"))
                        .executes(QuickeconomyCommand::runSetupLogic));
    }

    private static int runPluginInfoLogic(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender.hasPermission("quickeconomy.balance")) {
            sender.sendMessage(Component.translatable("qecommand.balance", Styles.INFOSTYLE));
        }
        if (sender.hasPermission("quickeconomy.balance.seeall")) {
            sender.sendMessage(Component.translatable("qecommand.balance.seeall", Styles.INFOSTYLE));
        }
        if (sender.hasPermission("quickeconomy.balance.modifyall")) {
            sender.sendMessage(Component.translatable("qecommand.balance.modifyall", Styles.INFOSTYLE));
        }
        if (sender.hasPermission("quickeconomy.shop.create")) {
            sender.sendMessage(Component.translatable("qecommand.shop.create", Styles.INFOSTYLE));
        }
        if (sender.hasPermission("quickeconomy.bank")) {
            sender.sendMessage(Component.translatable("qecommand.bank", Styles.INFOSTYLE));
        }
        if (sender.hasPermission("quickeconomy.bank.create")) {
            sender.sendMessage(Component.translatable("qecommand.bank.create", Styles.INFOSTYLE));
        }
        if (sender.hasPermission("quickeconomy.migrate")) {
            sender.sendMessage(Component.translatable("qecommand.migrate.info", Styles.INFOSTYLE));
        }
        if (sender.hasPermission("quickeconomy.rollback")) {
            sender.sendMessage(Component.translatable("qecommand.rollback.info", Styles.INFOSTYLE));
        }
        if (sender.hasPermission("quickeconomy.setup")) {
            sender.sendMessage(Component.translatable("qecommand.setup.info", Styles.INFOSTYLE));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int runMigrateLogic(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (Main.SQLMode) {
            Migration.migrateToBalanceFile().thenRun(() -> {
                plugin.getConfig().set("database.enabled", false);
                plugin.saveConfig();
                Main.SQLMode = false;
                sender.sendMessage(Component.translatable("qecommand.migrate.file").style(Styles.INFOSTYLE));
            });
        } else {
            try {
                Utility.connectToDatabase();
                TableManagement.createTables().join();
                plugin.getConfig().set("database.enabled", true);
                plugin.saveConfig();
                Main.SQLMode = true;
                sender.sendMessage(Component.translatable("qecommand.migrate.database").style(Styles.INFOSTYLE));
                Migration.migrateToDatabase();
            } catch (Exception e) {
                sender.sendMessage(Component.translatable("qecommand.migrate.database.fail").style(Styles.ERRORSTYLE));
                plugin.getLogger().warning("Failed to connect to database during migration attempt! " + e.getMessage());
                plugin.getConfig().set("database.enabled", false);
                plugin.saveConfig();
                Main.SQLMode = false;
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int runRollbackLogic(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!Main.SQLMode) {
            return Command.SINGLE_SUCCESS;
        }

        String timestampString;
        try {
            String year = String.valueOf(ctx.getArgument("year", Integer.class));
            String month = String.format("%02d", ctx.getArgument("month", Integer.class));
            String day = String.format("%02d", ctx.getArgument("day", Integer.class));
            String hour = String.format("%02d", ctx.getArgument("hour", Integer.class));
            String minute = String.format("%02d", ctx.getArgument("minute", Integer.class));
            String second = String.format("%02d", ctx.getArgument("second", Integer.class));

            timestampString = year + "-" + month + "-" + day + " " + hour + ":" + minute + ":" + second;
            Timestamp.valueOf(timestampString);
        } catch (Exception e) {
            sender.sendMessage(Component.translatable("qecommand.rollback.date.fail").style(Styles.ERRORSTYLE));
            plugin.getLogger().info("Rollback failed: " + e.getMessage());
            return Command.SINGLE_SUCCESS;
        }

        System.rollback(timestampString).whenComplete((result, ex) -> {
            if (ex != null) {
                sender.sendMessage(Component.translatable("qecommand.rollback.fail").style(Styles.ERRORSTYLE));
                plugin.getLogger().info("Rollback failed: " + ex.getMessage());
            } else {
                sender.sendMessage(Component.translatable("qecommand.rollback.success").style(Styles.INFOSTYLE));
                plugin.getLogger().info("Rollback complete to " + timestampString);
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    private static int runSetupLogic(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String storageMethod = Main.SQLMode ? "SQL Server" : "File";
        String connections = Main.SQLMode ? "(" + Utility.dataSource.getMaximumPoolSize() + ")" : "";
        sender.sendMessage(Component.translatable("qecommand.setup", Component.text(storageMethod + " " + connections), Component.text(plugin.getPluginMeta().getVersion())).style(Styles.INFOSTYLE));
        if (DerflaAPI.updateAvailable()) {
            sender.sendMessage(Component.translatable("quickeconomy.update").style(Styles.INFOSTYLE).clickEvent(ClickEvent.openUrl("https://modrinth.com/plugin/quickeconomy/")));
        }
        return Command.SINGLE_SUCCESS;
    }
}
